package com.extendedae_plus.mixin.advancedae.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import com.extendedae_plus.api.PatternProviderMenuDoublingSync;
import com.extendedae_plus.api.SmartDoublingHolder;
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

@Mixin(AdvPatternProviderMenu.class)
public abstract class AdvPatternProviderMenuDoublingMixin implements PatternProviderMenuDoublingSync {
    @Final
    @Shadow(remap = false)
    protected AdvPatternProviderLogic logic;

    @Unique
    @GuiSync(23)
    public boolean eap$SmartDoubling = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncSmartDoubling(CallbackInfo ci) {
        if (!((AEBaseMenu) (Object) this).isClientSide()) {
            var l = this.logic;
            if (l instanceof SmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
            }
        }
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initSmartSync_Public(int id, Inventory playerInventory, AdvPatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof SmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initSmartSync_Protected(MenuType menuType, int id, Inventory playerInventory, AdvPatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof SmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean eap$getSmartDoublingSynced() {
        return this.eap$SmartDoubling;
    }
}
