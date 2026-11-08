package com.extendedae_plus.mixin.emi;

import com.extendedae_plus.compat.emi.EmiStockOverlay;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
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
