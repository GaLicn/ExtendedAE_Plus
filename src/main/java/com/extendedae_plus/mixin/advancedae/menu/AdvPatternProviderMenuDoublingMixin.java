package com.extendedae_plus.mixin.advancedae.menu;

import appeng.api.config.YesNo;
import appeng.menu.guisync.GuiSync;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.api.smartDoubling.IPatternProviderMenuDoublingSync;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingHolder;
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
public abstract class AdvPatternProviderMenuDoublingMixin implements IPatternProviderMenuDoublingSync {
    @Shadow @Final protected AdvPatternProviderLogic logic;
    @Unique @GuiSync(21) private YesNo eap$SmartDoubling;
    @Unique @GuiSync(22) private int eap$PerProviderScalingLimit = 0;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncSmartDoubling(CallbackInfo ci) {
        if (!((AdvPatternProviderMenu) (Object) this).isClientSide()) {
            var l = this.logic;
            this.eap$SmartDoubling = l.getConfigManager().getSetting(EAPSettings.SMART_DOUBLING);
            if (l instanceof ISmartDoublingHolder holder) {
                this.eap$PerProviderScalingLimit = holder.eap$getProviderSmartDoublingLimit();
            }
        }
    }

    @Override
    public YesNo eap$getSmartDoublingSynced() {
        return this.eap$SmartDoubling;
    }

    @Override
    public int eap$getScalingLimit() {
        return this.eap$PerProviderScalingLimit;
    }
}
