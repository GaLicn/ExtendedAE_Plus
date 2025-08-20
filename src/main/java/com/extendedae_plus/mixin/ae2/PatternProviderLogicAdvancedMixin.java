package com.extendedae_plus.mixin.ae2;

import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extendedae_plus.api.AdvancedBlockingHolder;

@Mixin(PatternProviderLogic.class)
public class PatternProviderLogicAdvancedMixin implements AdvancedBlockingHolder {
    @Unique
    private static final String EPP_ADV_BLOCKING_KEY = "epp_advanced_blocking";

    @Unique
    private boolean epp$advancedBlocking = false;

    @Override
    public boolean ext$getAdvancedBlocking() {
        return epp$advancedBlocking;
    }

    @Override
    public void ext$setAdvancedBlocking(boolean value) {
        this.epp$advancedBlocking = value;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
    private void epp$writeAdvancedToNbt(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(EPP_ADV_BLOCKING_KEY, this.epp$advancedBlocking);
        System.out.println("[EPP][NBT] writeToNBT: " + EPP_ADV_BLOCKING_KEY + "=" + this.epp$advancedBlocking);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void epp$readAdvancedFromNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(EPP_ADV_BLOCKING_KEY)) {
            this.epp$advancedBlocking = tag.getBoolean(EPP_ADV_BLOCKING_KEY);
            System.out.println("[EPP][NBT] readFromNBT: " + EPP_ADV_BLOCKING_KEY + "=" + this.epp$advancedBlocking);
        } else {
            System.out.println("[EPP][NBT] readFromNBT: key missing, default=" + this.epp$advancedBlocking);
        }
    }
}
