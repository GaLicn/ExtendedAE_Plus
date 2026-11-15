package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.compat.emi.EmiStockOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.StackBatcher;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 EMI 侧边栏图标合批绘制完成后叠加库存，保留当前分页起点。 */
@Pseudo
@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$ScreenSpace", remap = false)
public abstract class EmiScreenManagerScreenSpaceMixin {
    @WrapOperation(method = "render(Ldev/emi/emi/runtime/EmiDrawContext;IIFI)V",
            at = @At(value = "INVOKE", target = "Ldev/emi/emi/screen/StackBatcher;render(Ldev/emi/emi/api/stack/EmiIngredient;Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
            remap = false)
    private void eap$renderSidebarEntry(StackBatcher batcher, EmiIngredient ingredient, GuiGraphics graphics,
            int x, int y, float delta, Operation<Void> original) {
        EmiIngredient previous = EmiStockOverlay.setRenderingEntry(ingredient);
        try {
            original.call(batcher, ingredient, graphics, x, y, delta);
        } finally {
            EmiStockOverlay.setRenderingEntry(previous);
        }
    }

    @Inject(
            method = "render(Ldev/emi/emi/runtime/EmiDrawContext;IIFI)V",
            at = @At("TAIL"),
            remap = false
    )
    private void eap$renderNetworkStock(EmiDrawContext context, int mouseX, int mouseY, float delta,
            int startIndex, CallbackInfo ci) {
        GuiGraphics guiGraphics = context.raw();
        if (guiGraphics == null) {
            return;
        }
        EmiStockOverlay.render(guiGraphics, (EmiScreenManager.ScreenSpace) (Object) this, startIndex);
    }
}
