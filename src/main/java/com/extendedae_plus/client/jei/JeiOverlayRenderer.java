package com.extendedae_plus.client.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class JeiOverlayRenderer {

    private static final char[] POSTFIXES = {'K', 'M', 'G', 'T', 'P', 'E'};

    public static void renderOverlay(GuiGraphics guiGraphics, int x, int y, long amount, boolean craftable) {
        if (amount <= 0 && !craftable) return;

        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = guiGraphics.pose();

        if (amount > 0) {
            String text = formatAmount(amount, 3);
            renderSizeLabel(guiGraphics, font, x, y, text);
            if (craftable) {
                renderCraftableMarker(guiGraphics, font, x, y);
            }
        } else if (craftable) {
            renderSizeLabel(guiGraphics, font, x, y, "Craft");
        }
    }

    private static void renderSizeLabel(GuiGraphics guiGraphics, Font font, int slotX, int slotY, String text) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        float scaleFactor = 0.5f;
        int width = font.width(text);

        float renderX = (slotX + 16 - width * scaleFactor) / scaleFactor;
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
        float renderX = (slotX + 1) / scaleFactor;
        float renderY = (slotY + 1) / scaleFactor;

        poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
        guiGraphics.drawString(font, "+", (int) renderX, (int) renderY, 0xFFFFFF, true);
        poseStack.popPose();
    }

    public static String formatAmount(long number, int width) {
        if (number < 0) return String.valueOf(number);

        String plain = Long.toString(number);
        if (plain.length() <= width) return plain;

        long base = number;
        double last = base * 1000.0;
        int exponent = -1;

        while (Long.toString(base).length() + 1 > width) {
            last = base;
            base /= 1000;
            exponent++;
            if (exponent >= POSTFIXES.length - 1) break;
        }

        String postFix = String.valueOf(POSTFIXES[exponent]);
        String withPrecision = String.format("%.1f", last / 1000.0) + postFix;
        String withoutPrecision = base + postFix;

        if (withPrecision.length() <= width) return withPrecision;
        return withoutPrecision;
    }
}
