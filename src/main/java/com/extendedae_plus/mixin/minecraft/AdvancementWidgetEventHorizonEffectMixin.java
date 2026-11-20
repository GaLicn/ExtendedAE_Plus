package com.extendedae_plus.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AdvancementWidget.class)
@Pseudo
public abstract class AdvancementWidgetEventHorizonEffectMixin {
    @Unique
    private static final String EAP$DESCRIPTION_KEY = "advancements.extendedae_plus.walking_event_horizon.description";

    @WrapOperation(
            method = "drawHover(Lnet/minecraft/client/gui/GuiGraphics;IIFII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"
            )
    )
    private int eap$animateWalkingEventHorizonDescription(
            GuiGraphics guiGraphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color,
            boolean dropShadow,
            Operation<Integer> original
    ) {
        String renderedText = eap$toString(text);
        String description = Language.getInstance().getOrDefault(EAP$DESCRIPTION_KEY);
        String normalizedDescription = eap$removeWhitespace(description);
        String normalizedRenderedText = eap$removeWhitespace(renderedText);
        boolean isDescriptionLine = !normalizedRenderedText.isEmpty()
                && normalizedDescription.contains(normalizedRenderedText);
        return original.call(guiGraphics, font, text, x, y, color, dropShadow || isDescriptionLine);
    }

    @Unique
    private String eap$toString(FormattedCharSequence text) {
        StringBuilder result = new StringBuilder();
        text.accept((position, style, codePoint) -> {
            result.appendCodePoint(codePoint);
            return true;
        });
        return result.toString();
    }

    @Unique
    private String eap$removeWhitespace(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                result.appendCodePoint(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

}
