package com.extendedae_plus.client.event;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.compat.EmiHelper;
import com.extendedae_plus.compat.EmiRecipeCompat;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.pattern.CreateAndUploadPatternC2SPacket;
import com.extendedae_plus.network.pattern.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
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
				com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(event.getKeyCode()))) {
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
		if (selected == null || selected.getRecipe().getId() == null) {
			return;
		}

        List<ItemStack> selectedIngredients = selected.selectBestInputs(Map.of());
        List<ItemStack> selectedOutputs = convertOutputsToItemStacks(selected);

        boolean openProviderSelector = !selected.isCraftingRecipe();
        if (openProviderSelector) {
            // 加工样板选择供应器时，预填配方类型映射名，便于直接筛选目标供应器。
            String searchKey = RecipeTypeNameConfig.mapRecipeTypeToSearchKey(selected.getRecipe());
            RecipeTypeNameConfig.setLastProcessingName(searchKey);
        }

        ModNetwork.CHANNEL.sendToServer(new CreateCtrlQPatternC2SPacket(
            selected.getRecipe().getId(),
            selected.isCraftingRecipe(),
            selectedIngredients,
            selectedOutputs,
            openProviderSelector,
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
	 * 合成链（BoM 树）一键批量编码：遍历整棵树，收集所有涉及配方的样板并批量发送
	 * （直传装配矩阵，无矩阵时服务端自动落背包）。同一配方只编码一次。
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
		int sent = 0;
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
			RecipeInfo info = EmiRecipeCompat.fromEmiRecipe(node.recipe);
			if (info == null) {
				continue;
			}
			ResourceLocation recipeId = info.getRecipe().getId();
			if (recipeId == null || !seen.add(recipeId)) {
				continue;
			}
			ModNetwork.CHANNEL.sendToServer(new CreateAndUploadPatternC2SPacket(
				recipeId,
				info.isCraftingRecipe(),
				info.selectBestInputs(Map.of()),
				convertOutputsToItemStacks(info),
				isAllowSubstitutes,
				isFluidSubstitutes
			));
			sent++;
		}

		if (mc.player != null) {
			mc.player.displayClientMessage(
				sent > 0
					? Component.literal("[EAP] 已发送 " + sent + " 个样板编码请求").withStyle(ChatFormatting.GREEN)
					: Component.literal("[EAP] 合成链中没有可编码的配方").withStyle(ChatFormatting.RED),
				true
			);
		}
		return sent;
	}
}
