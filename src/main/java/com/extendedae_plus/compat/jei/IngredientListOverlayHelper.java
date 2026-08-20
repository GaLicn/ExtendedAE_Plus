package com.extendedae_plus.compat.jei;

import appeng.api.stacks.AEItemKey;
import com.extendedae_plus.client.jei.NetworkItemCache;
import com.extendedae_plus.util.GuiUtil;
import com.extendedae_plus.util.NumberFormatUtil;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class IngredientListOverlayHelper {
    private IngredientListOverlayHelper() {
    }

    public static void render(GuiGraphics guiGraphics, List<?> slots) {
        if (!NetworkItemCache.INSTANCE.isConnected()) {
            return;
        }
        for (Object rawSlot : slots) {
            if (!(rawSlot instanceof IngredientListSlotAccessor slot)
                    || slot.eap$isBlocked()
                    || slot.eap$getOptionalElement().isEmpty()) {
                continue;
            }
            IElement<?> element = slot.eap$getOptionalElement().get();
            var typedIngredient = element.getTypedIngredient();
            if (typedIngredient.getType() != VanillaTypes.ITEM_STACK) {
                continue;
            }
            ItemStack stack = (ItemStack) typedIngredient.getIngredient();
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            long amount = NetworkItemCache.INSTANCE.getAmount(key);
            boolean craftable = NetworkItemCache.INSTANCE.isCraftable(key);
            if (amount <= 0 && !craftable) {
                continue;
            }
            var area = slot.eap$getArea();
            int padding = slot.eap$getPadding();
            int x = area.getX() + padding;
            int y = area.getY() + padding;
            var font = Minecraft.getInstance().font;
            if (amount > 0) {
                GuiUtil.drawAmountText(guiGraphics, font, NumberFormatUtil.formatNumber(amount), x, y);
                if (craftable) {
                    renderCraftableMarker(guiGraphics, x, y);
                }
            } else {
                GuiUtil.drawAmountText(guiGraphics, font, "Craft", x, y);
            }
        }
    }

    private static void renderCraftableMarker(GuiGraphics guiGraphics, int slotX, int slotY) {
        // 提升绘制层级，避免 JEI 的物品图标覆盖合成标记。
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
