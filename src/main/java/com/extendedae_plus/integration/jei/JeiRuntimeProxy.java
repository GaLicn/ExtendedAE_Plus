package com.extendedae_plus.integration.jei;

import com.extendedae_plus.mixin.jei.accessor.BookmarkOverlayAccessor;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    /**
     * 检测 JEI 是否开启了作弊模式（给物品）。
     * 使用 JEI 内部开关，若 JEI 未初始化或异常则返回 false。
     */
    public static boolean isJeiCheatModeEnabled() {
        try {
            // 使用完全限定名以避免在源码缺失时的编译依赖问题
            return mezz.jei.common.Internal.getClientToggleState().isCheatItemsEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 将文本写入 JEI 的搜索过滤框。
     * 若 JEI runtime 不可用则静默返回。
     */
    public static void setIngredientFilterText(String text) {
        IJeiRuntime rt = RUNTIME;
        if (rt == null) return;
        try {
            rt.getIngredientFilter().setFilterText(text == null ? "" : text);
        } catch (Throwable ignored) {
            // 兼容不同 JEI 版本或在启动阶段尚未就绪
        }
    }

    /**
     * 通用获取 JEI 悬浮配料的本地化显示名称（适配物品/流体等）。
     * 若无法安全获取，则返回空字符串。
     */
    public static <T> String getTypedIngredientDisplayName(ITypedIngredient<T> typed) {
        IJeiRuntime rt = RUNTIME;
        if (rt == null || typed == null) return "";
        try {
            var manager = rt.getIngredientManager();
            var helper = manager.getIngredientHelper(typed.getType());
            // JEI 的 IIngredientHelper#getDisplayName 返回 Component（新版本）或 String（旧版本）
            // 统一转为字符串，使用 toString() 兜底
            Object display = helper.getDisplayName(typed.getIngredient());
            if (display == null) return "";
            // 新版：net.minecraft.network.chat.Component
            if (display instanceof net.minecraft.network.chat.Component comp) {
                String s = comp.getString();
                return s == null ? "" : s;
            }
            String s = display.toString();
            return s == null ? "" : s;
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 获取JEI书签列表
     */
    public static List<? extends ITypedIngredient<?>> getBookmarkList() {
        IJeiRuntime rt = RUNTIME;
        if (rt == null) return Collections.emptyList();
        IBookmarkOverlay bookmarkOverlay = rt.getBookmarkOverlay();
        if (bookmarkOverlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList bookmarkList = accessor.eap$getBookmarkList();
            return bookmarkList.getElements().stream().map(IElement::getTypedIngredient).toList();
        }
        return Collections.emptyList();
    }

    /**
     * 将物品添加到 JEI 书签
     */
    public static void addBookmark(ItemStack stack) {
        IJeiRuntime rt = RUNTIME;
        if (rt == null || stack == null || stack.isEmpty()) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            try {
                var typedOpt = rt.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
                typedOpt.ifPresent(typed -> {
                    IngredientBookmark<ItemStack> bookmark = IngredientBookmark.create(typed, rt.getIngredientManager());
                    list.add(bookmark); // add 内部会自动保存到配置
                });
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 从 JEI 书签移除物品
     */
    public static void removeBookmark(ItemStack stack) {
        IJeiRuntime rt = RUNTIME;
        if (rt == null || stack == null || stack.isEmpty()) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            try {
                var typedOpt = rt.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
                typedOpt.ifPresent(typed -> {
                    IngredientBookmark<ItemStack> bookmark = IngredientBookmark.create(typed, rt.getIngredientManager());
                    list.remove(bookmark);
                });
            } catch (Throwable ignored) {}
        }
    }
}
