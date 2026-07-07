package com.extendedae_plus.mixin.extendedae.common;

import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = TileExPatternProvider.class, priority = 3000, remap = false)
public abstract class TileExPatternProviderMixin {

    @ModifyArg(
            method = "createLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/helpers/patternprovider/PatternProviderLogic;<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V"
            ),
            index = 2
    )
    private int eap$expandCapacity(int original) {
        // 固定预留 4 页物理容量，实际可用页数由扩容卡解锁。
        return Math.max(original, UpgradeSlotCompat.getExtendedPatternProviderPatternCapacity());
    }
}
