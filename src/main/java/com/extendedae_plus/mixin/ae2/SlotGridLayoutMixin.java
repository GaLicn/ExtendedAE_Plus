package com.extendedae_plus.mixin.ae2;

import appeng.client.Point;
import appeng.client.gui.layout.SlotGridLayout;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Field;

@Mixin(SlotGridLayout.class)
public abstract class SlotGridLayoutMixin {

    @Unique
    private static final int SLOTS_PER_PAGE = 36;

    @Inject(method = "getRowBreakPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetRowBreakPosition(int x, int y, int semanticIdx, int cols, CallbackInfoReturnable<Point> cir) {
        // 仅在 9 列布局 且 当前屏幕为 扩展样板供应器 时处理
        if (cols != 9) {
            return;
        }

        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof com.glodblock.github.extendedae.client.gui.GuiExPatternProvider)) {
            return;
        }

        // 读取实际当前页码：优先从 GUI accessor，其次反射容器，失败则为 0
        int currentPage = 0;
        try {
            if (screen instanceof com.extendedae_plus.api.ExPatternPageAccessor accessor) {
                currentPage = accessor.eap$getCurrentPage();
            } else {
                var menu = ((com.glodblock.github.extendedae.client.gui.GuiExPatternProvider) screen).getMenu();
                Field fieldPage = eap$findFieldRecursive(menu.getClass(), "page");
                if (fieldPage != null) {
                    fieldPage.setAccessible(true);
                    currentPage = (Integer) fieldPage.get(menu);
                }
            }
        } catch (Throwable ignored) {
        }

        // 该槽位属于第几页
        int slotPage = semanticIdx / SLOTS_PER_PAGE;
        if (slotPage != currentPage) {
            // 非当前页：将其移出视野，避免渲染与鼠标命中
            cir.setReturnValue(new Point(-10000, -10000));
            cir.cancel();
            return;
        }

        // 当前页中的位置（0..35）
        int slotInPage = semanticIdx % SLOTS_PER_PAGE;
        int row = slotInPage / 9;  // 0-3
        int col = slotInPage % 9;  // 0-8

        // 计算目标位置（始终在前4行）
        int targetX = x + col * 18;
        int targetY = y + row * 18;

        cir.setReturnValue(new Point(targetX, targetY));
        cir.cancel();
    }

    @Unique
    private static Field eap$findFieldRecursive(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }
}
 