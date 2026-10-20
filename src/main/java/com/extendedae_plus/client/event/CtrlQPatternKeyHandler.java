package com.extendedae_plus.client.event;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.compat.jei.JeiRuntimeCompat;
import com.extendedae_plus.mixin.jei.accessor.RecipeBookmarkAccessor;
import com.extendedae_plus.network.CreateAndUploadPatternC2SPacket;
import com.extendedae_plus.network.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.util.ModCheckUtils;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * Ctrl+Q 快速创建样板事件监听器
 */
public final class CtrlQPatternKeyHandler {
	private CtrlQPatternKeyHandler() {
	}

	@SubscribeEvent
	public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
		Screen screen = event.getScreen();
		if (screen == null) {
			return;
		}

		int keyCode = event.getKeyCode();
		boolean isAllowSubstitutes = Screen.hasShiftDown();
		boolean isFluidSubstitutes = Screen.hasAltDown();
		// isActiveAndMatches 才会校验修饰键（Ctrl）与冲突上下文；KeyMapping.matches 对裸键也会命中
		if (!ModKeybindings.CREATE_PATTERN_KEY.isActiveAndMatches(
				InputConstants.Type.KEYSYM.getOrCreate(keyCode))) {
			return;
		}

		// 双查看器仲裁：EMI 在场时由 EmiCtrlQHandler 接管（与 InputEvents 的分派优先级一致）。
		if (ModCheckUtils.isLoaded(ModCheckUtils.MODID_EMI)) {
			return;
		}

		if (JeiRuntimeCompat.getRuntime() == null) {
			return;
		}

		Optional<RecipeBookmark<?, ?>> recipeBookmark = JeiRuntimeCompat.getRecipeBookmarkUnderMouse();
		if (recipeBookmark.isPresent()) {
			handleRecipeBookmark(recipeBookmark.get(), isAllowSubstitutes, isFluidSubstitutes);
			event.setCanceled(true);
			return;
		}

