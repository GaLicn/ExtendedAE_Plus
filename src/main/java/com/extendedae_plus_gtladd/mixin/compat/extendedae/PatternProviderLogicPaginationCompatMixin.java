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
 * Exposes the GTLCore-scaled pattern capacity.
 *
 * <p>ExtendedAE Plus normally exposes only the number of pages unlocked by
 * its expansion cards. With GTLCore installed, the configured capacity is
 * the base and each expansion card adds one multiple of it, so the exposed
 * slot count becomes {@code gtlcore * (1 + cards)}.</p>
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
        int scaledSlots = ExtendedAePatternCapacityCompat.getCardUnlockedPatternSlots(this);
        if (scaledSlots < 0) {
            return;
        }

        // Ignore EAEP's legacy unlocked-page migration; the backing inventory
        // is already sized for the maximum multiple, so only the scaled limit
        // decides what is usable right now.
        cir.setReturnValue(Math.min(this.patternInventory.size(), scaledSlots));
    }
}
