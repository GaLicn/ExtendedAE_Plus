package com.extendedae_plus.client.event;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.integration.jei.JeiRuntimeProxy;
import com.extendedae_plus.network.CreateAndUploadPatternC2SPacket;
import com.extendedae_plus.network.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.network.RequestProvidersListC2SPacket;
import com.extendedae_plus.util.RecipeFinderUtil;
import com.extendedae_plus.util.RecipeInfo;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
		int scanCode = event.getScanCode();
		if (!ModKeybindings.CREATE_PATTERN_KEY.matches(keyCode, scanCode)) {
			return;
		}

		if (JeiRuntimeProxy.get() == null) {
			return;
		}

		Optional<?> recipeBookmark = JeiRuntimeProxy.getRecipeBookmarkUnderMouse();
		if (recipeBookmark.isPresent()) {
			handleRecipeBookmark(recipeBookmark.get());
			event.setCanceled(true);
			return;
		}

		Optional<ITypedIngredient<?>> ingredient = castTypedIngredient(JeiRuntimeProxy.getIngredientUnderMouse());
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
			selectedOutputs
		));
		event.setCanceled(true);
	}

	private static void handleRecipeBookmark(Object recipeBookmark) {
		if (isCraftingRecipe(recipeBookmark)) {
			handleCraftingRecipeBookmark(recipeBookmark);
		} else {
			handleProcessingRecipeBookmark(recipeBookmark);
		}
	}

	private static boolean isCraftingRecipe(Object recipeBookmark) {
		try {
			var getRecipeCategoryMethod = recipeBookmark.getClass().getMethod("getRecipeCategory");
			Object recipeCategory = getRecipeCategoryMethod.invoke(recipeBookmark);

			var getRecipeTypeMethod = recipeCategory.getClass().getMethod("getRecipeType");
			Object recipeType = getRecipeTypeMethod.invoke(recipeCategory);
			return RecipeTypes.CRAFTING.equals(recipeType)
				|| RecipeTypes.STONECUTTING.equals(recipeType)
				|| RecipeTypes.SMITHING.equals(recipeType);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void handleCraftingRecipeBookmark(Object recipeBookmark) {
		try {
			ResourceLocation recipeId = getRecipeId(recipeBookmark);
			if (recipeId == null) {
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

			PacketDistributor.sendToServer(new CreateAndUploadPatternC2SPacket(
				recipeId,
				matching.isCraftingRecipe(),
				selectedIngredients,
				selectedOutputs
			));
		} catch (Throwable ignored) {
		}
	}

	private static void handleProcessingRecipeBookmark(Object recipeBookmark) {
		try {
			ResourceLocation recipeId = getRecipeId(recipeBookmark);
			if (recipeId == null) {
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

			Object recipeBase = null;
			try {
				var getRecipeMethod = recipeBookmark.getClass().getMethod("getRecipe");
				recipeBase = getRecipeMethod.invoke(recipeBookmark);
			} catch (Throwable ignored) {
			}
			setLastProcessingNameFromRecipe(recipeBase != null ? recipeBase : recipeOpt.get());

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
				true
			));
			PacketDistributor.sendToServer(RequestProvidersListC2SPacket.INSTANCE);
		} catch (Throwable ignored) {
		}
	}

	private static List<RecipeInfo> findRecipeInfosForBookmark(Object recipeBookmark) {
		Optional<ITypedIngredient<?>> hovered = castTypedIngredient(JeiRuntimeProxy.getIngredientUnderMouse());
		if (hovered.isPresent()) {
			List<RecipeInfo> infos = RecipeFinderUtil.findRecipesByIngredient(hovered.get());
			if (!infos.isEmpty()) {
				return infos;
			}
		}

		try {
			var getRecipeOutputMethod = recipeBookmark.getClass().getMethod("getRecipeOutput");
			Object recipeOutput = getRecipeOutputMethod.invoke(recipeBookmark);
			if (recipeOutput instanceof ITypedIngredient<?> typed) {
				return RecipeFinderUtil.findRecipesByIngredient(typed);
			}
		} catch (Throwable ignored) {
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

	private static ResourceLocation getRecipeId(Object recipeBookmark) {
		try {
			var getRecipeUidMethod = recipeBookmark.getClass().getMethod("getRecipeUid");
			Object recipeId = getRecipeUidMethod.invoke(recipeBookmark);
			if (recipeId instanceof ResourceLocation rl) {
				return rl;
			}
		} catch (Throwable ignored) {
		}
		return null;
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
		if (recipeBase instanceof Recipe<?> recipe) {
			name = ExtendedAEPatternUploadUtil.mapRecipeTypeToSearchKey(recipe);
		} else if (recipeBase != null
			&& "com.gregtechceu.gtceu.api.recipe.GTRecipe".equals(recipeBase.getClass().getName())) {
			name = ExtendedAEPatternUploadUtil.mapGTCEuRecipeToSearchKey(recipeBase);
		} else if (recipeBase != null
			&& "com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper".equals(recipeBase.getClass().getName())) {
			try {
				var field = recipeBase.getClass().getField("recipe");
				Object inner = field.get(recipeBase);
				name = ExtendedAEPatternUploadUtil.mapGTCEuRecipeToSearchKey(inner);
			} catch (Throwable ignored) {
			}
		}

		if (name == null || name.isBlank()) {
			name = ExtendedAEPatternUploadUtil.deriveSearchKeyFromUnknownRecipe(recipeBase);
		}
		if (name != null && !name.isBlank()) {
			ExtendedAEPatternUploadUtil.setLastProcessingName(name);
		}
	}

	private static List<ItemStack> selectIngredientsWithJeiPriority(RecipeInfo recipeInfo) {
		List<?> bookmarks = JeiRuntimeProxy.getBookmarkList();
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