		Optional<ITypedIngredient<?>> ingredient = castTypedIngredient(JeiRuntimeCompat.getIngredientUnderMouse());
		if (ingredient.isEmpty()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.hover_item_first"), true);
			}
			return;
		}

		List<RecipeInfo> recipes = RecipeFinderUtil.findRecipesByIngredient(ingredient.get());
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

		List<ItemStack> selectedIngredients = selectIngredientsWithJeiPriority(selected);
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

	private static void handleRecipeBookmark(RecipeBookmark<?, ?> recipeBookmark, boolean isAllowSubstitutes, boolean isFluidSubstitutes) {
		if (isCraftingRecipe(recipeBookmark)) {
			handleCraftingRecipeBookmark(recipeBookmark, isAllowSubstitutes, isFluidSubstitutes);
		} else {
			handleProcessingRecipeBookmark(recipeBookmark, isAllowSubstitutes, isFluidSubstitutes);
		}
	}

	private static boolean isCraftingRecipe(RecipeBookmark<?, ?> recipeBookmark) {
		Object recipeType = recipeBookmark.getRecipeCategory().getRecipeType();
		return RecipeTypes.CRAFTING.equals(recipeType)
			|| RecipeTypes.STONECUTTING.equals(recipeType)
			|| RecipeTypes.SMITHING.equals(recipeType);
	}

	private static void handleCraftingRecipeBookmark(RecipeBookmark<?, ?> recipeBookmark, boolean isAllowSubstitutes, boolean isFluidSubstitutes) {
		try {
			ResourceLocation recipeId = getRecipeId(recipeBookmark);
			if (recipeId == null) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.recipe_not_found"), true);
				}
				return;
			}

			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				return;
			}
			var recipeOpt = mc.level.getRecipeManager().byKey(recipeId);
			if (recipeOpt.isEmpty()) {
				if (mc.player != null) {
					mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.recipe_not_found"), true);
				}
				return;
			}

			List<RecipeInfo> recipeInfos = findRecipeInfosForBookmark(recipeBookmark);
			if (recipeInfos.isEmpty()) {
				if (mc.player != null) {
					mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.no_recipes_found"), true);
				}
				return;
			}

			RecipeInfo matching = matchById(recipeInfos, recipeId);
			List<ItemStack> selectedIngredients = selectIngredientsWithJeiPriority(matching);
			List<ItemStack> selectedOutputs = convertOutputsToItemStacks(matching);
			RecipeTypeNameConfig.presetCraftingProviderSearchKey();

			PacketDistributor.sendToServer(new CreateAndUploadPatternC2SPacket(
				recipeId,
				matching.isCraftingRecipe(),
				selectedIngredients,
				selectedOutputs,
				isAllowSubstitutes,
				isFluidSubstitutes
			));
		} catch (RuntimeException e) {
			LOGGER.warn("[CtrlQ] 处理合成书签失败", e);
		}
	}

	private static void handleProcessingRecipeBookmark(RecipeBookmark<?, ?> recipeBookmark, boolean isAllowSubstitutes, boolean isFluidSubstitutes) {
		try {
			ResourceLocation recipeId = getRecipeId(recipeBookmark);
			if (recipeId == null) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.recipe_not_found"), true);
				}
				return;
			}

			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				return;
			}
			var recipeOpt = mc.level.getRecipeManager().byKey(recipeId);
			if (recipeOpt.isEmpty()) {
				if (mc.player != null) {
					mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.recipe_not_found"), true);
				}
				return;
			}

			setLastProcessingNameFromRecipe(recipeBookmark.getRecipe());

			List<RecipeInfo> recipeInfos = findRecipeInfosForBookmark(recipeBookmark);
			if (recipeInfos.isEmpty()) {
				if (mc.player != null) {
					mc.player.displayClientMessage(Component.translatable("message.extendedae_plus.no_recipes_found"), true);
				}
				return;
			}

			RecipeInfo matching = matchById(recipeInfos, recipeId);
			List<ItemStack> selectedIngredients = selectIngredientsWithJeiPriority(matching);
			List<ItemStack> selectedOutputs = convertOutputsToItemStacks(matching);

			PacketDistributor.sendToServer(new CreateCtrlQPatternC2SPacket(
				recipeId,
				matching.isCraftingRecipe(),
				selectedIngredients,
				selectedOutputs,
				true,
				isAllowSubstitutes,
				isFluidSubstitutes
			));
		} catch (RuntimeException e) {
			LOGGER.warn("[CtrlQ] 处理加工书签失败", e);
		}
	}

	private static List<RecipeInfo> findRecipeInfosForBookmark(RecipeBookmark<?, ?> recipeBookmark) {
		Optional<ITypedIngredient<?>> hovered = castTypedIngredient(JeiRuntimeCompat.getIngredientUnderMouse());
		if (hovered.isPresent()) {
			List<RecipeInfo> infos = RecipeFinderUtil.findRecipesByIngredient(hovered.get());
			if (!infos.isEmpty()) {
				return infos;
			}
		}

		// 书签创建时已确定展示用材料（优先产出槽，其次输入槽），直接用它反查配方。
		ITypedIngredient<?> displayIngredient = recipeBookmark.getDisplayIngredient();
		if (displayIngredient != null) {
			return RecipeFinderUtil.findRecipesByIngredient(displayIngredient);
		}
		return List.of();
	}

	private static RecipeInfo matchById(List<RecipeInfo> recipeInfos, ResourceLocation recipeId) {
		for (RecipeInfo info : recipeInfos) {
			if (recipeId.equals(info.getRecipeId())) {
				return info;
			}
		}
		return recipeInfos.get(0);
	}

	private static ResourceLocation getRecipeId(RecipeBookmark<?, ?> recipeBookmark) {
		return ((RecipeBookmarkAccessor) recipeBookmark).eap$getRecipeUid();
	}

	@SuppressWarnings("unchecked")
	private static Optional<ITypedIngredient<?>> castTypedIngredient(Optional<?> opt) {
		if (opt == null || opt.isEmpty()) {
			return Optional.empty();
		}
		Object value = opt.get();
		if (value instanceof ITypedIngredient<?>) {
			return (Optional<ITypedIngredient<?>>) (Optional<?>) Optional.of(value);
		}
		return Optional.empty();
	}

	private static void setLastProcessingNameFromRecipe(Object recipeBase) {
		String name = null;

		if (recipeBase instanceof RecipeHolder<?> holder) {
			recipeBase = holder.value();
		}

		if (recipeBase instanceof Recipe<?> recipe) {
			name = RecipeTypeNameConfig.mapRecipeTypeToSearchKey(recipe);
			if (name == null || name.isBlank()) {
				// 注册表反查失败时按配方类名保底，保证供应器搜索词不会为空。
				name = RecipeTypeNameConfig.deriveSearchKeyFromUnknownRecipe(recipe);
			}
		}

		if (name == null || name.isBlank()) {
			name = RecipeTypeNameConfig.deriveSearchKeyFromUnknownRecipe(recipeBase);
		}
		if (name != null && !name.isBlank()) {
			RecipeTypeNameConfig.setLastProcessingName(name);
		}
	}

	private static List<ItemStack> selectIngredientsWithJeiPriority(RecipeInfo recipeInfo) {
		List<?> bookmarks = JeiRuntimeCompat.getBookmarkList();
		Map<Item, Integer> priorities = new HashMap<>();
		AtomicInteger index = new AtomicInteger(Integer.MAX_VALUE);

		for (Object obj : bookmarks) {
			if (obj instanceof ITypedIngredient<?> ingredient) {
				ingredient.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(itemStack ->
					priorities.put(itemStack.getItem(), index.getAndDecrement())
				);
			}
		}

		return recipeInfo.selectBestInputs(priorities);
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