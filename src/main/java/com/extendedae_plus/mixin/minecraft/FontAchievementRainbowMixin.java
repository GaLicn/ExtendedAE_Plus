package com.extendedae_plus.mixin.minecraft;

import com.extendedae_plus.util.AchievementTextRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
@Pseudo
public abstract class FontAchievementRainbowMixin {
    @Unique
    private static final long EAP$RAINBOW_PERIOD_MS = 6_000L;
    @Unique
    private static final float EAP$RAINBOW_CODE_POINT_STEP = 0.035F;
    @Unique
    private static final int EAP$TITLE_GOLD = 0xFFAA00;
    @Unique
    private static final long EAP$DESCRIPTION_IDLE_RESET_MS = 250L;
    @Unique
    private static final String EAP$TITLE_KEY = "advancements.extendedae_plus.walking_event_horizon.title";
    @Unique
    private static final String EAP$DESCRIPTION_KEY = "advancements.extendedae_plus.walking_event_horizon.description";
    @Unique
    private static final String[] EAP$INFINITY_DISK_DESCRIPTION_KEYS = {
            "tooltip.extendedae_plus.infinity_biginteger_cell.line.1",
            "tooltip.extendedae_plus.infinity_biginteger_cell.line.2"
    };
    @Unique
    private static long EAP$DESCRIPTION_ANIMATION_START_TIME_MS = -1L;
    @Unique
    private static long EAP$LAST_DESCRIPTION_RENDER_TIME_MS = -1L;

    @ModifyVariable(
            method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private FormattedCharSequence eap$styleWalkingEventHorizonText(FormattedCharSequence text) {
        String renderedText = eap$toString(text);
        String title = Language.getInstance().getOrDefault(EAP$TITLE_KEY);
        if (renderedText.equals(title)) {
            return eap$withColor(text, EAP$TITLE_GOLD);
        }

        FormattedCharSequence achievementDescription = eap$rainbowDescription(
                text, renderedText, Language.getInstance().getOrDefault(EAP$DESCRIPTION_KEY));
        if (achievementDescription != null) {
            return achievementDescription;
        }

        String[] diskDescriptionLines = new String[EAP$INFINITY_DISK_DESCRIPTION_KEYS.length];
        int diskDescriptionGlyphCount = 0;
        for (int index = 0; index < EAP$INFINITY_DISK_DESCRIPTION_KEYS.length; index++) {
            diskDescriptionLines[index] = eap$stripFormattingCodes(
                    Language.getInstance().getOrDefault(EAP$INFINITY_DISK_DESCRIPTION_KEYS[index]));
            diskDescriptionGlyphCount += diskDescriptionLines[index].codePointCount(
                    0, diskDescriptionLines[index].length());
        }

        int lineOffset = 0;
        for (String descriptionLine : diskDescriptionLines) {
            FormattedCharSequence diskDescription = eap$rainbowDescription(
                    text, renderedText, descriptionLine, lineOffset, diskDescriptionGlyphCount);
            if (diskDescription != null) {
                return diskDescription;
            }
            lineOffset += descriptionLine.codePointCount(0, descriptionLine.length());
        }
        return text;
    }

    @Unique
    private FormattedCharSequence eap$rainbowDescription(FormattedCharSequence text, String renderedText,
                                                         String description) {
        String sanitizedDescription = eap$stripFormattingCodes(description);
        int descriptionGlyphCount = sanitizedDescription.codePointCount(0, sanitizedDescription.length());
        return eap$rainbowDescription(text, renderedText, sanitizedDescription, 0, descriptionGlyphCount);
    }

    @Unique
    private FormattedCharSequence eap$rainbowDescription(FormattedCharSequence text, String renderedText,
                                                         String description, int descriptionBaseOffset,
                                                         int descriptionGlyphCount) {
        String normalizedDescription = eap$removeWhitespace(description);
        String normalizedRenderedText = eap$removeWhitespace(eap$stripFormattingCodes(renderedText));
        if (normalizedRenderedText.isEmpty()) {
            return null;
        }

        int normalizedOffset = normalizedDescription.indexOf(normalizedRenderedText);
        if (normalizedOffset < 0) {
            return null;
        }

        int descriptionOffset = descriptionBaseOffset + eap$codePointOffset(description, normalizedOffset);
        long animationStartTimeMillis = eap$descriptionAnimationStartTimeMillis();
        return eap$rainbow(text, descriptionOffset, descriptionGlyphCount, animationStartTimeMillis);
    }

    @Unique
    private FormattedCharSequence eap$withColor(FormattedCharSequence text, int color) {
        return sink -> text.accept((position, style, codePoint) ->
                sink.accept(position, style.withColor(color), codePoint));
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

    @Unique
    private String eap$stripFormattingCodes(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\u00A7' && index + 1 < text.length()) {
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    @Unique
    private int eap$codePointOffset(String text, int normalizedOffset) {
        int normalizedIndex = 0;
        int codePointOffset = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                if (normalizedIndex == normalizedOffset) {
                    return codePointOffset;
                }
                normalizedIndex++;
            }
            codePointOffset++;
            index += Character.charCount(codePoint);
        }
        return codePointOffset;
    }

    @Unique
    private static synchronized long eap$descriptionAnimationStartTimeMillis() {
        long now = System.currentTimeMillis();
        if (EAP$LAST_DESCRIPTION_RENDER_TIME_MS < 0L
                || now - EAP$LAST_DESCRIPTION_RENDER_TIME_MS > EAP$DESCRIPTION_IDLE_RESET_MS) {
            EAP$DESCRIPTION_ANIMATION_START_TIME_MS = now;
        }
        EAP$LAST_DESCRIPTION_RENDER_TIME_MS = now;
        return EAP$DESCRIPTION_ANIMATION_START_TIME_MS;
    }

    @Unique
    private FormattedCharSequence eap$rainbow(FormattedCharSequence text, int startIndex,
                                              int descriptionGlyphCount, long animationStartTimeMillis) {
        float phase = (System.currentTimeMillis() % EAP$RAINBOW_PERIOD_MS) / (float) EAP$RAINBOW_PERIOD_MS;
        return sink -> {
            int[] codePointIndex = {startIndex};
            return text.accept((position, style, codePoint) -> {
                int glyphIndex = codePointIndex[0]++;
                float hue = (phase + glyphIndex * EAP$RAINBOW_CODE_POINT_STEP) % 1.0F;
                Style rainbowStyle = style.withColor(Mth.hsvToRgb(hue, 0.75F, 1.0F));
                AchievementTextRenderState.GlyphContext previous = AchievementTextRenderState.enterDescriptionGlyph(
                        glyphIndex, descriptionGlyphCount, animationStartTimeMillis);
                try {
                    return sink.accept(position, rainbowStyle, codePoint);
                } finally {
                    AchievementTextRenderState.exitDescriptionGlyph(previous);
                }
            });
        };
    }
}
