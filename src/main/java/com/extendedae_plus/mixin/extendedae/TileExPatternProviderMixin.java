package com.extendedae_plus.mixin.extendedae;

import com.extendedae_plus.config.ModConfigs;
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
    private int extendedae_plus$multiplyCapacity(int original) {
        int mult = ModConfigs.PAGE_MULTIPLIER.get();
        if (mult < 1) mult = 1;
        if (mult > 64) mult = 64;
        return Math.max(1, original) * mult;
    }
}
