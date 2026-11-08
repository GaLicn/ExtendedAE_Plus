package com.extendedae_plus.compat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.extendedae_plus.client.jei.NetworkItemCache;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.util.GuiUtil;
import com.extendedae_plus.util.NumberFormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** JEI / EMI 共用的网络库存与可合成标记绘制，不依赖任一查看器的类型。 */
public final class NetworkStockOverlay {
    private NetworkStockOverlay() {
    }

    /** 叠加层是否应当绘制：配置开启且已收到网络库存同步。 */
    public static boolean isEnabled() {
        return ModConfig.INSTANCE.jeiNetworkOverlayEnabled && NetworkItemCache.INSTANCE.isConnected();
    }

    /** 坐标使用物品图标左上角，与 {@link GuiUtil#drawAmountText} 一致。 */
    public static void renderSlot(GuiGraphics guiGraphics, AEKey key, int slotX, int slotY) {
        long amount = NetworkItemCache.INSTANCE.getAmount(key);
        boolean craftable = NetworkItemCache.INSTANCE.isCraftable(key);
        if (amount <= 0 && !craftable) {
            return;
        }
        if (amount > 0) {
            GuiUtil.drawAmountText(guiGraphics, Minecraft.getInstance().font, formatAmount(key, amount), slotX, slotY);
            if (craftable) {
                renderCraftableMarker(guiGraphics, slotX, slotY);
            }
        } else {
            // 网络里没有存量但可合成：只显示 "+"，与 JEI 侧行为一致。
            GuiUtil.drawAmountText(guiGraphics, Minecraft.getInstance().font, "+", slotX, slotY);
        }
    }

    private static String formatAmount(AEKey key, long amount) {
        if (key.getAmountPerUnit() <= 1) {
            return NumberFormatUtil.formatNumber(amount);
        }

        String result = key.formatAmount(amount, AmountFormat.SLOT);
        String unit = key.getUnitSymbol();
        return unit == null || unit.isEmpty() ? result : result + unit;
    }

    private static void renderCraftableMarker(GuiGraphics guiGraphics, int slotX, int slotY) {
        // 提升绘制层级，避免条目图标覆盖合成标记。
        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);
        float scaleFactor = 0.5f;
        poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
        guiGraphics.drawString(Minecraft.getInstance().font, "+", (int) ((slotX + 1) / scaleFactor),
                (int) ((slotY + 1) / scaleFactor), 0xFFFFFF, true);
        poseStack.popPose();
    }
}
