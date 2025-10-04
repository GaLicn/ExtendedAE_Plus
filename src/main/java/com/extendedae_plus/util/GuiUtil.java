package com.extendedae_plus.util;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.me.patternaccess.PatternSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;


/**
 * GUI工具类，提供样板获取、绘制等通用功能
 */
public class GuiUtil {
    private GuiUtil() {throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");}

    /**
     * 从样板中获取输出数量文本
     *
     * @param pattern 样板物品
     * @return 格式化后的数量文本
     */
    public static String getPatternOutputText(ItemStack pattern) {
        if (pattern.isEmpty()) {
            return "";
        }

        var details = PatternDetailsHelper.decodePattern(pattern, Minecraft.getInstance().level);
        if (details == null) {
            return "";
        }
        java.util.List<GenericStack> outputs = details.getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        GenericStack out = outputs.getFirst();
        long amount = out.amount();
        long perUnit = out.what().getAmountPerUnit();
        if (amount <= 0 || perUnit <= 0) {
            return "";
        }

        // 计算实际单位数量，支持小数
        double units = (double) amount / perUnit;
        if (units <= 0) {
            return "";
        }

        // 自动判断是否为流体，避免重复后缀
        String autoSuffix = "";
        if (perUnit > 1) {
            // 如果每单位数量大于1，说明是流体（如1000mB = 1B）
            autoSuffix = "B";
        }
        return NumberFormatUtil.formatNumberWithDecimal(units) + autoSuffix;
    }

    /**
     * 在槽位右下角绘制数量文本
     * @param guiGraphics GUI图形上下文
     * @param font 字体
     * @param text 要绘制的文本
     * @param slotX 槽位X坐标
     * @param slotY 槽位Y坐标
     * @param scale 缩放比例
     */
    public static void drawAmountText(GuiGraphics guiGraphics, Font font, String text, int slotX, int slotY, float scale) {
        if (text.isEmpty()) {
            return;
        }

        // 计算缩放后的字体宽度，确保右对齐
        int scaledWidth = (int)(font.width(text) * scale);
        int textX = slotX + 16 - scaledWidth;
        int textY = slotY + 11; // 右下角显示

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300); // 提升 Z，确保在最上层
        guiGraphics.pose().scale(scale, scale, 1.0f); // 缩小字体
        guiGraphics.drawString(font, text, (int)(textX / scale), (int)(textY / scale), 0xFFFFFFFF, true);
        guiGraphics.pose().popPose();
    }

    // Helper: add alpha channel to RGB (rgb is 0xRRGGBB)
    private static int withAlpha(int rgb, int alpha255) {
        return ((alpha255 & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    // HSV -> RGB (returns 0xRRGGBB)
    private static int hsvToRgb(float h, float s, float v) {
        if (s <= 0.0f) {
            int g = Math.round(v * 255.0f);
            return (g << 16) | (g << 8) | g;
        }
        float hh = (h - (float) Math.floor(h)) * 6.0f;
        int sector = (int) Math.floor(hh);
        float f = hh - sector;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r, g, b;
        switch (sector) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        int ri = Math.round(r * 255.0f);
        int gi = Math.round(g * 255.0f);
        int bi = Math.round(b * 255.0f);
        return (ri << 16) | (gi << 8) | bi;
    }

    // 返回当前时间对应的彩虹色（0xRRGGBB），周期固定为 4000ms
    private static int getRainbowRgb() {
        long now = System.currentTimeMillis();
        final long rainbowPeriodMs = 4000L;
        float hue = (now % rainbowPeriodMs) / (float) rainbowPeriodMs; // 0.0 ~ 1.0
        return hsvToRgb(hue, 1.0f, 1.0f);
    }

    // 在给定槽位坐标绘制 1px 边框（18x18）和 16x16 半透明背景
    private static void drawSlotBox(GuiGraphics guiGraphics, int sx, int sy, int borderColor, int backgroundColor) {
        guiGraphics.fill(sx - 1, sy - 1, sx + 17, sy, borderColor);
        guiGraphics.fill(sx - 1, sy + 16, sx + 17, sy + 17, borderColor);
        guiGraphics.fill(sx - 1, sy, sx, sy + 16, borderColor);
        guiGraphics.fill(sx + 16, sy, sx + 17, sy + 16, borderColor);
        guiGraphics.fill(sx, sy, sx + 16, sy + 16, backgroundColor);
    }

    /**
     * 在槽位上绘制彩色流转的高亮和浅底色
     */
    public static void drawPatternSlotHighlights(GuiGraphics guiGraphics, List<Slot> slots, Set<ItemStack> matchedStack, Set<PatternContainerRecord> matchedProvider) {
        if (slots == null) return;

        int rainbowRgb = getRainbowRgb();

        for (Slot slot : slots) {
            if (!(slot instanceof PatternSlot ps)) {
                continue;
            }

            int sx = slot.x;
            int sy = slot.y;

            boolean isMatchedSlot = matchedStack != null && matchedStack.contains(slot.getItem());
            boolean isMatchedProvider = false;
            try {
                PatternContainerRecord container = ps.getMachineInv();
                isMatchedProvider = matchedProvider != null && matchedProvider.contains(container);
            } catch (Throwable ignored) {
            }

            int borderColor;
            int backgroundColor;

            if (isMatchedSlot) {
                borderColor = withAlpha(rainbowRgb, 0xA0);
                backgroundColor = withAlpha(rainbowRgb, 0x3C);
            } else if (!isMatchedProvider) {
                borderColor = withAlpha(0xFFFFFF, 0x40);
                backgroundColor = withAlpha(0x000000, 0x18);
            } else {
                borderColor = withAlpha(0xFFFFFF, 0x30);
                backgroundColor = withAlpha(0xFFFFFF, 0x14);
            }

            drawSlotBox(guiGraphics, sx, sy, borderColor, backgroundColor);
        }
    }

    /**
     * 在指定槽位坐标绘制彩虹流转的边框与浅底色（用于非 PatternSlot 的高亮场景）
     */
    public static void drawSlotRainbowHighlight(GuiGraphics guiGraphics, int sx, int sy) {
        int rainbowRgb = getRainbowRgb();
        int borderColor = withAlpha(rainbowRgb, 0xA0);
        int backgroundColor = withAlpha(rainbowRgb, 0x3C);
        drawSlotBox(guiGraphics, sx, sy, borderColor, backgroundColor);
    }
} 