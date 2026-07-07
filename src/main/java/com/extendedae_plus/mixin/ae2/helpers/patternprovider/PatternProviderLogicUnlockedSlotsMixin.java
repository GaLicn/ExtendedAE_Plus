package com.extendedae_plus.mixin.ae2.helpers.patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicPatternInputsAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.smartDoubling.PatternScaler.getComputedMul;

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

        PatternProviderLogic self = (PatternProviderLogic) (Object) this;
        var patternInventory = self.getPatternInv();
        var blockEntity = this.host.getBlockEntity();
        if (patternInventory == null || blockEntity == null || blockEntity.getLevel() == null) {
            return;
        }

        int unlockedSlots = Math.max(0, Math.min(patternInventory.size(), bridge.eap$getUnlockedPatternSlots()));
        var patterns = ((PatternProviderLogicAccessor) this).eap$patterns();
        var patternInputs = ((PatternProviderLogicPatternInputsAccessor) this).eap$patternInputs();

        patterns.clear();
        patternInputs.clear();

        for (int i = 0; i < unlockedSlots; i++) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(
                    patternInventory.getStackInSlot(i),
                    blockEntity.getLevel());
            if (details == null) {
                continue;
            }

            patterns.add(details);
            for (var input : details.getInputs()) {
                for (GenericStack candidate : input.getPossibleInputs()) {
                    AEKey key = candidate.what().dropSecondary();
                    patternInputs.add(key);
                }
            }
        }

        if ((Object) this instanceof ISmartDoublingHolder holder) {
            boolean allowScaling = holder.eap$getSmartDoubling();
            int limit = holder.eap$getProviderSmartDoublingLimit();
            for (IPatternDetails details : patterns) {
                if (details instanceof AEProcessingPattern proc && proc instanceof ISmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(allowScaling);
                    aware.eap$setMultiplierLimit(getComputedMul(proc, limit));
                }
            }
        }
    }
}
