package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import com.extendedae_plus.util.NumberFormatUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.text.NumberFormat;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public class CraftConfirmScreenMixin {

    @Redirect(
            method = "updateBeforeRender",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;",
                    ordinal = 0
            )
    )
    private String useCustomFormat(NumberFormat instance, long number) {
        return NumberFormatUtil.formatNumber(number);
    }
}