package com.extendedae_plus.integration.jei;

import java.util.Optional;

import javax.annotation.Nullable;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * 线程安全地缓存并访问 JEI Runtime。
 */
public final class JeiRuntimeProxy {
    private static volatile IJeiRuntime RUNTIME;

    private JeiRuntimeProxy() {}

    static void setRuntime(IJeiRuntime runtime) {
        RUNTIME = runtime;
    }

    @Nullable
    public static IJeiRuntime get() {
        return RUNTIME;
    }

    public static Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
        IJeiRuntime rt = RUNTIME;
        if (rt == null) return Optional.empty();

        IIngredientListOverlay list = rt.getIngredientListOverlay();
        if (list != null) {
            var ing = list.getIngredientUnderMouse();
            if (ing.isPresent()) return ing.map(i -> (ITypedIngredient<?>) i);
        }
        IBookmarkOverlay bm = rt.getBookmarkOverlay();
        if (bm != null) {
            var ing = bm.getIngredientUnderMouse();
            if (ing.isPresent()) return ing.map(i -> (ITypedIngredient<?>) i);
        }
        return Optional.empty();
    }

    /**
     * 在 JEI 配方界面区域内，基于屏幕坐标查询鼠标下的配料（优先物品，其次流体）。
     */
    public static Optional<ITypedIngredient<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
        IJeiRuntime rt = RUNTIME;
        if (rt == null || rt.getRecipesGui() == null) return Optional.empty();

        var ingredientManager = rt.getIngredientManager();

        // 支持物品（通用且所有版本可用）。如需流体可后续按版本判断再扩展
        var item = rt.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK)
                .flatMap(v -> ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, v))
                .map(x -> (ITypedIngredient<?>) x);
        if (item.isPresent()) return Optional.of(item.get());

        return Optional.empty();
    }
}
