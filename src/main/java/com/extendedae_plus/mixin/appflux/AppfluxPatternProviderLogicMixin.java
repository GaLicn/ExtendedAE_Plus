package com.extendedae_plus.mixin.appflux;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.content.ae2.MirrorPatternProviderBlockEntity;
import com.extendedae_plus.util.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 当 appflux 存在时，按宿主类型扩展它的升级槽数量。
 * 对扩展样板供应器会额外补 3 个槽位。
 */
@Mixin(value = PatternProviderLogic.class, priority = 2000, remap = false)
public class AppfluxPatternProviderLogicMixin {

    /**
     * 在 appflux 初始化升级槽之后，替换为兼容层所需的槽位数量。
     */
    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$modifyAppfluxUpgradeSlots(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        try {
            if (host instanceof MirrorPatternProviderBlockEntity || !UpgradeSlotCompat.shouldListenToAppfluxUpgrades()) {
                return;
            }

            IUpgradeInventory currentUpgrades = UpgradeSlotCompat.getPatternProviderAppfluxUpgrades(this);
            if (currentUpgrades == null) {
                Logger.EAP$LOGGER.debug("未找到appflux升级槽字段，跳过升级槽兼容调整");
                return;
            }

            int targetSlots = UpgradeSlotCompat.getPatternProviderAppfluxUpgradeSlots(host);
            if (currentUpgrades.size() == targetSlots) {
                return;
            }

            IUpgradeInventory newUpgrades = UpgradeInventories.forMachine(
                    host.getTerminalIcon().getItem(),
                    targetSlots,
                    () -> {
                        try {
                            UpgradeSlotCompat.invokePatternProviderAppfluxUpgradesChanged(this);
                        } catch (Exception e) {
                            Logger.EAP$LOGGER.error("调用appflux升级变更方法失败", e);
                        }
                    }
            );

            for (int i = 0; i < Math.min(currentUpgrades.size(), newUpgrades.size()); i++) {
                if (!currentUpgrades.getStackInSlot(i).isEmpty()) {
                    newUpgrades.insertItem(i, currentUpgrades.getStackInSlot(i).copy(), false);
                }
            }

            if (!UpgradeSlotCompat.setPatternProviderAppfluxUpgrades(this, newUpgrades)) {
                Logger.EAP$LOGGER.debug("设置appflux升级槽失败，跳过升级槽兼容调整");
            }
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("AppfluxPatternProviderLogicMixin执行失败", e);
        }
    }
}
