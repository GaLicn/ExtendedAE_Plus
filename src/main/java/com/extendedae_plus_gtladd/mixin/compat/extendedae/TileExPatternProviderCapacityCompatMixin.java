package com.extendedae_plus_gtladd.mixin.compat.extendedae;

import com.bawnorton.mixinsquared.TargetHandler;
import com.extendedae_plus_gtladd.util.ExtendedAePatternCapacityCompat;
import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the EAEP four-page backing inventory when GTLCore changes the
 * per-page capacity in its TileExPatternProvider mixin.
 */
@Mixin(value = TileExPatternProvider.class, priority = 4000, remap = false)
public abstract class TileExPatternProviderCapacityCompatMixin {

    @TargetHandler(
            mixin = "org.gtlcore.gtlcore.mixin.extendedae.TileExPatternProviderMixin",
            name = "modifyContainer"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void eap$scaleGtlcoreBackingCapacity(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ExtendedAePatternCapacityCompat.getGtlcoreBackingPatternCapacity(
                cir.getReturnValue()));
    }
}
