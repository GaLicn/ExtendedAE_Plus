package com.extendedae_plus.integration.jei;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.input.MouseUtil;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** 直接使用 JEI 15.74 的元素和书签接口，不再反射探测旧版字段与方法。 */
public final class JeiBookmarkBridge {
    private JeiBookmarkBridge() {
    }

    public static List<? extends ITypedIngredient<?>> getBookmarkList() {
        IJeiRuntime runtime = JeiRuntimeProxy.get();
        if (runtime == null) return List.of();
        return ((BookmarkOverlay) runtime.getBookmarkOverlay()).getBookmarkList().getElements()
                .stream().map(IElement::getTypedIngredient).toList();
    }

    public static Optional<ITypedIngredient<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
        return getIngredientsUnderMouse(mouseX, mouseY)
                .<ITypedIngredient<?>>map(IClickableIngredientInternal::getTypedIngredient)
                .findFirst();
    }

    private static Stream<IClickableIngredientInternal<?>> getIngredientsUnderMouse(double mouseX, double mouseY) {
        IJeiRuntime runtime = JeiRuntimeProxy.get();
        if (runtime == null) return Stream.empty();

        // 两侧叠层都会返回各自的历史记录；配方界面也保留流体和化学品的类型信息。
        return Stream.of(runtime.getIngredientListOverlay(), runtime.getBookmarkOverlay(), runtime.getRecipesGui())
                .map(IRecipeFocusSource.class::cast)
                .flatMap(source -> source.getIngredientUnderMouse(mouseX, mouseY));
    }

    public static void addBookmark(Object ingredient) {
        IJeiRuntime runtime = JeiRuntimeProxy.get();
        if (runtime == null || ingredient == null) return;

        // 新版 IngredientManager 根据已注册类型识别物品、流体及 Mekanism 化学品。
        runtime.getIngredientManager().createTypedIngredient(ingredient)
                .ifPresent(typed -> ((BookmarkOverlay) runtime.getBookmarkOverlay()).getBookmarkList().add(typed));
    }

    public static Optional<IBookmark> getBookmarkUnderMouse() {
        return getIngredientsUnderMouse(MouseUtil.getX(), MouseUtil.getY())
                .flatMap(clickable -> clickable.getElement().getBookmark().stream())
                .findFirst();
    }

    public static Optional<RecipeBookmark<?, ?>> getRecipeBookmarkUnderMouse() {
        return getBookmarkUnderMouse().flatMap(bookmark -> {
            if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
                return Optional.of(recipeBookmark);
            }
            return Optional.empty();
        });
    }

    public static void removeBookmark(Object ingredient) {
        IJeiRuntime runtime = JeiRuntimeProxy.get();
        if (runtime == null || ingredient == null) return;
        runtime.getIngredientManager().createTypedIngredient(ingredient)
                .ifPresent(typed -> ((BookmarkOverlay) runtime.getBookmarkOverlay()).getBookmarkList().remove(typed));
    }
}
