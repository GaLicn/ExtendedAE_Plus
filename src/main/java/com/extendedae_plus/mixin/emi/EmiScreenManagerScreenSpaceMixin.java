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

/**
 * 在 EMI 侧边栏（索引 / 收藏 / 查询历史等）每个条目格子上叠加 AE2 网络存量与可合成标识。
 * <p>
 * 注入点取每块侧边栏渲染的末尾：EMI 先把所有条目图标合批绘制（{@code batcher.draw()}），
 * 之后的文字才不会被图标覆盖；{@code startIndex} 是该块当前页的起始下标，叠加层据此
 * 还原槽位与条目的对应关系。
 */
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
