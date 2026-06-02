package com.extendedae_plus.mixin.advancedae.helpers;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.IConfigManager;
import appeng.util.ConfigManager;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import com.extendedae_plus.util.smartDoubling.PatternScaler;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public class AdvPatternProviderLogicDoublingMixin implements ISmartDoublingHolder {
    @Unique private static final String EAP_PROVIDER_SCALING_LIMIT = "eap_provider_scaling_limit";
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow @Final private IConfigManager configManager;
    @Unique private int eap$providerScalingLimit = 0; // 供应器级别的上限，0 表示不限制

    @Shadow
    public IConfigManager getConfigManager() {return null;}

    @Shadow
    public void updatePatterns() {}

    @Override
    public int eap$getProviderSmartDoublingLimit() {
        return this.eap$providerScalingLimit;
    }

    @Override
    public void eap$setProviderSmartDoublingLimit(int limit) {
        this.eap$providerScalingLimit = limit;
        this.updatePatterns();
    }

    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;I)V",
            at = @At("TAIL")
    )
    private void onInitTail(IManagedGridNode mainNode, AdvPatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        // 直接往构建后的 configManager 里加 setting
        ConfigManager configManager = (ConfigManager) this.getConfigManager();
        configManager.registerSetting(EAPSettings.SMART_DOUBLING, YesNo.NO);
    }

    @Inject(method = "configChanged", at = @At("HEAD"))
    private void eap$onConfigChanged(IConfigManager manager, Setting<?> setting, CallbackInfo ci) {
        if (setting == EAPSettings.SMART_DOUBLING) {
            this.updatePatterns();
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$writeSmartDoublingToNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        // 保存供应器级别上限
        tag.putInt(EAP_PROVIDER_SCALING_LIMIT, this.eap$providerScalingLimit);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readSmartDoublingFromNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        // TODO
        // 适配旧版本中的数据，后续版本删除
        if (tag.contains("eap_smart_doubling")) {
            this.configManager.putSetting(EAPSettings.SMART_DOUBLING,
                                          tag.getBoolean("eap_smart_doubling") ? YesNo.YES : YesNo.NO
            );
            tag.remove("eap_smart_doubling");
        }

        if (tag.contains(EAP_PROVIDER_SCALING_LIMIT)) {
            this.eap$providerScalingLimit = tag.getInt(EAP_PROVIDER_SCALING_LIMIT);
        }
    }

    @Inject(method = "updatePatterns", at = @At("TAIL"))
    private void eap$applySmartDoublingToPatterns(CallbackInfo ci) {
        IConfigManager configManager = this.getConfigManager();
        boolean allowScaling = configManager.getSetting(EAPSettings.SMART_DOUBLING) == YesNo.YES;
        int limit = this.eap$providerScalingLimit;
        for (IPatternDetails details : this.patterns) {
            if (details instanceof ISmartDoublingAwarePattern aware) {
                aware.eap$setAllowScaling(allowScaling);
                aware.eap$setMultiplierLimit(PatternScaler.getComputedMul(details, limit));
            }
        }
    }
}
