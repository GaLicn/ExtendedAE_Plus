package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.widgets.ActionButton;
import appeng.core.localization.ButtonToolTips;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.util.GetKey;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ActionButton.class)
public class MixinActionButton {
//    @ModifyVariable(method = "buildMessage", at = @At("STORE"))
//    private static String eaep$modifyEncodeButtonTooltips(String value) {
//        if ()
//    }
    @Inject(method = "buildMessage", at = @At("TAIL"), cancellable = true)
    private void eaep$modifyButtonTooltips(ButtonToolTips displayName,
                                           ButtonToolTips displayValue,
                                           CallbackInfoReturnable<Component> cir) {
        if (displayValue == ButtonToolTips.EncodeDescription && !ModConfig.INDEPENDENT_UPLOADING_BUTTON.getAsBoolean())
            cir.setReturnValue(cir.getReturnValue().copy().append(
                    new GetKey(GetKey.TOOLTIP).addStr("encode_button_description").build()));
    }
}
