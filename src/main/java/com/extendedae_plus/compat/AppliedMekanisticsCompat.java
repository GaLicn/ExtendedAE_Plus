package com.extendedae_plus.compat;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.IMekanismAccess;
import mekanism.api.chemical.ChemicalStack;
import mezz.jei.api.ingredients.IIngredientType;

import javax.annotation.Nullable;

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
}