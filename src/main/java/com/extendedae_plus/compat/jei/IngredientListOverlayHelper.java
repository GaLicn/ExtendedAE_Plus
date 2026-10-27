package com.extendedae_plus.compat.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.extendedae_plus.compat.AppliedMekanisticsCompat;
import com.extendedae_plus.compat.NetworkStockOverlay;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class IngredientListOverlayHelper {
    private IngredientListOverlayHelper() {
    }

    public static void render(GuiGraphics guiGraphics, List<IngredientListSlot> slots) {
        // 关闭显示时不再扫描 JEI 槽位，避免产生无意义的逐帧开销。
        if (!NetworkStockOverlay.isEnabled()) {
            return;
        }

        IIngredientType<?> chemicalIngredientType = hasAppliedMekanistics()
                ? AppliedMekanisticsCompat.getChemicalIngredientType()
                : null;
        for (IngredientListSlot slot : slots) {
            if (slot.isBlocked()) {
                continue;
            }
            // 当前 JEI 已公开槽位信息，直接读取以避免额外的版本适配 Mixin。
            IElement<?> element = slot.getOptionalElement().orElse(null);
            if (element == null) {
                continue;
            }
            var typedIngredient = element.getTypedIngredient();
            AEKey key = toKey(typedIngredient, chemicalIngredientType);
            if (key == null) {
                continue;
            }
            var area = slot.getArea();
            int padding = slot.getPadding();
            NetworkStockOverlay.renderSlot(guiGraphics, key, area.getX() + padding, area.getY() + padding);
        }
    }

    private static AEKey toKey(ITypedIngredient<?> typedIngredient,
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
