package com.extendedae_plus.client.event;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.client.emi.BoMMappingStatus;
import com.extendedae_plus.compat.EmiHelper;
import com.extendedae_plus.compat.EmiRecipeCompat;
import com.extendedae_plus.network.BatchCreateAndUploadPatternC2SPacket;
import com.extendedae_plus.network.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ctrl+Q 快速创建样板（EMI 路径）。
 * 与 {@link CtrlQPatternKeyHandler}（JEI 路径）互斥分发：EMI 在场时由本类接管。
 * 本类只引用 EMI/原版类，可在未装 JEI 的环境安全加载。
 */
public final class EmiCtrlQHandler {
	private EmiCtrlQHandler() {}

	@SubscribeEvent
	public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
		Screen screen = event.getScreen();
		if (screen == null) {
			return;
		}
		// isActiveAndMatches 才会校验修饰键（Ctrl）与冲突上下文；KeyMapping.matches 对裸键也会命中
		if (!ModKeybindings.CREATE_PATTERN_KEY.isActiveAndMatches(
				InputConstants.Type.KEYSYM.getOrCreate(event.getKeyCode()))) {
			return;
		}
		// 双查看器仲裁：EMI 在场时本类接管，JEI 分支让位（与 InputEvents 的分派优先级一致）。
		if (!EmiHelper.isLoaded()) {
			return;
		}

		boolean isAllowSubstitutes = Screen.hasShiftDown();
		boolean isFluidSubstitutes = Screen.hasAltDown();

		// 合成链（配方树/BoM）界面内禁用快捷键编码：批量编码由界面上的 A 按钮承担
		if (screen instanceof dev.emi.emi.screen.BoMScreen) {
			return;
		}

		ItemStack hovered = EmiHelper.getIngredientUnderMouse();
		if (hovered.isEmpty()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.hover_item_first"), true);
			}
			return;
		}

		List<RecipeInfo> recipes = EmiRecipeCompat.findRecipesByOutput(hovered);
		if (recipes.isEmpty()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.no_recipes_found"), true);
			}
			return;
		}

		RecipeInfo selected = RecipeFinderUtil.selectBestRecipe(recipes);
		if (selected == null || selected.getRecipeId() == null) {
			return;
		}

		List<ItemStack> selectedIngredients = selected.selectBestInputs(Map.of());
		List<ItemStack> selectedOutputs = convertOutputsToItemStacks(selected);

		PacketDistributor.sendToServer(new CreateCtrlQPatternC2SPacket(
			selected.getRecipeId(),
			selected.isCraftingRecipe(),
			selectedIngredients,
			selectedOutputs,
			isAllowSubstitutes,
			isFluidSubstitutes
		));
		event.setCanceled(true);
	}

	private static List<ItemStack> convertOutputsToItemStacks(RecipeInfo recipeInfo) {
		return recipeInfo.getOutputs().stream()
			.map(genericStack -> {
				if (genericStack.what() instanceof AEItemKey itemKey) {
					return itemKey.toStack((int) genericStack.amount());
				}
				return GenericStack.wrapInItemStack(genericStack);
			})
			.toList();
	}

	/**
	 * 合成链（BoM 树）一键批量编码：遍历整棵树，收集所有涉及配方的样板，
	 * 分批打包成 {@link BatchCreateAndUploadPatternC2SPacket} 发送
	 * （合成样板进装配矩阵，处理样板按映射进供应器，都不行才落背包）。同一配方只编码一次。
	 * <p>
	 * 调用方（A 按钮）负责在存在缺映射节点时拦住本方法；此处只跳过无法解析配方 ID 的节点，
	 * 那类节点补映射也编不出样板。
	 *
	 * @return 成功发送的样板数
	 */
	public static int encodeBoMTreeAll(boolean isAllowSubstitutes, boolean isFluidSubstitutes) {
		Minecraft mc = Minecraft.getInstance();
		dev.emi.emi.bom.MaterialTree tree = dev.emi.emi.bom.BoM.tree;
		if (tree == null || tree.goal == null) {
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.no_recipes_found"), true);
			}
			return 0;
		}

		Set<ResourceLocation> seen = new HashSet<>();
		List<BatchCreateAndUploadPatternC2SPacket.Entry> entries = new ArrayList<>();
		int skipped = 0;
		Deque<dev.emi.emi.bom.MaterialNode> stack = new ArrayDeque<>();
		stack.push(tree.goal);
		while (!stack.isEmpty()) {
			dev.emi.emi.bom.MaterialNode node = stack.pop();
			if (node.children != null) {
				for (dev.emi.emi.bom.MaterialNode child : node.children) {
					stack.push(child);
				}
			}
			if (node.recipe == null) {
				continue;
			}
			if (BoMMappingStatus.of(node.recipe) == BoMMappingStatus.Status.NO_RECIPE_ID) {
				skipped++;
				continue;
			}
			RecipeInfo info = EmiRecipeCompat.fromEmiRecipe(node.recipe);
			if (info == null || info.getRecipeId() == null || !seen.add(info.getRecipeId())) {
				continue;
			}
			entries.add(new BatchCreateAndUploadPatternC2SPacket.Entry(
				info.getRecipeId(),
				info.isCraftingRecipe(),
				info.selectBestInputs(Map.of()),
				convertOutputsToItemStacks(info),
				BoMMappingStatus.searchKeyOf(node.recipe)
			));
		}

		if (entries.isEmpty()) {
			if (mc.player != null) {
				mc.player.displayClientMessage(
					Component.translatable("message.extendedae_plus.bom_encode.nothing_to_encode"), true);
			}
			return 0;
		}

		// 单封包条目上限，超出时分批发送，避免一棵大树塞爆一个数据包。
		int limit = BatchCreateAndUploadPatternC2SPacket.MAX_ENTRIES;
		for (int from = 0; from < entries.size(); from += limit) {
			PacketDistributor.sendToServer(new BatchCreateAndUploadPatternC2SPacket(
				new ArrayList<>(entries.subList(from, Math.min(from + limit, entries.size()))),
				isAllowSubstitutes,
				isFluidSubstitutes
			));
		}

		if (skipped > 0 && mc.player != null) {
			mc.player.displayClientMessage(Component.translatable(
				"message.extendedae_plus.bom_encode.skipped_no_recipe", skipped), true);
		}
		return entries.size();
	}
}
