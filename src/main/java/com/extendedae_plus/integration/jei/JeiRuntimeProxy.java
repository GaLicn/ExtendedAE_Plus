package com.extendedae_plus.integration.jei;

import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.input.MouseUtil;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/** 缓存当前世界的 JEI Runtime，所有入口均在运行时就绪后调用新版接口。 */
public final class JeiRuntimeProxy {
    private static volatile @Nullable IJeiRuntime runtime;

    private JeiRuntimeProxy() {
    }

    static void setRuntime(@Nullable IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Nullable
    public static IJeiRuntime get() {
        return runtime;
    }

    @Nullable
    public static String getRecipeCategorySearchKey(Object recipe) {
        IJeiRuntime current = runtime;
        if (current == null || recipe == null) return null;

        try {
            IRecipeManager recipeManager = current.getRecipeManager();
            List<RecipeType<?>> candidates = current.getJeiHelpers().getAllRecipeTypes()
                    .filter(type -> type.getRecipeClass().isInstance(recipe))
                    .toList();
            RecipeType<?> matched = candidates.size() == 1 ? candidates.get(0)
                    : candidates.stream().filter(type -> containsRecipe(recipeManager, type, recipe))
                            .findFirst().orElse(null);
            if (matched == null) return null;
            String title = recipeManager.getRecipeCategory(matched).getTitle().getString();
            return RecipeTypeNameConfig.resolveRecipeTypeSearchKey(matched.getUid(), title);
        } catch (RuntimeException error) {
            EAP$LOGGER.warn("读取 JEI 配方分类失败", error);
            return null;
        }
    }

    private static <T> boolean containsRecipe(IRecipeManager manager, RecipeType<T> type, Object recipe) {
        return manager.createRecipeLookup(type).includeHidden().get()
                .anyMatch(candidate -> candidate == recipe || Objects.equals(candidate, recipe));
    }

    public static Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
        return getIngredientUnderMouse(MouseUtil.getX(), MouseUtil.getY());
    }

    public static Optional<ITypedIngredient<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
        return JeiBookmarkBridge.getIngredientUnderMouse(mouseX, mouseY);
    }

    public static boolean isJeiCheatModeEnabled() {
        return runtime != null && Internal.getClientToggleState().isCheatItemsEnabled();
    }

    public static void setIngredientFilterText(@Nullable String text) {
        IJeiRuntime current = runtime;
        if (current != null) current.getIngredientFilter().setFilterText(text == null ? "" : text);
    }

    public static <T> String getTypedIngredientDisplayName(@Nullable ITypedIngredient<T> ingredient) {
        IJeiRuntime current = runtime;
        if (current == null || ingredient == null) return "";
        return current.getIngredientManager().getIngredientHelper(ingredient.getType())
                .getDisplayName(ingredient.getIngredient());
    }

    public static List<? extends ITypedIngredient<?>> getBookmarkList() {
        return JeiBookmarkBridge.getBookmarkList();
    }

    public static void addBookmark(ItemStack stack) {
        JeiBookmarkBridge.addBookmark(stack);
    }

    public static void addBookmark(FluidStack stack) {
        JeiBookmarkBridge.addBookmark(stack);
    }

    public static void addBookmark(Object ingredient) {
        JeiBookmarkBridge.addBookmark(ingredient);
    }

    public static Optional<RecipeBookmark<?, ?>> getRecipeBookmarkUnderMouse() {
        return JeiBookmarkBridge.getRecipeBookmarkUnderMouse();
    }

    public static Optional<IBookmark> getBookmarkUnderMouse() {
        return JeiBookmarkBridge.getBookmarkUnderMouse();
    }

    public static void removeBookmark(ItemStack stack) {
        JeiBookmarkBridge.removeBookmark(stack);
    }
}
