package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.config.YesNo;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.api.smartDoubling.IPatternProviderMenuDoublingSync;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuDoublingMixin implements IPatternProviderMenuDoublingSync {
    @Shadow @Final protected PatternProviderLogic logic;
    @Unique @GuiSync(21) private YesNo eap$SmartDoubling;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncSmartDoubling(CallbackInfo ci) {
        if (!((PatternProviderMenu) (Object) this).isClientSide()) {
            this.eap$SmartDoubling = this.logic.getConfigManager().getSetting(EAPSettings.SMART_DOUBLING);
        }
    }

    @Override
    public YesNo eap$getSmartDoublingSynced() {
        return this.eap$SmartDoubling;
    }
}