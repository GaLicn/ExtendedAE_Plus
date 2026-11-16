package com.extendedae_plus.compat;

import appeng.parts.AEBasePart;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;

public final class AdvancedAEPatternProviderCompat {
    private AdvancedAEPatternProviderCompat() {
    }

    public static boolean isPatternProvider(AEBasePart part) {
        return part instanceof AdvPatternProviderLogicHost;
    }
}
