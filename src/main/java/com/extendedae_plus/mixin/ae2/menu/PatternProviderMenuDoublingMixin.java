package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.smartDoubling.IPatternProviderMenuDoublingSync;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuDoublingMixin implements IPatternProviderMenuDoublingSync {
    @Final
    @Shadow(remap = false)
    protected PatternProviderLogic logic;

    @Unique
    @GuiSync(21)
    public boolean eap$SmartDoubling = false;
    @Unique
    @GuiSync(22)
    public int eap$PerProviderScalingLimit = 0; // 0 = no limit

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncSmartDoubling(CallbackInfo ci) {
        if (!((AEBaseMenu) (Object) this).isClientSide()) {
            var l = this.logic;
            if (l instanceof ISmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
                this.eap$PerProviderScalingLimit = holder.eap$getProviderSmartDoublingLimit();
            }
        }
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initSmartSync_Public(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof ISmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
                this.eap$PerProviderScalingLimit = holder.eap$getProviderSmartDoublingLimit();
            }
        } catch (Throwable t) {
            EAP$LOGGER.error("Error initializing smart doubling sync", t);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initSmartSync_Protected(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof ISmartDoublingHolder holder) {
                this.eap$SmartDoubling = holder.eap$getSmartDoubling();
                this.eap$PerProviderScalingLimit = holder.eap$getProviderSmartDoublingLimit();
            }
        } catch (Throwable t) {
            EAP$LOGGER.error("Error initializing smart doubling sync", t);
        }
    }


    @Override
    public boolean eap$getSmartDoublingSynced() {
        return this.eap$SmartDoubling;
    }

    @Override
    public int eap$getScalingLimit() {
        return this.eap$PerProviderScalingLimit;
    }
}
