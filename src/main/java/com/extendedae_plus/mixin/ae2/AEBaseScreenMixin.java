package com.extendedae_plus.mixin.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import com.extendedae_plus.api.ExPatternPageAccessor;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseScreen.class)
public abstract class AEBaseScreenMixin {

    @Unique
    private ScreenStyle eap$getStyle(Object self) {
        try {
            var f = self.getClass().getDeclaredField("style");
            f.setAccessible(true);
            Object v = f.get(self);
            if (v instanceof ScreenStyle s) return s;
        } catch (Throwable ignored) {}
        return null;
    }

    @Unique
    private static int eap$getIntField(Object self, String name, int def) {
        Class<?> c = self.getClass();
        while (c != null && c != Object.class) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(self);
                if (v instanceof Integer i) return i;
            } catch (Throwable ignored) {}
            c = c.getSuperclass();
        }
        return def;
    }

    @Unique
    private static Font eap$getFont(Object self) {
        Class<?> c = self.getClass();
        while (c != null && c != Object.class) {
            try {
                var f = c.getDeclaredField("font");
                f.setAccessible(true);
                Object v = f.get(self);
                if (v instanceof Font font) return font;
            } catch (Throwable ignored) {}
            c = c.getSuperclass();
        }
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    // 在标签绘制后追加页码绘制，仅限扩展样板供应器界面
    @Inject(method = "renderLabels", at = @At("TAIL"), remap = false)
    private void eap$renderPageIndicator(GuiGraphics guiGraphics, int x, int y, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof GuiExPatternProvider)) {
            return;
        }
        int cur = 1;
        int max = 1;
        try {
            if (self instanceof ExPatternPageAccessor accessor) {
                cur = Math.max(0, accessor.eap$getCurrentPage()) + 1;
            }
            // 读取最大页（从 GUI 本地字段）
            try {
                var fMax = self.getClass().getDeclaredField("eap$maxPageLocal");
                fMax.setAccessible(true);
                Object v = fMax.get(self);
                if (v instanceof Integer i) {
                    max = Math.max(1, i);
                }
            } catch (Throwable ignored) {}

            ScreenStyle style = eap$getStyle(self);
            Font font = eap$getFont(self);
            int leftPos = eap$getIntField(self, "leftPos", 0);
            int topPos = eap$getIntField(self, "topPos", 0);
            int imageWidth = eap$getIntField(self, "imageWidth", 0);

            String text = cur + "/" + max;
            int tx = leftPos + imageWidth - font.width(text) - 6;
            int ty = topPos + 6;
            int color = 0xFFFFFFFF;
            if (style != null) {
                try {
                    color = style.getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB();
                } catch (Throwable ignored) {}
            }
            guiGraphics.drawString(font, text, tx, ty, color, false);
        } catch (Throwable ignored) {
        }
    }
}
