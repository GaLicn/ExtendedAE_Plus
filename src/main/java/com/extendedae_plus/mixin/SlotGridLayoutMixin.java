package com.extendedae_plus.mixin;

import appeng.client.gui.layout.SlotGridLayout;
import appeng.client.Point;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlotGridLayout.class)
public abstract class SlotGridLayoutMixin {

    @Unique
    private static final int SLOTS_PER_PAGE = 36;

    @Inject(method = "getRowBreakPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetRowBreakPosition(int x, int y, int semanticIdx, int cols, CallbackInfoReturnable<Point> cir) {
        // 只处理BREAK_AFTER_9COLS布局
        if (cols != 9) {
            return;
        }

        // 计算当前页码
        int currentPage = semanticIdx / SLOTS_PER_PAGE;
        
        // 计算在当前页中的位置
        int slotInPage = semanticIdx % SLOTS_PER_PAGE;
        int row = slotInPage / 9;  // 0-3
        int col = slotInPage % 9;  // 0-8
        
        // 计算目标位置（始终在前4行）
        int targetX = x + col * 18;
        int targetY = y + row * 18;
        
        cir.setReturnValue(new Point(targetX, targetY));
    }
} 