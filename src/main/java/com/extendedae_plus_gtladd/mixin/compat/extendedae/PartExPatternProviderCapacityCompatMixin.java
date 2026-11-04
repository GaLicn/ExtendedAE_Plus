package com.extendedae_plus_gtladd.mixin.compat.extendedae;

import com.extendedae_plus_gtladd.util.ExtendedAePatternCapacityCompat;
import com.glodblock.github.extendedae.common.parts.PartExPatternProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Overrides EAEP's hard-coded four-page backing capacity with GTLCore's
 * configured value multiplied by the maximum number of expansion cards.
 *
 * <p>EAEP's own {@code createLogic} mixin applies
 * {@code Math.max(original, 4 * 36)}, which discards GTLCore's configured
 * per-page capacity. This mixin uses a lower priority than EAEP's (2000 vs
 * 3000) so it runs after that clamp and can replace the result with
 * {@code gtlcore * 4}. The usable slot count is still limited per card by
 * {@link com.extendedae_plus_gtladd.mixin.compat.extendedae.PatternProviderLogicPaginationCompatMixin}.</p>
 */
@Mixin(value = PartExPatternProvider.class, priority = 2000, remap = false)
public abstract class PartExPatternProviderCapacityCompatMixin {

    @ModifyArg(
            method = "createLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/helpers/patternprovider/PatternProviderLogic;<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V"
            ),
            index = 2
    )
    private int eap$scaleGtlcoreBackingCapacity(int original) {
        int backing = ExtendedAePatternCapacityCompat.getGtlcoreBackingPatternCapacity(
                ExtendedAePatternCapacityCompat.getGtlcoreBasePatternSlots());
        return backing > 0 ? backing : original;
    }
}