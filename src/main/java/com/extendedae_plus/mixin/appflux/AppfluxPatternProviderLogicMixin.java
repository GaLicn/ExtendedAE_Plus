package com.extendedae_plus.mixin.appflux;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.util.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 当appflux存在时，修改PatternProviderLogic的升级槽数量为2个
 * 优先级设置为2000，确保在appflux之后应用
 */
@Mixin(value = PatternProviderLogic.class, priority = 2000, remap = false)
public class AppfluxPatternProviderLogicMixin {

    /**
     * 在appflux初始化升级槽之后，替换为2个槽的版本
     */
    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$modifyAppfluxUpgradeSlots(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        try {
            Logger.EAP$LOGGER.info("AppfluxPatternProviderLogicMixin被调用！");
            
            // 只有当appflux存在且不启用我们的升级槽时才修改数量
            if (!UpgradeSlotCompat.shouldEnableUpgradeSlots() && UpgradeSlotCompat.shouldEnableChannelCard()) {
                Logger.EAP$LOGGER.info("尝试修改appflux升级槽数量为2个");
                
                // 使用反射找到appflux的升级槽字段并替换
                try {
                    Field upgradesField = this.getClass().getDeclaredField("af_$upgrades");
                    upgradesField.setAccessible(true);
                    IUpgradeInventory currentUpgrades = (IUpgradeInventory) upgradesField.get(this);
                    
                    if (currentUpgrades != null) {
                        Logger.EAP$LOGGER.info("找到appflux升级槽，当前大小: {}", currentUpgrades.size());
                        
                        // 创建新的2槽升级槽
                        IUpgradeInventory newUpgrades = UpgradeInventories.forMachine(
                            host.getTerminalIcon().getItem(), 
                            2, 
                            () -> {
                                try {
                                    // 调用appflux的升级变更方法
                                    this.getClass().getDeclaredMethod("af_$onUpgradesChanged").invoke(this);
                                } catch (Exception e) {
                                    Logger.EAP$LOGGER.error("调用appflux升级变更方法失败", e);
                                }
                            }
                        );
                        
                        // 复制原有升级卡到新的升级槽
                        for (int i = 0; i < Math.min(currentUpgrades.size(), newUpgrades.size()); i++) {
                            if (!currentUpgrades.getStackInSlot(i).isEmpty()) {
                                newUpgrades.insertItem(i, currentUpgrades.getStackInSlot(i).copy(), false);
                            }
                        }
                        
                        // 替换升级槽
                        upgradesField.set(this, newUpgrades);
                        Logger.EAP$LOGGER.info("成功将appflux升级槽替换为2个槽");
                    }
                } catch (Exception e) {
                    Logger.EAP$LOGGER.error("反射修改appflux升级槽失败", e);
                }
            }
        } catch (Exception e) {
            Logger.EAP$LOGGER.error("AppfluxPatternProviderLogicMixin执行失败", e);
        }
    }
}
