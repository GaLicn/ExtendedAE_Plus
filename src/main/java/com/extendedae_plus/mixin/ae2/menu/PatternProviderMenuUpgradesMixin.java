package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.bridge.IUpgradableMenu;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.bridge.CompatUpgradeProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.extendedae_plus.util.ExtendedAELogger;

@Mixin(value = PatternProviderMenu.class, priority = 2000, remap = false)
public abstract class PatternProviderMenuUpgradesMixin extends AEBaseMenu implements IUpgradableMenu {
    @Final
    @Shadow protected PatternProviderLogic logic;

    @Unique
    private ToolboxMenu eap$toolbox;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        this.eap$toolbox = new ToolboxMenu(this);
        
        // 现在 PatternProviderLogic 始终实现 IUpgradeableObject（通过我们的 mixin）
        if (this.logic instanceof IUpgradeableObject upgradeableLogic) {
            IUpgradeInventory upgrades = upgradeableLogic.getUpgrades();
            if (upgrades != null && upgrades != appeng.api.upgrades.UpgradeInventories.empty()) {
                ExtendedAELogger.LOGGER.debug("[样板供应器][菜单] 设置升级槽 UI，槽位数: {}", upgrades.size());
                this.setupUpgrades(upgrades);
            } else {
                ExtendedAELogger.LOGGER.debug("[样板供应器][菜单] 升级槽为空或未初始化");
            }
        } else if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            // 备用方案：使用 compat 升级槽
            ExtendedAELogger.LOGGER.debug("[样板供应器][菜单] 备用方案：使用 compat 升级槽");
            this.setupUpgrades(((CompatUpgradeProvider) this.logic).eap$getCompatUpgrades());
        }
    }

    @Override
    public ToolboxMenu getToolbox() {
        return this.eap$toolbox;
    }

    public PatternProviderMenuUpgradesMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }
}
