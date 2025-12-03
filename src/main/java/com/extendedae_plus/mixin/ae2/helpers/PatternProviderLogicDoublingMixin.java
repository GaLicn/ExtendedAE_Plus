package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.ConfigManager;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = PatternProviderLogic.class, remap = false)
public class PatternProviderLogicDoublingMixin {
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow @Final private IConfigManager configManager;

    @Shadow
    public IConfigManager getConfigManager() {return null;}

    @Shadow
    public void updatePatterns() {}

    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL")
    )
    private void onInitTail(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
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

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readSmartDoublingFromNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        // TODO
        // 适配旧版本中的数据，后续版本删除
        if (tag.contains("epp_smart_doubling")) {
            this.configManager.putSetting(EAPSettings.SMART_DOUBLING,
                    tag.getBoolean("epp_smart_doubling") ? YesNo.YES : YesNo.NO);
            tag.remove("epp_smart_doubling");
        }
    }

    @Inject(method = "updatePatterns", at = @At("TAIL"))
    private void eap$applySmartDoublingToPatterns(CallbackInfo ci) {
        IConfigManager configManager = this.getConfigManager();
        boolean allowScaling = configManager.getSetting(EAPSettings.SMART_DOUBLING) == YesNo.YES;
        for (IPatternDetails details : this.patterns) {
            if (details instanceof ISmartDoublingAwarePattern aware) {
                aware.eap$setAllowScaling(allowScaling);
            }
        }
    }
}
