package com.extendedae_plus.integration.jei;

import com.extendedae_plus.mixin.jei.accessor.BookmarkOverlayAccessor;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Pseudo;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 将所有会引用 JEI GUI 内部类（如 BookmarkList、IngredientBookmark、IElement 等）的逻辑
 * 隔离在此桥接类中，避免在未安装 JEI 或 JEI 组件不完整时过早类加载导致的 NoClassDefFoundError。
 *
 * 该类仅会被 {@link JeiRuntimeProxy} 通过反射调用。
 */
@Pseudo
public final class JeiBookmarkBridge {
    private JeiBookmarkBridge() {}

    // 通过 JeiRuntimeProxy 的包内可见方法安全地获取 Runtime
    private static @Nullable IJeiRuntime getRuntime() {
        try {
            Class<?> proxy = Class.forName("com.extendedae_plus.integration.jei.JeiRuntimeProxy");
            var m = proxy.getDeclaredMethod("get");
            Object rt = m.invoke(null);
            return (IJeiRuntime) rt;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static List<? extends ITypedIngredient<?>> getBookmarkList() {
        IJeiRuntime rt = getRuntime();
        if (rt == null) return Collections.emptyList();
        IBookmarkOverlay bookmarkOverlay = rt.getBookmarkOverlay();
        if (bookmarkOverlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList bookmarkList = accessor.eap$getBookmarkList();
            return bookmarkList.getElements().stream().map(IElement::getTypedIngredient).toList();
        }
        return Collections.emptyList();
    }

    public static void addBookmark(ItemStack stack) {
        IJeiRuntime rt = getRuntime();
        if (rt == null) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            Optional<ITypedIngredient<ItemStack>> typedOpt = rt.getIngredientManager()
                .createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
            typedOpt.ifPresent(typed -> {
                IngredientBookmark<ItemStack> bookmark = IngredientBookmark.create(typed, rt.getIngredientManager());
                list.add(bookmark); // add 内部会自动保存到配置
            });
        }
    }

    public static void addBookmark(FluidStack fluidStack) {
        IJeiRuntime rt = getRuntime();
        if (rt == null) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            Optional<ITypedIngredient<FluidStack>> typedOpt = rt.getIngredientManager()
                .createTypedIngredient(ForgeTypes.FLUID_STACK, fluidStack);
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

        IJeiRuntime rt = getRuntime();
        if (rt == null) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            try {
                if (chemicalStack == null) return;

                // Determine Mekanism JEI ingredient type constant by runtime class name
                String clsName = chemicalStack.getClass().getName();
                String mekanismJeiClass = "mekanism.client.jei.MekanismJEI";
                Class<?> jeiCls = Class.forName(mekanismJeiClass);
                Field typeField = getField(clsName, jeiCls);

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
                            list.add((IngredientBookmark) bookmark);
                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private static @Nullable Field getField(String clsName, Class<?> jeiCls) throws NoSuchFieldException {
        Field typeField = null;
        if ("mekanism.api.chemical.gas.GasStack".equals(clsName)) {
            typeField = jeiCls.getField("TYPE_GAS");
        } else if ("mekanism.api.chemical.infuse.InfusionStack".equals(clsName)) {
            typeField = jeiCls.getField("TYPE_INFUSION");
        } else if ("mekanism.api.chemical.pigment.PigmentStack".equals(clsName)) {
            typeField = jeiCls.getField("TYPE_PIGMENT");
        } else if ("mekanism.api.chemical.slurry.SlurryStack".equals(clsName)) {
            typeField = jeiCls.getField("TYPE_SLURRY");
        }
        return typeField;
    }

    /**
     * 从 JEI 书签移除物品
     */
    public static void removeBookmark(ItemStack stack) {
        IJeiRuntime rt = getRuntime();
        if (rt == null) return;

        IBookmarkOverlay overlay = rt.getBookmarkOverlay();
        if (overlay instanceof BookmarkOverlayAccessor accessor) {
            BookmarkList list = accessor.eap$getBookmarkList();
            Optional<ITypedIngredient<ItemStack>> typedOpt = rt.getIngredientManager()
                .createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
            typedOpt.ifPresent(typed -> {
                IngredientBookmark<ItemStack> bookmark = IngredientBookmark.create(typed, rt.getIngredientManager());
                list.remove(bookmark);
            });
        }
    }
}
