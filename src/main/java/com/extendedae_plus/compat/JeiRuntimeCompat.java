package com.extendedae_plus.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.overlay.IngredientListOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
		if (ingredient.isPresent()) {
			return ingredient;
		}
		ingredient = jeiRuntime.getBookmarkOverlay().getIngredientUnderMouse();
		return ingredient.isPresent() ? ingredient : getRecipeIngredientUnderMouse(jeiRuntime);
	}

	public static Optional<ITypedIngredient<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return Optional.empty();
		}
		Optional<ITypedIngredient<?>> ingredient = getIngredientUnderMouse(jeiRuntime.getIngredientListOverlay(), mouseX, mouseY);
		if (ingredient.isPresent()) {
			return ingredient;
		}
		ingredient = getIngredientUnderMouse(jeiRuntime.getBookmarkOverlay(), mouseX, mouseY);
		return ingredient.isPresent() ? ingredient : getRecipeIngredientUnderMouse(jeiRuntime);
	}

	private static Optional<ITypedIngredient<?>> getRecipeIngredientUnderMouse(IJeiRuntime jeiRuntime) {
		try {
			return jeiRuntime.getRecipesGui()
				.getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
				.flatMap(stack -> jeiRuntime.getIngredientManager()
					.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false)
					.map(typed -> (ITypedIngredient<?>) typed));
		} catch (Throwable ignored) {
			return Optional.empty();
		}
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
		if (jeiRuntime == null) {
			return Collections.emptyList();
		}
		Object overlay = safeInvoke(jeiRuntime, "getBookmarkOverlay");
		if (!isBookmarkOverlay(overlay)) {
			return Collections.emptyList();
		}
		Object bookmarkList = safeReadField(overlay, "bookmarkList");
		if (bookmarkList == null) {
			return Collections.emptyList();
		}
		Object elements = safeInvoke(bookmarkList, "getElements");
		if (!(elements instanceof Iterable<?> iterable)) {
			return Collections.emptyList();
		}
		List<ITypedIngredient<?>> result = new ArrayList<>();
		for (Object element : iterable) {
			Object typed = safeInvoke(element, "getTypedIngredient");
			if (typed instanceof ITypedIngredient<?> typedIngredient) {
				result.add(typedIngredient);
			}
		}
		return result;
	}

	public static Optional<?> getBookmarkUnderMouse() {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return Optional.empty();
		}
		Object overlay = safeInvoke(jeiRuntime, "getBookmarkOverlay");
		if (!isBookmarkOverlay(overlay)) {
			return Optional.empty();
		}
		Object hovered = safeInvoke(overlay, "getIngredientUnderMouse", new Class<?>[]{double.class, double.class}, getGuiMouseX(), getGuiMouseY());
		if (!(hovered instanceof Optional<?> optionalHovered)) {
			return Optional.empty();
		}
		for (Object hoveredIngredient : optionalHovered.stream().toList()) {
			Object element = safeInvoke(hoveredIngredient, "getElement");
			Object bookmark = safeInvoke(element, "getBookmark");
			if (bookmark != null) {
				return Optional.of(bookmark);
			}
		}
		return Optional.empty();
	}

	public static Optional<?> getRecipeBookmarkUnderMouse() {
		return getBookmarkUnderMouse();
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
		if (jeiRuntime == null) {
			return;
		}
		Object overlay = safeInvoke(jeiRuntime, "getBookmarkOverlay");
		if (!isBookmarkOverlay(overlay)) {
			return;
		}
		Object bookmarkList = safeReadField(overlay, "bookmarkList");
		if (bookmarkList == null) {
			return;
		}
		Object bookmarkFactory = safeReadField(bookmarkList, "bookmarkFactory");
		if (bookmarkFactory == null) {
			return;
		}
		jeiRuntime.getIngredientManager().createTypedIngredient(type, ingredient, false)
			.map(item -> safeInvokeFactory(bookmarkFactory, item))
			.ifPresent(bookmark -> safeInvoke(bookmarkList, "add", new Class<?>[]{Object.class}, bookmark));
	}

	private static void addBookmarkUnchecked(Object ingredient) {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return;
		}
		Object overlay = safeInvoke(jeiRuntime, "getBookmarkOverlay");
		if (!isBookmarkOverlay(overlay)) {
			return;
		}
		Object bookmarkList = safeReadField(overlay, "bookmarkList");
		if (bookmarkList == null) {
			return;
		}
		Object bookmarkFactory = safeReadField(bookmarkList, "bookmarkFactory");
		if (bookmarkFactory == null) {
			return;
		}
		jeiRuntime.getIngredientManager().createTypedIngredient(ingredient, false)
			.map(item -> safeInvokeFactory(bookmarkFactory, item))
			.ifPresent(bookmark -> safeInvoke(bookmarkList, "add", new Class<?>[]{Object.class}, bookmark));
	}

	private static Optional<ITypedIngredient<?>> getIngredientUnderMouse(Object overlay, double mouseX, double mouseY) {
		if (overlay instanceof IngredientListOverlay ingredientListOverlay) {
			return ingredientListOverlay.getIngredientUnderMouse(mouseX, mouseY)
				.<ITypedIngredient<?>>map(IClickableIngredientInternal::getTypedIngredient)
				.findFirst();
		}
		if (isBookmarkOverlay(overlay)) {
			Object result = safeInvoke(overlay, "getIngredientUnderMouse", new Class<?>[]{double.class, double.class}, mouseX, mouseY);
			if (result instanceof Optional<?> optional) {
				for (Object candidate : optional.stream().toList()) {
					Object typed = safeInvoke(candidate, "getTypedIngredient");
					if (typed instanceof ITypedIngredient<?> ingredient) {
						return Optional.of(ingredient);
					}
				}
			}
		}
		return Optional.empty();
	}

	private static boolean isBookmarkOverlay(Object overlay) {
		if (overlay == null) {
			return false;
		}
		try {
			Class<?> bookmarkOverlayClass = Class.forName("mezz.jei.gui.overlay.bookmarks.BookmarkOverlay");
			return bookmarkOverlayClass.isInstance(overlay);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static Object safeInvoke(Object target, String methodName) {
		return safeInvoke(target, methodName, new Class<?>[0], new Object[0]);
	}

	private static Object safeInvoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
		if (target == null) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(methodName, parameterTypes);
			return method.invoke(target, args);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object safeReadField(Object target, String fieldName) {
		if (target == null) {
			return null;
		}
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(target);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object safeInvokeFactory(Object bookmarkFactory, Object ingredient) {
		try {
			Method create = bookmarkFactory.getClass().getMethod("create", Object.class);
			return create.invoke(bookmarkFactory, ingredient);
		} catch (Throwable ignored) {
			try {
				Method create = bookmarkFactory.getClass().getMethod("create", Object.class, boolean.class);
				return create.invoke(bookmarkFactory, ingredient, false);
			} catch (Throwable ignoredAgain) {
				return null;
			}
		}
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
