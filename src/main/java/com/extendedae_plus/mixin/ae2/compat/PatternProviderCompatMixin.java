package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.util.Logger;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PatternProviderMenu的兼容性Mixin
 * 优先级设置为500，低于appflux的默认优先级，避免冲突
 */
@Mixin(value = PatternProviderMenu.class, priority = 500, remap = false)
public abstract class PatternProviderCompatMixin extends AEBaseMenu implements UpgradeSlotCompat.IUpgradeableMenuCompat {
    
    @Unique
    private ToolboxMenu eap$compatToolbox;
    
    @Unique
    private IUpgradeInventory eap$compatUpgrades;
    
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void eap$initCompatUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            // 检测是否应该启用升级卡槽功能
            if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
                // 直接初始化升级功能
                this.eap$compatToolbox = new ToolboxMenu(this);
                
                if (host instanceof appeng.api.upgrades.IUpgradeableObject upgradeableHost) {
                    this.eap$compatUpgrades = upgradeableHost.getUpgrades();
                    this.setupUpgrades(this.eap$compatUpgrades);
                }
            }
        } catch (Exception e) {
            // 静默处理异常，确保不会因为升级功能导致崩溃
            Logger.EAP$LOGGER.error("PatternProviderMenu兼容性升级初始化失败", e);
        }
    }
    
    @Override
    public ToolboxMenu getCompatToolbox() {
        return this.eap$compatToolbox;
    }
    
    @Override
    public void setCompatToolbox(ToolboxMenu toolbox) {
        this.eap$compatToolbox = toolbox;
    }
    
    @Override
    public IUpgradeInventory getCompatUpgrades() {
        return this.eap$compatUpgrades;
    }
    
    // 构造函数，Mixin要求
    public PatternProviderCompatMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }
}
