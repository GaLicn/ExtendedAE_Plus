package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.Point;
import appeng.client.gui.layout.SlotGridLayout;
import com.extendedae_plus.api.IExPatternPage;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlotGridLayout.class)
public abstract class SlotGridLayoutMixin {
    @Inject(method = "getRowBreakPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetRowBreakPosition(int x, int y, int semanticIdx, int cols, CallbackInfoReturnable<Point> cir) {
        // 仅在 9 列布局 且 当前屏幕为 扩展样板供应器时处理
        if (cols != 9) return;

        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GuiExPatternProvider gui)) return;

        // 当前页
        int currentPage = (gui instanceof IExPatternPage accessor) ?
                accessor.eap$getCurrentPage() :
                0;

        // 该槽位属于第几页
        int slotPage = semanticIdx / 36;
        if (slotPage != currentPage) {
            // 非当前页：将其移出视野，避免渲染与鼠标命中
            cir.setReturnValue(new Point(-10000, -10000));
            cir.cancel();
            return;
        }

        // 当前页中的位置（0..35）
        int slotInPage = semanticIdx % 36;
        int row = slotInPage / 9;  // 0-3
        int col = slotInPage % 9;  // 0-8

        // 计算目标位置（始终在前4行）
        int targetX = x + col * 18;
        int targetY = y + row * 18;

        cir.setReturnValue(new Point(targetX, targetY));
        cir.cancel();
    }
}
 