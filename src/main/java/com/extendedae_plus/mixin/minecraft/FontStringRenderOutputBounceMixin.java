package com.extendedae_plus.mixin.minecraft;

import com.extendedae_plus.util.AchievementTextRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
@Pseudo
public abstract class FontStringRenderOutputBounceMixin {
    @Unique
    private static final int EAP$BOUNCE_GROUP_SIZE = 3;
    @Unique
    private static final long EAP$BOUNCE_GROUP_STEP_MS = 180L;
    @Unique
    private static final long EAP$BOUNCE_GLYPH_STAGGER_MS = 25L;
    @Unique
    private static final long EAP$BOUNCE_DURATION_MS = 130L;
    @Unique
    private static final float EAP$BOUNCE_AMPLITUDE = 2.0F;

    @ModifyArg(
            method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V"
            ),
            index = 5,
            require = 0
    )
    private float eap$bounceDescriptionGlyph(float y) {
        AchievementTextRenderState.GlyphContext glyphContext = AchievementTextRenderState.currentGlyphContext();
        if (glyphContext == null) {
            return y;
        }

        int glyphIndex = glyphContext.glyphIndex();
        int groupCount = (glyphContext.descriptionGlyphCount() + EAP$BOUNCE_GROUP_SIZE - 1)
                / EAP$BOUNCE_GROUP_SIZE;
        long cycleDuration = (long) groupCount * EAP$BOUNCE_GROUP_STEP_MS;
        long elapsed = Math.floorMod(
                System.currentTimeMillis() - glyphContext.animationStartTimeMillis(), cycleDuration);
        int activeGroup = (int) (elapsed / EAP$BOUNCE_GROUP_STEP_MS);
        if (glyphIndex / EAP$BOUNCE_GROUP_SIZE != activeGroup) {
            return y;
        }

        long groupElapsed = elapsed % EAP$BOUNCE_GROUP_STEP_MS;
        long glyphElapsed = groupElapsed - glyphIndex % EAP$BOUNCE_GROUP_SIZE * EAP$BOUNCE_GLYPH_STAGGER_MS;
        if (glyphElapsed < 0L || glyphElapsed >= EAP$BOUNCE_DURATION_MS) {
            return y;
        }

        double phase = Math.PI * glyphElapsed / EAP$BOUNCE_DURATION_MS;
        float yOffset = EAP$BOUNCE_AMPLITUDE * (float) Math.sin(phase);
        return y - yOffset;
    }
}
