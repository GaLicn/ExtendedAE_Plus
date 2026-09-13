package com.extendedae_plus_gtladd.mixin.compat.extendedae;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import com.bawnorton.mixinsquared.TargetHandler;
import com.extendedae_plus_gtladd.util.ExtendedAePatternCapacityCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets GTLCore decide the physical pattern capacity.
 *
 * <p>ExtendedAE Plus normally exposes only the number of pages unlocked by
 * its expansion cards. GTLCore's backing size becomes authoritative only
 * after all EAEP expansion cards are installed.</p>
 */
@Mixin(value = PatternProviderLogic.class, priority = 4000, remap = false)
public abstract class PatternProviderLogicPaginationCompatMixin {

    @Shadow
    @Final
    private AppEngInternalInventory patternInventory;

    @TargetHandler(
            mixin = "com.extendedae_plus.mixin.ae2.compat.PatternProviderLogicCompatMixin",
            name = "eap$getExposedPatternSlots"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void eap$preferGtlcorePatternCapacity(CallbackInfoReturnable<Integer> cir) {
        if (!ExtendedAePatternCapacityCompat.isGtlcoreLoaded()) {
            return;
        }

        int cardUnlockedSlots = ExtendedAePatternCapacityCompat.getCardUnlockedPatternSlots(this);
        if (cardUnlockedSlots < 0) {
            return;
        }

        if (ExtendedAePatternCapacityCompat.shouldUseGtlcoreCapacity(this)) {
            cir.setReturnValue(Math.max(0, this.patternInventory.size()));
        } else {
            // Ignore EAEP's legacy unlocked-page migration while GTLCore is
            // installed. The current card inventory is the hard limit until
            // all EAEP expansion cards are present.
            cir.setReturnValue(Math.min(this.patternInventory.size(), cardUnlockedSlots));
        }
    }
}
