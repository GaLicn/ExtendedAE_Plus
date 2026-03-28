package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.me.items.ProcessingEncodingPanel;
import appeng.client.gui.widgets.ActionButton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ProcessingEncodingPanel.class, remap = false)
public abstract class ProcessingEncodingPanelMixin {
    @Shadow
    @Final
    private ActionButton cycleOutputBtn;

    @Inject(method = "setVisible", at = @At("TAIL"), remap = false)
    private void eap$hideVanillaCycleOutputButton(boolean visible, CallbackInfo ci) {
        this.cycleOutputBtn.setVisibility(false);
    }
}
