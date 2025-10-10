package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.smartDoubling.PatternScaler.getComputedMul;

@Mixin(value = PatternProviderLogic.class, remap = false)
public class PatternProviderLogicDoublingMixin implements ISmartDoublingHolder {
    @Unique private static final String EAP_SMART_DOUBLING_KEY = "eap_smart_doubling";
    @Unique private static final String EAP_PROVIDER_SCALING_LIMIT = "eap_provider_scaling_limit";

    @Unique private boolean eap$smartDoubling = false;
    @Unique private int eap$providerScalingLimit = 0; // 供应器级别的上限，0 表示不限制

    @Override
    public boolean eap$getSmartDoubling() {
        return eap$smartDoubling;
    }

    @Override
    public void eap$setSmartDoubling(boolean value) {
        this.eap$smartDoubling = value;
        // 触发一次刷新
        try {
            ((PatternProviderLogic) (Object) this).updatePatterns();
        } catch (Throwable ignored) {}
    }

    @Override
    public int eap$getProviderSmartDoublingLimit() {
        return this.eap$providerScalingLimit;
    }

    @Override
    public void eap$setProviderSmartDoublingLimit(int limit) {
        this.eap$providerScalingLimit = limit;
        try {
            ((PatternProviderLogic) (Object) this).updatePatterns();
        } catch (Throwable ignored) {}
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$writeSmartDoublingToNbt(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(EAP_SMART_DOUBLING_KEY, this.eap$smartDoubling);
        // 保存供应器级别上限
        tag.putInt(EAP_PROVIDER_SCALING_LIMIT, this.eap$providerScalingLimit);

    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readSmartDoublingFromNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(EAP_SMART_DOUBLING_KEY)) {
            this.eap$smartDoubling = tag.getBoolean(EAP_SMART_DOUBLING_KEY);
        }
        if (tag.contains(EAP_PROVIDER_SCALING_LIMIT)) {
            this.eap$providerScalingLimit = tag.getInt(EAP_PROVIDER_SCALING_LIMIT);
        }
    }

    @Inject(method = "updatePatterns", at = @At("TAIL"))
    private void eap$applySmartDoublingToPatterns(CallbackInfo ci) {
        try {
            var list = ((PatternProviderLogicAccessor) this).eap$patterns();
            boolean allow = this.eap$smartDoubling;
            int limit = this.eap$providerScalingLimit;
            for (IPatternDetails details : list) {
                if (details instanceof AEProcessingPattern proc && proc instanceof ISmartDoublingAwarePattern pattern) {
                    pattern.eap$setAllowScaling(allow);
                    pattern.eap$setMultiplierLimit(getComputedMul(proc, limit));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Shadow
    public void saveChanges() {}

    @Inject(method = "exportSettings(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void onExportSettings(CompoundTag output, CallbackInfo ci) {
        output.putBoolean(EAP_SMART_DOUBLING_KEY, this.eap$smartDoubling);
        output.putInt(EAP_PROVIDER_SCALING_LIMIT, this.eap$providerScalingLimit);
    }

    @Inject(method = "importSettings(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
    private void onImportSettings(CompoundTag input, Player player, CallbackInfo ci) {
        if (input.contains(EAP_SMART_DOUBLING_KEY)) {
            this.eap$smartDoubling = input.getBoolean(EAP_SMART_DOUBLING_KEY);
            // 持久化到 world
            this.saveChanges();
        }
        if (input.contains(EAP_PROVIDER_SCALING_LIMIT)) {
            this.eap$providerScalingLimit = input.getInt(EAP_PROVIDER_SCALING_LIMIT);
            this.saveChanges();
        }
    }
}
