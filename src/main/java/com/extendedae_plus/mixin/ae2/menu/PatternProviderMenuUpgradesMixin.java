package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.bridge.CompatUpgradeProvider;
import com.extendedae_plus.api.bridge.PatternProviderLogicAppfluxBridge;
import com.extendedae_plus.compat.DynamicSizeInternalInventory;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderMenu.class, priority = 2000, remap = false)
public abstract class PatternProviderMenuUpgradesMixin extends AEBaseMenu {
    @Final
    @Shadow protected PatternProviderLogic logic;

    public PatternProviderMenuUpgradesMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
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
            at = @At(value = "INVOKE", target = "Lappeng/menu/implementations/PatternProviderMenu;createPlayerInventorySlots(Lnet/minecraft/world/entity/player/Inventory;)V"),
            remap = false)
    private void eap$ensureAppfluxUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldListenToAppfluxUpgrades()
                && (Object) this.logic instanceof PatternProviderLogicAppfluxBridge bridge) {
            bridge.eap$ensureAppfluxUpgradeSlots();
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        if (UpgradeSlotCompat.shouldManageLocalUpgradeInventory()) {
            this.setupUpgrades(((CompatUpgradeProvider) this.logic).eap$getCompatUpgrades());
        }
    }
}
