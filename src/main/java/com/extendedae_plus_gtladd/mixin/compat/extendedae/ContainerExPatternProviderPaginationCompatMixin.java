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
 * Makes the EAEP container use the number of slots actually created in the
 * menu when GTLCore is installed.
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
        if (!ExtendedAePatternCapacityCompat.isGtlcoreLoaded()
                || !(this instanceof PatternProviderMenuAccessor accessor)) {
            return;
        }

        ContainerExPatternProvider container = (ContainerExPatternProvider) (Object) this;
        int slotCount = container.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        int cardUnlockedSlots = ExtendedAePatternCapacityCompat.getCardUnlockedPatternSlots(accessor.eap$logic());
        if (cardUnlockedSlots < 0) {
            return;
        }

        if (ExtendedAePatternCapacityCompat.shouldUseGtlcoreCapacity(accessor.eap$logic())) {
            cir.setReturnValue(Math.max(1, (slotCount + 35) / 36));
        } else {
            cir.setReturnValue(Math.max(1, (Math.min(slotCount, cardUnlockedSlots) + 35) / 36));
        }
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
        if (!ExtendedAePatternCapacityCompat.isGtlcoreLoaded()
                || !(this instanceof PatternProviderMenuAccessor accessor)
                || ExtendedAePatternCapacityCompat.shouldUseGtlcoreCapacity(accessor.eap$logic())) {
            return;
        }

        int cardUnlockedSlots = ExtendedAePatternCapacityCompat.getCardUnlockedPatternSlots(accessor.eap$logic());
        if (cardUnlockedSlots < 0) {
            return;
        }

        ContainerExPatternProvider container = (ContainerExPatternProvider) (Object) this;
        int slotCount = container.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        cir.setReturnValue(Math.max(1, (Math.min(slotCount, cardUnlockedSlots) + 35) / 36));
    }

    /**
     * EAEP's page method normally applies this state. Reapply the card limit
     * after it so a legacy unlock value cannot leave GTLCore-only slots active.
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

    private void eap$applyCardPatternSlotLimit() {
        if (!ExtendedAePatternCapacityCompat.isGtlcoreLoaded()
                || !(this instanceof PatternProviderMenuAccessor accessor)
                || ExtendedAePatternCapacityCompat.shouldUseGtlcoreCapacity(accessor.eap$logic())) {
            return;
        }

        int cardUnlockedSlots = ExtendedAePatternCapacityCompat.getCardUnlockedPatternSlots(accessor.eap$logic());
        if (cardUnlockedSlots < 0) {
            return;
        }

        ContainerExPatternProvider container = (ContainerExPatternProvider) (Object) this;
        List<Slot> patternSlots = container.getSlots(SlotSemantics.ENCODED_PATTERN);
        int unlockedSlots = Math.min(patternSlots.size(), cardUnlockedSlots);
        for (int i = 0; i < patternSlots.size(); i++) {
            if (patternSlots.get(i) instanceof AppEngSlot slot) {
                boolean enabled = i < unlockedSlots;
                slot.setSlotEnabled(enabled);
                if (!enabled) {
                    slot.setActive(false);
                }
            }
        }
    }
}
