package com.extendedae_plus.mixin.advancedae.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.ToolboxMenu;
import com.extendedae_plus.api.bridge.CompatUpgradeProvider;
import com.extendedae_plus.api.bridge.IUpgradableMenu;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 未安装 Applied Flux 时为 AdvancedAE 供应器菜单添加本地升级卡槽。 */
@Mixin(value = AdvPatternProviderMenu.class, remap = false)
public abstract class AdvPatternProviderMenuUpgradesMixin extends AEBaseMenu implements IUpgradableMenu {
    @Shadow @Final protected AdvPatternProviderLogic logic;
    @Unique private ToolboxMenu eap$toolbox;

    protected AdvPatternProviderMenuUpgradesMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void eap$setupChannelCardUpgrades(
            MenuType<?> menuType, int id, Inventory playerInventory, AdvPatternProviderLogicHost host, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.eap$toolbox = new ToolboxMenu(this);
            this.setupUpgrades(((CompatUpgradeProvider) this.logic).eap$getCompatUpgrades());
        }
    }

    @Override
    public ToolboxMenu eap$getToolbox() {
        return this.eap$toolbox;
    }
}
