package com.extendedae_plus.mixin.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

import java.util.List;

@Mixin(value = IngredientListRenderer.class, remap = false)
public class IngredientListRendererMixin {

    @Shadow
    @Final
    private List<IngredientListSlot> slots;

    @WrapMethod(method = "render")
    private void eap$renderNetworkOverlay(GuiGraphics guiGraphics, Operation<Void> original) {
        // 新版 JEI 在 render 尾部复用 this 的局部变量槽，包装调用可保留有效的实例引用。
        original.call(guiGraphics);
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
