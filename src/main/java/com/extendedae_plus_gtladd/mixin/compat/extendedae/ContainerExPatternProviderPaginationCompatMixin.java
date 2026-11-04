package com.extendedae_plus_gtladd.mixin.compat.extendedae;

import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.bawnorton.mixinsquared.TargetHandler;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAccessor;
import com.extendedae_plus_gtladd.util.ExtendedAePatternCapacityCompat;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Limits the EAEP container to the GTLCore-scaled slot count.
 *
 * <p>The backing inventory holds the largest multiple so cards can be added
 * or removed at any time; the usable slot count is
 * {@code gtlcore * (1 + cards)}.</p>
 */
@Mixin(value = ContainerExPatternProvider.class, priority = 4000, remap = false)
public abstract class ContainerExPatternProviderPaginationCompatMixin {

    @TargetHandler(
            mixin = "com.extendedae_plus.mixin.extendedae.container.ContainerExPatternProviderMixin",
            name = "eap$getDynamicPageCount"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void eap$preferGtlcoreMenuPageCount(CallbackInfoReturnable<Integer> cir) {
        int usableSlots = eap$getUsablePatternSlots(this);
        if (usableSlots < 0) {
            return;
        }

        cir.setReturnValue(Math.max(1, (usableSlots + 35) / 36));
    }

    @TargetHandler(
            mixin = "com.extendedae_plus.mixin.extendedae.container.ContainerExPatternProviderMixin",
            name = "eap$getAvailablePageCount"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void eap$limitEaepPageCount(CallbackInfoReturnable<Integer> cir) {
        int usableSlots = eap$getUsablePatternSlots(this);
        if (usableSlots < 0) {
            return;
        }

        cir.setReturnValue(Math.max(1, (usableSlots + 35) / 36));
    }

    /**
     * EAEP's page method normally applies this state. Reapply the card limit
     * after it so a legacy unlock value cannot leave extra slots active.
     */
    @TargetHandler(
            mixin = "com.extendedae_plus.mixin.extendedae.container.ContainerExPatternProviderMixin",
            name = "eap$showPage"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private void eap$enforceCardPatternSlotLimit(CallbackInfo ci) {
        eap$applyCardPatternSlotLimit();
    }

    /**
     * Keep the server-side slot state correct even when another pagination
     * implementation causes the page method to be refreshed in a different
     * order.
     */
    @Inject(method = "broadcastChanges", at = @At("TAIL"), remap = false, require = 0)
    private void eap$enforceCardPatternSlotLimitAfterBroadcast(CallbackInfo ci) {
        eap$applyCardPatternSlotLimit();
    }

    private static int eap$getUsablePatternSlots(Object container) {
        if (!(container instanceof PatternProviderMenuAccessor accessor)) {
            return -1;
        }

        int unlockedSlots = ExtendedAePatternCapacityCompat.getCardUnlockedPatternSlots(accessor.eap$logic());
        if (unlockedSlots < 0) {
            return -1;
        }

        ContainerExPatternProvider self = (ContainerExPatternProvider) container;
        return Math.min(self.getSlots(SlotSemantics.ENCODED_PATTERN).size(), unlockedSlots);
    }

    private void eap$applyCardPatternSlotLimit() {
        int unlockedSlots = eap$getUsablePatternSlots(this);
        if (unlockedSlots < 0) {
            return;
        }

        ContainerExPatternProvider container = (ContainerExPatternProvider) (Object) this;
        List<Slot> patternSlots = container.getSlots(SlotSemantics.ENCODED_PATTERN);
        int usableSlots = Math.min(patternSlots.size(), unlockedSlots);
        for (int i = 0; i < patternSlots.size(); i++) {
            if (patternSlots.get(i) instanceof AppEngSlot slot) {
                boolean enabled = i < usableSlots;
                slot.setSlotEnabled(enabled);
                if (!enabled) {
                    slot.setActive(false);
                }
            }
        }
    }
}
