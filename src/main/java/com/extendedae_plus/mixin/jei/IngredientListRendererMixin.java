package com.extendedae_plus.mixin.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.extendedae_plus.compat.AppliedMekanisticsCompat;
import com.extendedae_plus.compat.NetworkStockOverlay;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
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
        if (!NetworkStockOverlay.isEnabled()) return;

        for (IngredientListSlot slot : this.slots) {
            if (slot.isBlocked()) continue;

            var optElement = slot.getOptionalElement();
            if (optElement.isEmpty()) continue;

            IElement<?> element = optElement.get();
            ITypedIngredient<?> typed = element.getTypedIngredient();

            AEKey key = eap$toKey(typed);
            if (key == null) continue;

            var area = slot.getArea();
            int padding = slot.getPadding();
            NetworkStockOverlay.renderSlot(guiGraphics, key, area.getX() + padding, area.getY() + padding);
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
        
        if (ModList.get().isLoaded("appmek") && ModList.get().isLoaded("mekanism")
                && AppliedMekanisticsCompat.isChemicalType(typed.getType())) {
            return AppliedMekanisticsCompat.toKey(typed.getIngredient());
        }

        return null;
    }

}
