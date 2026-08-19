package com.extendedae_plus.integration.jei;

import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Pseudo;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 线程安全地缓存并访问 JEI Runtime。
 */
@Pseudo
public final class JeiRuntimeProxy {
    private static volatile IJeiRuntime RUNTIME;

    private JeiRuntimeProxy() {
    }

    static void setRuntime(IJeiRuntime runtime) {
        RUNTIME = runtime;
    }

    @Nullable
    public static IJeiRuntime get() {
        return RUNTIME;
    }

    /** 根据 JEI 实际注册的配方对象统一解析分类标题，不依赖具体模组实现。 */
    public static String getRecipeCategorySearchKey(Object recipe) {
        IJeiRuntime runtime = RUNTIME;
        if (runtime == null || recipe == null) {
            return null;
        }

        try {
            IRecipeManager recipeManager = runtime.getRecipeManager();
            List<RecipeType<?>> candidates = runtime.getJeiHelpers().getAllRecipeTypes()
                    .filter(type -> type.getRecipeClass().isInstance(recipe))
                    .toList();
            RecipeType<?> matched = candidates.size() == 1
                    ? candidates.get(0)
                    : candidates.stream()
                            .filter(type -> containsRecipe(recipeManager, type, recipe))
                            .findFirst()
                            .orElse(null);
            if (matched == null) {
                return null;
            }

            String title = getCategoryTitle(recipeManager, matched);
            return RecipeTypeNameConfig.resolveRecipeTypeSearchKey(matched.getUid(), title);
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
        try {
            Class<?> mouseUtil = Class.forName("mezz.jei.gui.input.MouseUtil");
            double mouseX = ((Number) mouseUtil.getMethod("getX").invoke(null)).doubleValue();
            double mouseY = ((Number) mouseUtil.getMethod("getY").invoke(null)).doubleValue();
            return getIngredientUnderMouse(mouseX, mouseY);
        } catch (Throwable ignored) {
        }

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
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var method = bridge.getMethod("getIngredientUnderMouse", double.class, double.class);
            @SuppressWarnings("unchecked")
            Optional<ITypedIngredient<?>> result = (Optional<ITypedIngredient<?>>) method.invoke(null, mouseX, mouseY);
            if (result != null && result.isPresent()) {
                return result;
            }
        } catch (Throwable ignored) {
        }

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
     * 获取 JEI 书签列表。为避免在未安装 JEI GUI 时崩溃，使用反射委托到桥接类。
     */
    public static List<? extends ITypedIngredient<?>> getBookmarkList() {
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var m = bridge.getMethod("getBookmarkList");
            @SuppressWarnings("unchecked")
            List<? extends ITypedIngredient<?>> list = (List<? extends ITypedIngredient<?>>) m.invoke(null);
            return list == null ? Collections.emptyList() : list;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    public static void addBookmark(ItemStack stack) {
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var m = bridge.getMethod("addBookmark", ItemStack.class);
            m.invoke(null, stack);
        } catch (Throwable ignored) {
        }
    }

    public static void addBookmark(FluidStack fluidStack) {
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var m = bridge.getMethod("addBookmark", FluidStack.class);
            m.invoke(null, fluidStack);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 如果存在 Mekanism/appmek，则将 Mekanism 化学堆栈添加到 JEI 书签。
     */
    public static void addBookmark(Object chemicalStack) {
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var m = bridge.getMethod("addBookmark", Object.class);
            m.invoke(null, chemicalStack);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 获取鼠标下的配方书签（如果存在）
     * 
     * @return 配方书签对象（RecipeBookmark<?, ?>），如果不是配方书签则返回空
     */
    public static Optional<?> getRecipeBookmarkUnderMouse() {
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var m = bridge.getMethod("getRecipeBookmarkUnderMouse");
            @SuppressWarnings("unchecked")
            Optional<?> result = (Optional<?>) m.invoke(null);
            return result == null ? Optional.empty() : result;
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public static Optional<?> getBookmarkUnderMouse() {
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var m = bridge.getMethod("getBookmarkUnderMouse");
            @SuppressWarnings("unchecked")
            Optional<?> result = (Optional<?>) m.invoke(null);
            return result == null ? Optional.empty() : result;
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    // Note: helper methods moved to bridge to avoid referencing JEI GUI at class load time.

    /**
     * 从 JEI 书签移除物品（反射委托）
     */
    public static void removeBookmark(ItemStack stack) {
        try {
            Class<?> bridge = Class.forName("com.extendedae_plus.integration.jei.JeiBookmarkBridge");
            var m = bridge.getMethod("removeBookmark", ItemStack.class);
            m.invoke(null, stack);
        } catch (Throwable ignored) {
        }
    }
}
