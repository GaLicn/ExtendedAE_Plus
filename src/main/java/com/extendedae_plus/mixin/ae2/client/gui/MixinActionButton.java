package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.widgets.ActionButton;
import appeng.core.localization.ButtonToolTips;
import com.extendedae_plus.config.EAEPConfig;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ActionButton.class)
public class MixinActionButton {
    @Inject(method = "buildMessage", at = @At("TAIL"), cancellable = true)
    private void eaep$modifyButtonTooltips(ButtonToolTips displayName,
                                           ButtonToolTips displayValue,
                                           CallbackInfoReturnable<Component> cir) {
        if (displayValue == ButtonToolTips.EncodeDescription && !EAEPConfig.INDEPENDENT_UPLOADING_BUTTON.getAsBoolean())
            cir.setReturnValue(cir.getReturnValue().copy().append(
                    Component.translatable("tooltip.extendedae_plus.encode_button.upload_pattern")));
    }
}
