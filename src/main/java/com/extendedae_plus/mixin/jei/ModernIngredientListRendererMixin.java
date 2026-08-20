package com.extendedae_plus.mixin.jei;

import com.extendedae_plus.compat.jei.IngredientListOverlayHelper;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientListRenderer", remap = false)
public class ModernIngredientListRendererMixin {
    @Shadow @Final private List<?> slots;

    @Inject(method = "render", at = @At("TAIL"))
    private void eap$renderNetworkOverlay(GuiGraphics guiGraphics, CallbackInfo callbackInfo) {
        IngredientListOverlayHelper.render(guiGraphics, slots);
    }
}
