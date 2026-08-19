package com.extendedae_plus.compat;

import com.extendedae_plus.mixin.jei.accessor.BookmarkListAccessor;
import com.extendedae_plus.mixin.jei.accessor.BookmarkOverlayAccessor;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JeiRuntimeCompat {
	private static volatile IJeiRuntime runtime;

	private JeiRuntimeCompat() {}

	public static void setRuntime(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}

	public static IJeiRuntime getRuntime() {
		return runtime;
	}

	/** 根据 JEI 实际注册的配方对象统一解析分类标题，不依赖具体模组实现。 */
	public static String getRecipeCategorySearchKey(Object recipe) {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null || recipe == null) {
			return null;
		}

		try {
			IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
			List<RecipeType<?>> candidates = jeiRuntime.getJeiHelpers().getAllRecipeTypes()
				.filter(type -> type.getRecipeClass().isInstance(recipe))
				.toList();
			RecipeType<?> matched = candidates.size() == 1
				? candidates.getFirst()
				: candidates.stream()
					.filter(type -> containsRecipe(recipeManager, type, recipe))
					.findFirst()
					.orElse(null);
			if (matched == null) {
				return null;
			}

			String title = getCategoryTitle(recipeManager, matched);
			return ExtendedAEPatternUploadUtil.resolveRecipeTypeSearchKey(matched.getUid(), title);
		} catch (Throwable ignored) {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean containsRecipe(IRecipeManager recipeManager, RecipeType<?> type, Object recipe) {
		return recipeManager.createRecipeLookup((RecipeType) type)
			.includeHidden()
			.get()
			.anyMatch(candidate -> candidate == recipe || Objects.equals(candidate, recipe));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String getCategoryTitle(IRecipeManager recipeManager, RecipeType<?> type) {
		return recipeManager.getRecipeCategory((RecipeType) type).getTitle().getString();
	}

	public static Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return Optional.empty();
		}
		Optional<ITypedIngredient<?>> ingredient = jeiRuntime.getIngredientListOverlay().getIngredientUnderMouse();
		return ingredient.isPresent() ? ingredient : jeiRuntime.getBookmarkOverlay().getIngredientUnderMouse();
	}

	public static Optional<ITypedIngredient<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return Optional.empty();
		}
		Optional<ITypedIngredient<?>> ingredient = getIngredientUnderMouse(jeiRuntime.getIngredientListOverlay(), mouseX, mouseY);
		return ingredient.isPresent() ? ingredient : getIngredientUnderMouse(jeiRuntime.getBookmarkOverlay(), mouseX, mouseY);
	}

	public static boolean isCheatModeEnabled() {
		return Internal.getClientToggleState().isCheatItemsEnabled();
	}

	public static String getTypedIngredientDisplayName(Object typed) {
		if (!(typed instanceof ITypedIngredient<?> typedIngredient)) {
			return "";
		}
		return getTypedIngredientDisplayName(typedIngredient);
	}

	public static Object getTypedIngredientValue(Object typed) {
		if (typed instanceof ITypedIngredient<?> typedIngredient) {
			return typedIngredient.getIngredient();
		}
		return null;
	}

	private static <T> String getTypedIngredientDisplayName(ITypedIngredient<T> typed) {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return "";
		}
		try {
			IIngredientHelper<T> helper = jeiRuntime.getIngredientManager().getIngredientHelper(typed.getType());
            return helper.getDisplayName(typed.getIngredient());
		} catch (Throwable ignored) {
			return "";
		}
	}

	public static List<ITypedIngredient<?>> getBookmarkList() {
		IJeiRuntime jeiRuntime = runtime;
		if (!(jeiRuntime != null && jeiRuntime.getBookmarkOverlay() instanceof BookmarkOverlay overlay)) {
			return Collections.emptyList();
		}
		return ((BookmarkOverlayAccessor) overlay).eap$getBookmarkList().getElements().stream()
			.<ITypedIngredient<?>>map(IElement::getTypedIngredient)
			.toList();
	}

	public static Optional<?> getBookmarkUnderMouse() {
		IJeiRuntime jeiRuntime = runtime;
		if (!(jeiRuntime != null && jeiRuntime.getBookmarkOverlay() instanceof BookmarkOverlay overlay)) {
			return Optional.empty();
		}
		return overlay.getIngredientUnderMouse(getGuiMouseX(), getGuiMouseY())
			.map(IClickableIngredientInternal::getElement)
			.map(IElement::getBookmark)
			.flatMap(Optional::stream)
			.findFirst();
	}

	public static Optional<?> getRecipeBookmarkUnderMouse() {
		return getBookmarkUnderMouse().filter(RecipeBookmark.class::isInstance);
	}

	public static void addBookmark(ItemStack stack) {
		if (stack != null && !stack.isEmpty()) {
			addBookmarkInternal(VanillaTypes.ITEM_STACK, stack);
		}
	}

	public static void addBookmark(FluidStack stack) {
		if (stack != null && !stack.isEmpty()) {
			addBookmarkInternal(NeoForgeTypes.FLUID_STACK, stack);
		}
	}

	public static void addBookmark(Object chemicalStack) {
		if (chemicalStack != null) {
			addBookmarkUnchecked(chemicalStack);
		}
	}

	private static <T> void addBookmarkInternal(IIngredientType<T> type, T ingredient) {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null || !(jeiRuntime.getBookmarkOverlay() instanceof BookmarkOverlay overlay)) {
			return;
		}
		BookmarkList bookmarkList = ((BookmarkOverlayAccessor) overlay).eap$getBookmarkList();
		jeiRuntime.getIngredientManager().createTypedIngredient(type, ingredient, false)
			.map(((BookmarkListAccessor) bookmarkList).eap$getBookmarkFactory()::create)
                  .ifPresent(bookmarkList::add);
	}

	private static void addBookmarkUnchecked(Object ingredient) {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null || !(jeiRuntime.getBookmarkOverlay() instanceof BookmarkOverlay overlay)) {
			return;
		}
		BookmarkList bookmarkList = ((BookmarkOverlayAccessor) overlay).eap$getBookmarkList();
		jeiRuntime.getIngredientManager().createTypedIngredient(ingredient, false)
			.map(((BookmarkListAccessor) bookmarkList).eap$getBookmarkFactory()::create)
			.ifPresent(bookmarkList::add);
	}

	private static Optional<ITypedIngredient<?>> getIngredientUnderMouse(Object overlay, double mouseX, double mouseY) {
		if (overlay instanceof IngredientListOverlay ingredientListOverlay) {
			return ingredientListOverlay.getIngredientUnderMouse(mouseX, mouseY)
				.<ITypedIngredient<?>>map(IClickableIngredientInternal::getTypedIngredient)
				.findFirst();
		}
		if (overlay instanceof BookmarkOverlay bookmarkOverlay) {
			return bookmarkOverlay.getIngredientUnderMouse(mouseX, mouseY)
				.<ITypedIngredient<?>>map(IClickableIngredientInternal::getTypedIngredient)
				.findFirst();
		}
		return Optional.empty();
	}

	private static double getGuiMouseX() {
		var minecraft = Minecraft.getInstance();
		return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
	}

	private static double getGuiMouseY() {
		var minecraft = Minecraft.getInstance();
		return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
	}
}
