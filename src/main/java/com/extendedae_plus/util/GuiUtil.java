package com.extendedae_plus.util;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;


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

        var details = PatternDetailsHelper.decodePattern(pattern, Minecraft.getInstance().level, false);
        if (details == null || details.getOutputs().length == 0) {
            return "";
        }

        GenericStack out = details.getOutputs()[0];
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
} 