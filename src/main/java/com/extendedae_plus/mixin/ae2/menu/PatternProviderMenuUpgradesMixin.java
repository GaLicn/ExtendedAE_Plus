package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.bridge.IUpgradableMenu;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderMenu.class, priority = 2000, remap = false)
public abstract class PatternProviderMenuUpgradesMixin extends AEBaseMenu implements IUpgradableMenu {
    @Final
    @Shadow protected PatternProviderLogic logic;

    @Unique
    private ToolboxMenu eap$toolbox;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        // 只有在应该启用升级卡槽时才初始化
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }
        
        this.eap$toolbox = new ToolboxMenu(this);
        this.setupUpgrades(((IUpgradeableObject) host).getUpgrades());
    }

    @Override
    public ToolboxMenu getToolbox() {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return null;
        }
        return this.eap$toolbox;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return appeng.api.upgrades.UpgradeInventories.empty();
        }
        return ((IUpgradeableObject) this.logic).getUpgrades();
    }

    public PatternProviderMenuUpgradesMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }
}
