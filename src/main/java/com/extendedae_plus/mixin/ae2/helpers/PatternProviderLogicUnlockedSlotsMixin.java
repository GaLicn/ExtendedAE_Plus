package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicPatternInputsAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicPatternsAccessor;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicUnlockedSlotsMixin {
    @Final
    @Shadow
    private PatternProviderLogicHost host;

    @Inject(method = "updatePatterns", at = @At("TAIL"))
    private void eap$limitPatternsToUnlockedPages(CallbackInfo ci) {
        if (!((Object) this instanceof PatternProviderPageUnlockBridge bridge)
                || !bridge.eap$isExtendedPatternProviderHost()) {
            return;
        }

        var self = (PatternProviderLogic) (Object) this;
        var patternInventory = self.getPatternInv();
        var blockEntity = this.host.getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        int unlockedSlots = Math.max(0, Math.min(patternInventory.size(), bridge.eap$getUnlockedPatternSlots()));
        var patterns = ((PatternProviderLogicPatternsAccessor) (Object) this).eap$patterns();
        var patternInputs = ((PatternProviderLogicPatternInputsAccessor) (Object) this).eap$patternInputs();

        patterns.clear();
        patternInputs.clear();

        for (int i = 0; i < unlockedSlots; i++) {
            var details = PatternDetailsHelper.decodePattern(patternInventory.getStackInSlot(i), level);
            if (details == null) {
                continue;
            }

            patterns.add(details);
            for (var input : details.getInputs()) {
                for (GenericStack candidate : input.getPossibleInputs()) {
                    patternInputs.add(candidate.what().dropSecondary());
                }
            }
        }

        if ((Object) this instanceof ISmartDoublingHolder holder) {
            boolean allowScaling = self.getConfigManager().getSetting(EAPSettings.SMART_DOUBLING) == YesNo.YES;
            int limit = holder.eap$getProviderSmartDoublingLimit();
            for (var details : patterns) {
                if (details instanceof ISmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(allowScaling);
                    aware.eap$setMultiplierLimit(PatternScaler.getComputedMul(details, limit));
                }
            }
        }
    }
}
