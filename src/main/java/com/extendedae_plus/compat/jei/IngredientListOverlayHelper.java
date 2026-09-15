package com.extendedae_plus.compat.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.extendedae_plus.client.jei.NetworkItemCache;
import com.extendedae_plus.compat.AppliedMekanisticsCompat;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.util.GuiUtil;
import com.extendedae_plus.util.NumberFormatUtil;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class IngredientListOverlayHelper {
    private IngredientListOverlayHelper() {
    }

    public static void render(GuiGraphics guiGraphics, List<?> slots) {
        // 关闭显示时不再扫描 JEI 槽位，避免产生无意义的逐帧开销。
        if (!ModConfigs.JEI_NETWORK_OVERLAY_ENABLED.get() || !NetworkItemCache.INSTANCE.isConnected()) {
            return;
        }

        IIngredientType<?> chemicalIngredientType = hasAppliedMekanistics()
                ? AppliedMekanisticsCompat.getChemicalIngredientType()
                : null;
        for (Object rawSlot : slots) {
            if (!(rawSlot instanceof IngredientListSlotAccessor slot)
                    || slot.eap$isBlocked()
                    || slot.eap$getOptionalElement().isEmpty()) {
                continue;
            }
            IElement<?> element = slot.eap$getOptionalElement().get();
            var typedIngredient = element.getTypedIngredient();
            AEKey key = toKey(typedIngredient, chemicalIngredientType);
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
                GuiUtil.drawAmountText(guiGraphics, font, formatAmount(key, amount), x, y);
                if (craftable) {
                    renderCraftableMarker(guiGraphics, x, y);
                }
            } else {
                GuiUtil.drawAmountText(guiGraphics, font, "Craft", x, y);
            }
        }
    }

    private static AEKey toKey(mezz.jei.api.ingredients.ITypedIngredient<?> typedIngredient,
            IIngredientType<?> chemicalIngredientType) {
        if (typedIngredient.getType() == VanillaTypes.ITEM_STACK) {
            ItemStack stack = (ItemStack) typedIngredient.getIngredient();
            return stack.isEmpty() ? null : AEItemKey.of(stack);
        }

        if (typedIngredient.getType() == NeoForgeTypes.FLUID_STACK) {
            FluidStack stack = (FluidStack) typedIngredient.getIngredient();
            return stack.isEmpty() ? null : AEFluidKey.of(stack);
        }

        if (chemicalIngredientType != null && typedIngredient.getType() == chemicalIngredientType) {
            return AppliedMekanisticsCompat.toKey(typedIngredient.getIngredient());
        }

        return null;
    }

    private static boolean hasAppliedMekanistics() {
        return ModList.get().isLoaded("appmek") && ModList.get().isLoaded("mekanism");
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
