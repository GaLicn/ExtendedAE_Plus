package com.extendedae_plus.mixin.advancedae.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import com.extendedae_plus.api.PatternProviderMenuDoublingSync;
import com.extendedae_plus.api.SmartDoublingHolder;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
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

    @Override
    public boolean eap$getSmartDoublingSynced() {
        return this.eap$SmartDoubling;
    }
}
