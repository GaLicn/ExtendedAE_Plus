package com.extendedae_plus.mixin.ae2;

import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.api.SmartDoublingHolder;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternProviderLogic.class)
public class PatternProviderLogicDoublingMixin implements SmartDoublingHolder {
    @Unique
    private static final String EPP_SMART_DOUBLING_KEY = "epp_smart_doubling";

    @Unique
    private boolean eap$smartDoubling = false;

    @Override
    public boolean eap$getSmartDoubling() {
        return eap$smartDoubling;
    }

    @Override
    public void eap$setSmartDoubling(boolean value) {
        this.eap$smartDoubling = value;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void eap$writeSmartDoublingToNbt(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(EPP_SMART_DOUBLING_KEY, this.eap$smartDoubling);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void eap$readSmartDoublingFromNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(EPP_SMART_DOUBLING_KEY)) {
            this.eap$smartDoubling = tag.getBoolean(EPP_SMART_DOUBLING_KEY);
        }
    }
}
