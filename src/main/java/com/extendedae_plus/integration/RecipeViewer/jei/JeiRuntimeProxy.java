package com.extendedae_plus.integration.RecipeViewer.jei;

import com.extendedae_plus.mixin.recipeViewer.jei.accessor.AccessorBookmarkOverlay;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
            if (ing.isPresent()) return ing;
        }
        IBookmarkOverlay bm = rt.getBookmarkOverlay();
        if (bm != null) {
            var ing = bm.getIngredientUnderMouse();
            if (ing.isPresent()) return ing;
        }
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
     * 获取JEI书签列表
     */
    public static List<? extends ITypedIngredient<?>> getBookmarkList() {
        IJeiRuntime rt = RUNTIME;
        if (rt == null) return Collections.emptyList();
        IBookmarkOverlay bookmarkOverlay = rt.getBookmarkOverlay();
        if (bookmarkOverlay instanceof AccessorBookmarkOverlay accessor) {
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
        if (overlay instanceof AccessorBookmarkOverlay accessor) {
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

    public static void addBookmark(FluidStack fluidStack) {
        IJeiRuntime rt = RUNTIME;
        if (rt == null) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof AccessorBookmarkOverlay accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            Optional<ITypedIngredient<FluidStack>> typedOpt = rt.getIngredientManager()
                    .createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluidStack);
            typedOpt.ifPresent(typed -> {
                IngredientBookmark<FluidStack> bookmark = IngredientBookmark.create(typed, rt.getIngredientManager());
                list.add(bookmark); // add 内部会自动保存到配置
            });
        }
    }

    /**
     * 如果存在 Mekanism/appmek，则将 Mekanism 化学堆栈添加到 JEI 书签。
     */
    public static void addBookmark(Object chemicalStack) {
        if (!ModList.get().isLoaded("mekanism") && !ModList.get().isLoaded("appmek")) return;

        IJeiRuntime rt = RUNTIME;
        if (rt == null) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof AccessorBookmarkOverlay accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            try {
                if (chemicalStack == null) return;

                // Determine Mekanism JEI ingredient type constant by runtime class name
                String clsName = chemicalStack.getClass().getName();
                String mekanismJeiClass = "mekanism.client.recipe_viewer.jei.MekanismJEI";
                Class<?> jeiCls = Class.forName(mekanismJeiClass);
                Field typeField = null;
                if ("mekanism.api.chemical.ChemicalStack".equals(clsName)) {
                    typeField = jeiCls.getField("TYPE_CHEMICAL");
                }
                if (typeField == null) return;
                Object typeConst = typeField.get(null);

                // Use ingredient manager reflectively to create a typed ingredient
                Object ingredientManager = rt.getIngredientManager();
                Method createTypedIngredient = ingredientManager.getClass().getMethod("createTypedIngredient", IIngredientType.class, Object.class);
                Object opt = createTypedIngredient.invoke(ingredientManager, typeConst, chemicalStack);
                if (!(opt instanceof Optional<?> typedOpt)) return;
                if (typedOpt.isPresent()) {
                    Object typed = typedOpt.get();
                    // Find a compatible static create(...) method on IngredientBookmark where
                    // the second parameter is assignable from the actual ingredientManager instance.
                    Method createMethod = null;
                    for (Method m : IngredientBookmark.class.getMethods()) {
                        if (!m.getName().equals("create")) continue;
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length != 2) continue;
                        // first param should accept the typed ingredient
                        boolean firstOk = params[0].isAssignableFrom(typed.getClass()) || params[0].isAssignableFrom(ITypedIngredient.class);
                        boolean secondOk = params[1].isAssignableFrom(ingredientManager.getClass());
                        if (firstOk && secondOk) {
                            createMethod = m;
                            break;
                        }
                    }
                    if (createMethod != null) {
                        Object bookmark = createMethod.invoke(null, typed, ingredientManager);
                        if (bookmark != null) {
                            list.add((IngredientBookmark<?>) bookmark);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}