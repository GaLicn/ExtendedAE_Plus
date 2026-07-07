package com.extendedae_plus.mixin.ae2.helpers;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import com.extendedae_plus.api.bridge.PatternProviderLogicSyncBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicSyncVersionMixin implements PatternProviderLogicSyncBridge {
    @Unique
    private long eap$patternSyncVersion;

    @Inject(method = "onChangeInventory", at = @At("TAIL"))
    private void eap$trackPatternInventoryChanges(AppEngInternalInventory inv, int slot, CallbackInfo ci) {
        this.eap$patternSyncVersion++;
    }

    @Override
    public long eap$getPatternSyncVersion() {
        return this.eap$patternSyncVersion;
    }

    @Override
    public void eap$markPatternSyncDirty() {
        this.eap$patternSyncVersion++;
    }
}
