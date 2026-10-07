package com.extendedae_plus.compat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.IMekanismAccess;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientType;

import javax.annotation.Nullable;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * Applied Mekanistics（appmek）兼容层。
 *
 * <p>本类直接引用 appmek 与 Mekanism 的类型，JVM 在链接本类时即须解析这些类型，
 * 因此调用方必须先经 {@code ModList.get().isLoaded("appmek")} 与
 * {@code ModList.get().isLoaded("mekanism")} 判定两个模组均已加载，再调用本类方法。
 * 缺少该判定的调用会在守卫位置抛出 {@link NoClassDefFoundError}。</p>
 *
 * <p>约束：判定必须写在调用方且使用 {@code ModList}。不得由本类提供 isLoaded 之类的
 * 方法供调用方判定——调用本类的任何方法都会触发类链接，使守卫失效。</p>
 */
public final class AppliedMekanisticsCompat {

    private AppliedMekanisticsCompat() {
    }

    public static boolean isChemicalType(@Nullable IIngredientType<?> type) {
        if (type == null) {
            return false;
        }

        try {
            return type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                    .getGasStackHelper().getIngredientType().getIngredientClass()
                    || type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                            .getInfusionStackHelper().getIngredientType().getIngredientClass()
                    || type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                            .getPigmentStackHelper().getIngredientType().getIngredientClass()
                    || type.getIngredientClass() == IMekanismAccess.INSTANCE.jeiHelper()
                            .getSlurryStackHelper().getIngredientType().getIngredientClass();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static AEKey toKey(@Nullable Object ingredient) {
        if (!(ingredient instanceof ChemicalStack<?> chemicalStack)) {
            return null;
        }

        try {
            return MekanismKey.of(chemicalStack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * JEI 化学品条目 → AE2 {@link GenericStack}。
     *
     * <p>JEI 悬停给出的是已携带数量的 {@link ChemicalStack}，与 {@link #toKey(Object)}
     * 只取键不同，本方法保留栈内数量。两侧数量单位同为 mB，因此直接采用。</p>
     *
     * @param ingredient JEI 侧化学品条目
     * @return 对应的 AE2 GenericStack；入参不是非空化学品栈时返回 null
     */
    @Nullable
    public static GenericStack fromJeiIngredient(@Nullable Object ingredient) {
        if (!(ingredient instanceof ChemicalStack<?> chemicalStack) || chemicalStack.isEmpty()) {
            return null;
        }

        try {
            AEKey key = MekanismKey.of(chemicalStack);
            return key == null ? null : new GenericStack(key, chemicalStack.getAmount());
        } catch (Throwable error) {
            EAP$LOGGER.warn("Mekanism 化学品栈转换为 AE2 GenericStack 失败", error);
            return null;
        }
    }
}