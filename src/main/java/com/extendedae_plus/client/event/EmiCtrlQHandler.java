package com.extendedae_plus.client.event;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.compat.EmiHelper;
import com.extendedae_plus.compat.EmiRecipeCompat;
import com.extendedae_plus.network.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

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
		if (!ModKeybindings.CREATE_PATTERN_KEY.matches(event.getKeyCode(), event.getScanCode())) {
			return;
		}
		// 双查看器仲裁：EMI 在场时本类接管，JEI 分支让位（与 InputEvents 的分派优先级一致）。
		if (!EmiHelper.isLoaded()) {
			return;
		}

		boolean isAllowSubstitutes = Screen.hasShiftDown();
		boolean isFluidSubstitutes = Screen.hasAltDown();

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
}
