package com.extendedae_plus.util;

public final class AchievementTextRenderState {
    private static final ThreadLocal<GlyphContext> CURRENT_GLYPH_CONTEXT = new ThreadLocal<>();

    private AchievementTextRenderState() {
    }

    public static GlyphContext enterDescriptionGlyph(int glyphIndex, int descriptionGlyphCount,
                                                     long animationStartTimeMillis) {
        GlyphContext previous = CURRENT_GLYPH_CONTEXT.get();
        CURRENT_GLYPH_CONTEXT.set(new GlyphContext(glyphIndex, descriptionGlyphCount, animationStartTimeMillis));
        return previous;
    }

    public static GlyphContext currentGlyphContext() {
        return CURRENT_GLYPH_CONTEXT.get();
    }

    public static void exitDescriptionGlyph(GlyphContext previous) {
        if (previous == null) {
            CURRENT_GLYPH_CONTEXT.remove();
        } else {
            CURRENT_GLYPH_CONTEXT.set(previous);
        }
    }

    public record GlyphContext(int glyphIndex, int descriptionGlyphCount, long animationStartTimeMillis) {
    }
}
