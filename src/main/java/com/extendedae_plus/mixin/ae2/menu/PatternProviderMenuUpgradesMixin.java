package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.bridge.CompatUpgradeProvider;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderMenu.class, priority = 2000, remap = false)
public abstract class PatternProviderMenuUpgradesMixin extends AEBaseMenu  {
    @Final
    @Shadow protected PatternProviderLogic logic;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        // 当未安装 AppliedFlux 时，我们负责注入升级槽；安装了 AF 则由 AF 的菜单 Mixin 负责，避免重复渲染
        if (UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            this.setupUpgrades(((CompatUpgradeProvider) this.logic).eap$getCompatUpgrades());
        }
    }

    public PatternProviderMenuUpgradesMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }
}
