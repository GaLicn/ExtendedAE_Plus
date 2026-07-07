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
        // 扩展样板供应器固定预留最大容量，实际启用页数由扩容卡控制。
        return Math.max(original, UpgradeSlotCompat.getExtendedPatternProviderPatternCapacity());
    }
}
