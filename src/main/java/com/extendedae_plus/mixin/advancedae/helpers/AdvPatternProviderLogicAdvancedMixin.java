package com.extendedae_plus.mixin.advancedae.helpers;

import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.util.ConfigManager;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.util.pattern.SmartBlockingMatcher;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public class AdvPatternProviderLogicAdvancedMixin {
    @Shadow @Final private IConfigManager configManager;

    @Shadow public IConfigManager getConfigManager() {throw new AssertionError();}

    @Shadow public boolean isBlocking() {throw new AssertionError();}

    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;I)V",
            at = @At("TAIL")
    )
    private void onInitTail(IManagedGridNode mainNode, AdvPatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        // 直接往构建后的 configManager 里加 setting
        ConfigManager configManager = (ConfigManager) this.getConfigManager();
        configManager.registerSetting(EAPSettings.ADVANCED_BLOCKING, YesNo.NO);
    }

    // 在 pushPattern 中，修改对 adapter.containsPatternInput(...) 的调用
    @WrapOperation(
            method = "pushPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/helpers/patternprovider/PatternProviderTarget;containsPatternInput(Ljava/util/Set;)Z"
            )
    )
    private boolean eap$redirectBlockingContains(PatternProviderTarget adapter,
                                                 Set<AEKey> patternInputs,
                                                 Operation<Boolean> original,
                                                 IPatternDetails patternDetails,
                                                 KeyCounter[] inputHolder) {
        // 原版是否打开阻挡
        if (!this.isBlocking()) {
            return original.call(adapter, patternInputs);
        }

        // 仅当高级阻挡启用时启用“匹配则不阻挡”
        if (this.configManager.getSetting(EAPSettings.ADVANCED_BLOCKING) == YesNo.YES) {
            if (this.eap$targetFullyMatchesPatternInputs(adapter, patternDetails)) {
                // 返回 false 表示“不包含阻挡关键物”，从而不触发 continue，允许发配
                return false;
            }
        }
        // 否则使用原判定
        return original.call(adapter, patternInputs);
    }

    @Unique
    private boolean eap$targetFullyMatchesPatternInputs(PatternProviderTarget adapter, IPatternDetails patternDetails) {
        // 只遍历一次目标库存，避免每个输入槽、每个候选都重复扫描导致的卡顿
        return SmartBlockingMatcher.matchesAllInputs(adapter, patternDetails);
    }

    @Inject(method = "configChanged", at = @At("HEAD"))
    private void eap$onConfigChanged(IConfigManager manager, Setting<?> setting, CallbackInfo ci) {
        // 开启智能阻挡联动开启原版阻挡
        if (setting == EAPSettings.ADVANCED_BLOCKING && manager.getSetting(EAPSettings.ADVANCED_BLOCKING) == YesNo.YES) {
            manager.putSetting(Settings.BLOCKING_MODE, YesNo.YES);
        }
        // 关闭原版阻挡联动关闭智能阻挡
        if (setting == Settings.BLOCKING_MODE && manager.getSetting(Settings.BLOCKING_MODE) == YesNo.NO) {
            manager.putSetting(EAPSettings.ADVANCED_BLOCKING, YesNo.NO);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$readSmartDoublingFromNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        // TODO
        // 适配旧版本中的数据，后续版本删除
        if (tag.contains("eap_advanced_blocking")) {
            this.configManager.putSetting(EAPSettings.ADVANCED_BLOCKING,
                    tag.getBoolean("eap_advanced_blocking") ? YesNo.YES : YesNo.NO);
            tag.remove("eap_advanced_blocking");
        }
    }
}
