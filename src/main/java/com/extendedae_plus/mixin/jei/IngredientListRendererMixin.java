package com.extendedae_plus.mixin.jei;

import appeng.api.stacks.AEItemKey;
import com.extendedae_plus.client.jei.NetworkItemCache;
import com.extendedae_plus.util.GuiUtil;
import com.extendedae_plus.util.NumberFormatUtil;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.gui.overlay.IngredientListRenderer;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = IngredientListRenderer.class, remap = false)
public class IngredientListRendererMixin {
    @Shadow @Final private List<IngredientListSlot> slots;

    @Inject(method = "render", at = @At("TAIL"))
    private void eap$renderNetworkOverlay(GuiGraphics guiGraphics, CallbackInfo callbackInfo) {
        if (!NetworkItemCache.INSTANCE.isConnected()) {
            return;
        }
        for (IngredientListSlot slot : slots) {
            if (slot.isBlocked() || slot.getOptionalElement().isEmpty()) {
                continue;
            }
            IElement<?> element = slot.getOptionalElement().get();
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
            var area = slot.getArea();
            int padding = slot.getPadding();
            int x = area.getX() + padding;
            int y = area.getY() + padding;
            var font = Minecraft.getInstance().font;
            if (amount > 0) {
                GuiUtil.drawAmountText(guiGraphics, font, NumberFormatUtil.formatNumber(amount), x, y);
                if (craftable) {
                    eap$renderCraftableMarker(guiGraphics, x, y);
                }
            } else {
                GuiUtil.drawAmountText(guiGraphics, font, "Craft", x, y);
            }
        }
    }

    @Unique
    private static void eap$renderCraftableMarker(GuiGraphics guiGraphics, int slotX, int slotY) {
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
