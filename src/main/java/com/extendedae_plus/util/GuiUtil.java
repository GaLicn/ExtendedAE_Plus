package com.extendedae_plus.util;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.util.inv.AppEngInternalInventory;
import com.extendedae_plus.mixin.ae2.accessor.PatternAccessTermScreenAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternAccessTermScreenSlotsRowAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalSlotsRowAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;


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


    /**
     * 渲染样板管理终端的数量显示
     * @param guiGraphics GUI图形上下文
     * @param screen 屏幕对象
     */
    public static void renderPatternAmounts(GuiGraphics guiGraphics, Object screen) {
        int scrollLevel;
        int visibleRows;
        ArrayList<?> rowsList;

        if (screen instanceof PatternAccessTermScreenAccessor aeAccessor) {
            var scrollbar = aeAccessor.getScrollbar();
            if (scrollbar == null) return;
            scrollLevel = scrollbar.getCurrentScroll();
            visibleRows = aeAccessor.getVisibleRows();
            if (visibleRows <= 0) return;
            rowsList = aeAccessor.getRows();
            if (rowsList == null || rowsList.isEmpty()) return;
        } else if (screen instanceof GuiExPatternTerminalAccessor exAccessor) {
            var scrollbar = exAccessor.getScrollbar();
            if (scrollbar == null) return;
            scrollLevel = scrollbar.getCurrentScroll();
            visibleRows = exAccessor.getVisibleRows();
            if (visibleRows <= 0) return;
            rowsList = exAccessor.getRows();
            if (rowsList == null || rowsList.isEmpty()) return;
        } else {
            return;
        }
        // 判断是否为ExtendedAE终端
        boolean isExtendedAE = screen instanceof GuiExPatternTerminalAccessor;

        // 根据终端类型使用不同的常量（与 AE2/ExtendedAE 源码保持一致）
        final int SLOT_SIZE = 18; // ROW_HEIGHT == 18, SLOT_SIZE == ROW_HEIGHT
        final int GUI_PADDING_X = isExtendedAE ? 22 : 8; // ExtendedAE使用22，AE2使用8
        final int SLOT_Y_OFFSET = isExtendedAE ? 34 : 0; // ExtendedAE需要额外的Y偏移

        var font = Minecraft.getInstance().font;

        for (int i = 0; i < visibleRows; ++i) {
            int rowIdx = scrollLevel + i;
            if (rowIdx < 0 || rowIdx >= rowsList.size()) {
                continue;
            }

            Object row = rowsList.get(rowIdx);
            if (row instanceof PatternAccessTermScreenSlotsRowAccessor slotsRow) {
                var container = slotsRow.getContainer();
                var inventory = container.getInventory();
                drawRowAmounts(guiGraphics, font, inventory, slotsRow.getOffset(), slotsRow.getSlots(), i, SLOT_SIZE, GUI_PADDING_X, SLOT_Y_OFFSET);
                continue;
            }

            if (row instanceof GuiExPatternTerminalSlotsRowAccessor exSlotsRow) {
                var container = exSlotsRow.getContainer();
                var inventory = container.getInventory();
                drawRowAmounts(guiGraphics, font, inventory, exSlotsRow.getOffset(), exSlotsRow.getSlots(), i, SLOT_SIZE, GUI_PADDING_X, SLOT_Y_OFFSET);
            }
        }
    }

    private static void drawRowAmounts(
            GuiGraphics guiGraphics,
            Font font,
            AppEngInternalInventory inventory,
            int offset,
            int slots,
            int visibleRowIndex,
            int slotSize,
            int guiPaddingX,
            int slotYOffset
    ) {
        for (int col = 0; col < slots; col++) {
            int index = offset + col;
            var pattern = inventory.getStackInSlot(index);
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            String amountText = getPatternOutputText(pattern);
            if (amountText.isEmpty()) {
                continue;
            }
            int slotX = col * slotSize + guiPaddingX;
            int slotY = (visibleRowIndex + 1) * slotSize + slotYOffset;
            drawAmountText(guiGraphics, font, amountText, slotX, slotY, 0.6f);
        }
    }
} 