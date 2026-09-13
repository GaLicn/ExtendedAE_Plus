package com.extendedae_plus_gtladd.util;

import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;

/** EAEP 已包含倍增信息的样板通用检查。 */
public final class EapScaledPatternCompat {

    private EapScaledPatternCompat() {
    }

    public static boolean isScaled(IPatternDetails pattern) {
        return pattern instanceof ScaledProcessingPattern
                || pattern instanceof ScaledMolecularAssemblerPattern;
    }

    /**
     * 返回原始样板身份，同时不修改调用方传入的计数器。
     * 计数器仍包含倍增后的批次数量。
     */
    public static IPatternDetails unwrap(IPatternDetails pattern) {
        if (pattern instanceof ScaledProcessingPattern scaled) {
            return scaled.getOriginal();
        }
        if (pattern instanceof ScaledMolecularAssemblerPattern scaled) {
            return scaled.getOriginal();
        }
        return pattern;
    }
}
