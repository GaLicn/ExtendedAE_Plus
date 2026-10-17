package com.extendedae_plus.compat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.extendedae_plus.client.jei.NetworkItemCache;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.util.GuiUtil;
import com.extendedae_plus.util.NumberFormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 物品浏览器（JEI / EMI）共用的「AE2 网络存量 + 可合成」叠加绘制。
 * <p>
 * 数据来自 {@link NetworkItemCache}，由服务端 {@code JeiSyncManager} 周期同步。
 * 本类不引用任何 JEI / EMI 类型，两套集成只需各自枚举条目槽位并解析出 {@link AEKey}，
 * 再调用 {@link #renderSlot} 即可，保证两边的字号、位置与标识样式完全一致。
 */
public final class NetworkStockOverlay {
    private NetworkStockOverlay() {
    }

    /** 叠加层是否应当绘制：配置开启且已收到网络库存同步。 */
    public static boolean isEnabled() {
        return ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get() && NetworkItemCache.INSTANCE.isConnected();
    }

    /**
     * 在某个条目槽位绘制存量与可合成标识。
     *
     * @param slotX 槽位左上角（不含内边距），与 {@link GuiUtil#drawAmountText} 的坐标系一致
     */
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
