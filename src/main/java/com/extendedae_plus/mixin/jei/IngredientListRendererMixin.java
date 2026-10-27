package com.extendedae_plus.mixin.jei;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.extendedae_plus.compat.jei.IngredientListOverlayHelper;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Pseudo
@Mixin(value = IngredientListRenderer.class, remap = false)
public class IngredientListRendererMixin {
    @Shadow @Final private List<IngredientListSlot> slots;

    @WrapMethod(method = "render")
    private void eap$renderNetworkOverlay(GuiGraphics guiGraphics, Operation<Void> original) {
        // JEI 会在原方法尾部复用 this 槽位，包装方法可安全保留渲染器实例。
        original.call(guiGraphics);
        IngredientListOverlayHelper.render(guiGraphics, slots);
    }
}
