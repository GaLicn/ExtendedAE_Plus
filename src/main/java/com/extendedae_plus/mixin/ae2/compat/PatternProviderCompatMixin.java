package com.extendedae_plus.mixin.ae2.compat;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.compat.DynamicSizeInternalInventory;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * PatternProviderMenu的兼容性Mixin
 * 优先级设置为500，低于appflux的默认优先级，避免冲突
 */
@Mixin(value = PatternProviderMenu.class, priority = 500, remap = false)
public abstract class PatternProviderCompatMixin extends AEBaseMenu {

    public PatternProviderCompatMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Redirect(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At(value = "INVOKE", target = "Lappeng/helpers/patternprovider/PatternProviderLogic;getPatternInv()Lappeng/api/inventories/InternalInventory;"))
    private InternalInventory eap$useFixedExtendedProviderMenuInventory(PatternProviderLogic logic) {
        InternalInventory inventory = logic.getPatternInv();
        if ((Object) this instanceof ContainerExPatternProvider
                && inventory instanceof DynamicSizeInternalInventory dynamicInventory) {
            return dynamicInventory.getBackingInventory();
        }
        return inventory;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void eap$initCompatUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
                if (host.getLogic() instanceof IUpgradeableObject upgradeableLogic) {
                    IUpgradeInventory upgrades = upgradeableLogic.getUpgrades();
                    this.setupUpgrades(upgrades);
                }
            }
        } catch (Exception e) {
            // 静默处理异常，确保不会因为升级功能导致崩溃
            EAP$LOGGER.error("PatternProviderMenu兼容性升级初始化失败", e);
        }
    }
}
