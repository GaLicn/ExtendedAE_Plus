package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.PatternProviderMenuDoublingSync;
import com.extendedae_plus.api.SmartDoublingHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuDoublingMixin implements PatternProviderMenuDoublingSync {
    @Shadow
    protected PatternProviderLogic logic;

    @Unique
    @GuiSync(21)
    public boolean eap$SmartDoubling = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncSmartDoubling(CallbackInfo ci) {
        if (!((AEBaseMenu) (Object) this).isClientSide()) {
            var l = this.logic;
            if (l instanceof SmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
                LOGGER.debug("[EAP] Menu broadcastChanges HEAD: eap$SmartDoubling={}", this.eap$SmartDoubling);
            }
        }
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"))
    private void eap$initSmartSync_Public(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof SmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
            }
        } catch (Throwable t) {
            LOGGER.error("Error initializing smart doubling sync", t);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"))
    private void eap$initSmartSync_Protected(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof SmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
            }
        } catch (Throwable t) {
            LOGGER.error("Error initializing smart doubling sync", t);
        }
    }

    @Override
    public boolean eap$getSmartDoublingSynced() {
        return this.eap$SmartDoubling;
    }
}
