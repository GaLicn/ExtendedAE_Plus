package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.compat.emi.EmiStockOverlay;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(value = EmiFavorite.class, remap = false)
public abstract class EmiFavoriteMixin {
    @ModifyArg(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIFI)V",
            at = @At(value = "INVOKE", target = "Ldev/emi/emi/api/stack/EmiIngredient;render(Lnet/minecraft/client/gui/GuiGraphics;IIFI)V"),
            index = 4, remap = false)
    private int eap$replaceRecipeAmount(int flags) {
        return EmiStockOverlay.shouldReplaceRecipeAmount((EmiFavorite) (Object) this)
                ? flags & ~EmiIngredient.RENDER_AMOUNT : flags;
    }
}
