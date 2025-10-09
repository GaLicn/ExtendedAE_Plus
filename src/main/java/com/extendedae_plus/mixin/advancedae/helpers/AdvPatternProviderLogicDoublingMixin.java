package com.extendedae_plus.mixin.advancedae.helpers;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.mixin.advancedae.accessor.AdvPatternProviderLogicPatternsAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public class AdvPatternProviderLogicDoublingMixin implements ISmartDoublingHolder {
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
        // 立即将开关状态应用到当前 Provider 的样板上，避免等待下一次 updatePatterns
        try {
            var list = ((AdvPatternProviderLogicPatternsAccessor) this).eap$patterns();
            for (IPatternDetails details : list) {
                if (details instanceof AEProcessingPattern proc && proc instanceof ISmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(value);
                }
            }
            // 触发一次刷新，让网络及时拿到最新状态（也会触发 ICraftingProvider.requestUpdate(mainNode)）
            ((AdvPatternProviderLogic) (Object) this).updatePatterns();
        } catch (Throwable ignored) {}
    }

    @Override
    public int eap$getProviderSmartDoublingLimit() {
        return this.eap$providerScalingLimit;
    }

    @Override
    public void eap$setProviderSmartDoublingLimit(int limit) {
        // 更新供应器级别上限并去抖保存
        var list = ((AdvPatternProviderLogicPatternsAccessor) this).eap$patterns();
        this.eap$providerScalingLimit = Math.max(0, limit);
        // 现在把防抖移到客户端屏幕端来处理，服务器端直接立即应用并保存
        for (IPatternDetails d : list) {
            if (d instanceof AEProcessingPattern proc && proc instanceof ISmartDoublingAwarePattern a) {
                try { a.eap$setScalingLimit(this.eap$providerScalingLimit); } catch (Throwable ignored) {}
            }
        }
        try {
            ((AdvPatternProviderLogic) (Object) this).updatePatterns();
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
            var list = ((AdvPatternProviderLogicPatternsAccessor) this).eap$patterns();
            boolean allow = this.eap$smartDoubling;
            int limit = this.eap$providerScalingLimit;
            for (IPatternDetails details : list) {
                if (details instanceof AEProcessingPattern proc && proc instanceof ISmartDoublingAwarePattern aware) {
                    aware.eap$setAllowScaling(allow);
                    aware.eap$setScalingLimit(limit);
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
