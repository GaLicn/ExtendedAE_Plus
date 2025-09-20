package com.extendedae_plus.mixin.advancedae.helpers;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.api.SmartDoublingAwarePattern;
import com.extendedae_plus.api.SmartDoublingHolder;
import com.extendedae_plus.mixin.advancedae.accessor.AdvPatternProviderLogicPatternsAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public class AdvPatternProviderLogicDoublingMixin implements SmartDoublingHolder {
    @Unique
    private static final String EAP_SMART_DOUBLING_KEY = "eap_smart_doubling";

    @Unique
    private boolean eap$smartDoubling = false;

    @Override
    public boolean eap$getSmartDoubling() {
        return eap$smartDoubling;
    }

    @Override
    public void eap$setSmartDoubling(boolean value) {
        this.eap$smartDoubling = value;
        // 立即将开关状态应用到当前 Provider 的样板上，避免等待下一次 updatePatterns
        try {
            var list = ((AdvPatternProviderLogicPatternsAccessor) this).eap$patterns();
            for (IPatternDetails details : list) {
                if (details instanceof AEProcessingPattern proc && proc instanceof SmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(value);
                }
            }
            // 触发一次刷新，让网络及时拿到最新状态（也会触发 ICraftingProvider.requestUpdate(mainNode)）
            ((AdvPatternProviderLogic) (Object) this).updatePatterns();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$writeSmartDoublingToNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putBoolean(EAP_SMART_DOUBLING_KEY, this.eap$smartDoubling);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readSmartDoublingFromNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (tag.contains(EAP_SMART_DOUBLING_KEY)) {
            this.eap$smartDoubling = tag.getBoolean(EAP_SMART_DOUBLING_KEY);
        }
    }

    @Inject(method = "updatePatterns", at = @At("TAIL"))
    private void eap$applySmartDoublingToPatterns(CallbackInfo ci) {
        try {
            var list = ((AdvPatternProviderLogicPatternsAccessor) this).eap$patterns();
            boolean allow = this.eap$smartDoubling;
            for (IPatternDetails details : list) {
                if (details instanceof AEProcessingPattern proc && proc instanceof SmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(allow);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Shadow
    public void saveChanges() {}
}
