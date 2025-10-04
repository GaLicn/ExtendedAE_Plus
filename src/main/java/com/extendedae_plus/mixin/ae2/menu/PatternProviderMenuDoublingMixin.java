package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.PatternProviderMenuDoublingSync;
import com.extendedae_plus.api.SmartDoublingHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
                // debug removed
            }
        }
    }

    @Override
    public boolean eap$getSmartDoublingSynced() {
        return this.eap$SmartDoubling;
    }
}
