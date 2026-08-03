package com.extendedae_plus.mixin.jei;

import appeng.api.stacks.AEItemKey;
import com.extendedae_plus.client.jei.JeiOverlayRenderer;
import com.extendedae_plus.client.jei.NetworkItemCache;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.gui.overlay.IngredientListRenderer;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
        if (!NetworkItemCache.INSTANCE.isConnected()) return;

        for (IngredientListSlot slot : this.slots) {
            if (slot.isBlocked()) continue;

            var optElement = slot.getOptionalElement();
            if (optElement.isEmpty()) continue;

            IElement<?> element = optElement.get();
            ITypedIngredient<?> typed = element.getTypedIngredient();

            if (typed.getType() != VanillaTypes.ITEM_STACK) continue;

            ItemStack itemStack = (ItemStack) typed.getIngredient();
            if (itemStack.isEmpty()) continue;

            AEItemKey key = AEItemKey.of(itemStack);
            if (key == null) continue;

            long amount = NetworkItemCache.INSTANCE.getAmount(key);
            boolean craftable = NetworkItemCache.INSTANCE.isCraftable(key);

            if (amount <= 0 && !craftable) continue;

            var area = slot.getArea();
            int padding = slot.getPadding();
            int renderX = area.getX() + padding;
            int renderY = area.getY() + padding;

            JeiOverlayRenderer.renderOverlay(guiGraphics, renderX, renderY, amount, craftable);
        }
    }
}
