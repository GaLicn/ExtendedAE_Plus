package com.extendedae_plus.mixin.advancedae.menu;

import appeng.api.config.YesNo;
import appeng.menu.guisync.GuiSync;
import com.extendedae_plus.api.advancedBlocking.IPatternProviderMenuAdvancedSync;
import com.extendedae_plus.api.config.EAPSettings;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvPatternProviderMenu.class, remap = false)
public abstract class AdvPatternProviderMenuAdvancedMixin implements IPatternProviderMenuAdvancedSync {
    @Shadow @Final protected AdvPatternProviderLogic logic;
    @Unique @GuiSync(20) private YesNo eap$AdvancedBlocking;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncSmartDoubling(CallbackInfo ci) {
        if (!((AdvPatternProviderMenu) (Object) this).isClientSide()) {
            this.eap$AdvancedBlocking = this.logic.getConfigManager().getSetting(EAPSettings.ADVANCED_BLOCKING);
        }
    }

    @Override
    public YesNo eap$getAdvancedBlockingSynced() {
        return this.eap$AdvancedBlocking;
    }
}
