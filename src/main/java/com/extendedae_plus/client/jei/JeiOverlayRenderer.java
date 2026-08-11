package com.extendedae_plus.client.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class JeiOverlayRenderer {
    private static final char[] POSTFIXES = {'K', 'M', 'G', 'T', 'P', 'E'};

    private JeiOverlayRenderer() {
    }

    public static void renderOverlay(GuiGraphics guiGraphics, int x, int y, long amount, boolean craftable) {
        if (amount <= 0 && !craftable) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        if (amount > 0) {
            renderSizeLabel(guiGraphics, font, x, y, formatAmount(amount, 3));
            if (craftable) {
                renderCraftableMarker(guiGraphics, font, x, y);
            }
        } else {
            renderSizeLabel(guiGraphics, font, x, y, "Craft");
        }
    }

    private static void renderSizeLabel(GuiGraphics guiGraphics, Font font, int slotX, int slotY, String text) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);
        float scaleFactor = 0.5f;
        float renderX = (slotX + 16 - font.width(text) * scaleFactor) / scaleFactor;
        float renderY = (slotY + 16 - 7 * scaleFactor) / scaleFactor;
        poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
        guiGraphics.drawString(font, text, (int) renderX, (int) renderY, 0xFFFFFF, true);
        poseStack.popPose();
    }

    private static void renderCraftableMarker(GuiGraphics guiGraphics, Font font, int slotX, int slotY) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);
        float scaleFactor = 0.5f;
        poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
        guiGraphics.drawString(font, "+", (int) ((slotX + 1) / scaleFactor), (int) ((slotY + 1) / scaleFactor), 0xFFFFFF, true);
        poseStack.popPose();
    }

    public static String formatAmount(long number, int width) {
        if (number < 0 || Long.toString(number).length() <= width) {
            return Long.toString(number);
        }

        long base = number;
        long last;
        int exponent = -1;
        do {
            last = base;
            base /= 1000;
            exponent++;
        } while (Long.toString(base).length() + 1 > width && exponent < POSTFIXES.length - 1);

        String suffix = String.valueOf(POSTFIXES[exponent]);
        String precise = String.format("%.1f", last / 1000.0) + suffix;
        return precise.length() <= width ? precise : base + suffix;
    }
}
