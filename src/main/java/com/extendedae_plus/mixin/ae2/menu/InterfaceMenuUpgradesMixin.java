package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.InterfaceLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.ToolboxMenu;
import appeng.menu.implementations.InterfaceMenu;
import com.extendedae_plus.bridge.IUpgradableMenu;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.bridge.CompatUpgradeProvider;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为ME接口菜单添加升级槽支持，确保与AppliedFlux的兼容性
 */
@Mixin(value = InterfaceMenu.class, priority = 900, remap = false)
public abstract class InterfaceMenuUpgradesMixin extends AEBaseMenu implements IUpgradableMenu {

    @Unique
    private ToolboxMenu eap$toolbox;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/InterfaceLogicHost;)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, InterfaceLogicHost host, CallbackInfo ci) {
        this.eap$toolbox = new ToolboxMenu(this);
        
        // InterfaceMenu已经继承自UpgradeableMenu，会自动处理升级槽
        // 这里只需要记录日志，不需要重复设置升级槽
        ExtendedAELogger.LOGGER.debug("[ME接口][菜单] 升级槽已由UpgradeableMenu自动处理，当前升级槽数量: {}", 
            host.getInterfaceLogic().getUpgrades().size());
    }

    @Override
    public ToolboxMenu getToolbox() {
        return this.eap$toolbox;
    }

    public InterfaceMenuUpgradesMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }
}
