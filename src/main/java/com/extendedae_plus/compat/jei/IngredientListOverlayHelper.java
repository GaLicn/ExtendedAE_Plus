package com.extendedae_plus.compat.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.extendedae_plus.compat.AppliedMekanisticsCompat;
import com.extendedae_plus.compat.NetworkStockOverlay;
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
        if (!NetworkStockOverlay.isEnabled()) {
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
            var area = slot.eap$getArea();
            int padding = slot.eap$getPadding();
            NetworkStockOverlay.renderSlot(guiGraphics, key, area.getX() + padding, area.getY() + padding);
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
}
