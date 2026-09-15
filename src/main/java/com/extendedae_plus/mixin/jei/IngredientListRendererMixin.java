package com.extendedae_plus.mixin.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.extendedae_plus.client.jei.NetworkItemCache;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.compat.AppliedMekanisticsCompat;
import com.extendedae_plus.util.GuiUtil;
import com.extendedae_plus.util.NumberFormatUtil;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
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

    @Shadow
    @Final
    private List<IngredientListSlot> slots;

    @Inject(method = "render", at = @At("TAIL"))
    private void eap$renderNetworkOverlay(GuiGraphics guiGraphics, CallbackInfo ci) {
        // 关闭显示时不再扫描 JEI 槽位，避免产生无意义的逐帧开销。
        if (!ModConfig.INSTANCE.jeiNetworkOverlayEnabled
                || !NetworkItemCache.INSTANCE.isConnected()) return;

        for (IngredientListSlot slot : this.slots) {
            if (slot.isBlocked()) continue;

            var optElement = slot.getOptionalElement();
            if (optElement.isEmpty()) continue;

            IElement<?> element = optElement.get();
            ITypedIngredient<?> typed = element.getTypedIngredient();

            AEKey key = eap$toKey(typed);
            if (key == null) continue;

            long amount = NetworkItemCache.INSTANCE.getAmount(key);
            boolean craftable = NetworkItemCache.INSTANCE.isCraftable(key);

            if (amount <= 0 && !craftable) continue;

            var area = slot.getArea();
            int padding = slot.getPadding();
            int x = area.getX() + padding;
            int y = area.getY() + padding;
            var font = Minecraft.getInstance().font;
            if (amount > 0) {
                GuiUtil.drawAmountText(guiGraphics, font, eap$formatAmount(key, amount), x, y);
                if (craftable) {
                    eap$renderCraftableMarker(guiGraphics, x, y);
                }
            } else {
                GuiUtil.drawAmountText(guiGraphics, font, "Craft", x, y);
            }
        }
    }

    @Unique
    private static AEKey eap$toKey(ITypedIngredient<?> typed) {
        if (typed.getType() == VanillaTypes.ITEM_STACK) {
            ItemStack itemStack = (ItemStack) typed.getIngredient();
            return itemStack.isEmpty() ? null : AEItemKey.of(itemStack);
        }

        if (typed.getType() == ForgeTypes.FLUID_STACK) {
            FluidStack fluidStack = (FluidStack) typed.getIngredient();
            return fluidStack.isEmpty() ? null : AEFluidKey.of(fluidStack);
        }

        if (AppliedMekanisticsCompat.isChemicalType(typed.getType())) {
            return AppliedMekanisticsCompat.toKey(typed.getIngredient());
        }

        return null;
    }

    @Unique
    private static String eap$formatAmount(AEKey key, long amount) {
        if (key.getAmountPerUnit() <= 1) {
            return NumberFormatUtil.formatNumber(amount);
        }

        String result = key.formatAmount(amount, AmountFormat.SLOT);
        String unit = key.getUnitSymbol();
        return unit == null || unit.isEmpty() ? result : result + unit;
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
