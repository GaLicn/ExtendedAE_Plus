package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.TextOverride;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.Text;
import appeng.client.gui.style.TextAlignment;
import appeng.menu.slot.AppEngSlot;
import com.extendedae_plus.api.ExPatternPageAccessor;
import com.extendedae_plus.content.ClientPatternHighlightStore;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.CraftingMonitorJumpC2SPacket;
import com.extendedae_plus.network.CraftingMonitorOpenProviderC2SPacket;
import com.extendedae_plus.util.GuiUtil;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /**
     * 在 AEBaseScreen 的 mouseClicked 入口拦截 CraftingCPUScreen 的 Shift+左键，
     * 读取鼠标下的 AEKey 并发送 CraftingMonitorJumpC2SPacket。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void eap$craftingCpuShiftLeftClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 仅处理 CraftingCPUScreen 实例
        Object self = this;
        if (!(self instanceof CraftingCPUScreen<?> screen)) {
            return;
        }
        // 仅在 Shift + 左键 时触发
        if (button != 0 || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            return;
        }
        try {
            StackWithBounds hovered = screen.getStackUnderMouse(mouseX, mouseY);
            if (hovered == null || hovered.stack() == null) {
                return;
            }
            AEKey key = hovered.stack().what();
            if (key == null) {
                return;
            }
            // Debug: 标记一次发送
            try {
                LogUtils.getLogger().info("EAP: Send CraftingMonitorJumpC2SPacket: {}", key);
            } catch (Throwable ignored2) {}
            ModNetwork.CHANNEL.sendToServer(new CraftingMonitorJumpC2SPacket(key));
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 在 AEBaseScreen 的 mouseClicked 入口拦截 CraftingCPUScreen 的 Shift+右键，
     * 读取鼠标下的 AEKey 并发送 CraftingMonitorOpenProviderC2SPacket（打开样板供应器UI）。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void eap$craftingCpuShiftRightClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 仅处理 CraftingCPUScreen 实例
        Object self = this;
        if (!(self instanceof CraftingCPUScreen<?> screen)) {
            return;
        }
        // 仅在 Shift + 右键 时触发
        if (button != 1 || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            return;
        }
        try {
            StackWithBounds hovered = screen.getStackUnderMouse(mouseX, mouseY);
            if (hovered == null || hovered.stack() == null) {
                return;
            }
            AEKey key = hovered.stack().what();
            if (key == null) {
                return;
            }
            // Debug: 标记一次发送（打开供应器UI）
            try {
                LogUtils.getLogger().info("EAP: Send CraftingMonitorOpenProviderC2SPacket: {}", key);
            } catch (Throwable ignored2) {}
            ModNetwork.CHANNEL.sendToServer(new CraftingMonitorOpenProviderC2SPacket(key));
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
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

    /**
     * 重写renderSlot方法，为所有可见的样板槽位添加数量显示
     */
    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void eap$renderSlotAmounts(GuiGraphics guiGraphics, Slot s, CallbackInfo ci) {
        Object self = this;

        // 只处理AppEngSlot类型的槽位
        if (!(s instanceof AppEngSlot appEngSlot)) {
            return;
        }

        // 检查槽位是否可见且有效
        if (!appEngSlot.isActive() || !appEngSlot.isSlotEnabled()) {
            return;
        }

        // 获取槽位中的物品
        var itemStack = appEngSlot.getItem();
        if (itemStack.isEmpty()) {
            return;
        }

        // 使用GuiUtil的格式化方法获取数量文本
        String amountText = GuiUtil.getPatternOutputText(itemStack);
        if (amountText.isEmpty()) {
            return;
        }

        // 在槽位右下角绘制数量文本
        Font font = eap$getFont(self);
        GuiUtil.drawAmountText(guiGraphics, font, amountText, appEngSlot.x, appEngSlot.y, 0.6f);

        try {
            var details = PatternDetailsHelper.decodePattern(itemStack, Minecraft.getInstance().level, false);
            try {
                if (details != null && details.getOutputs() != null && details.getOutputs().length > 0) {
                    AEKey key = details.getOutputs()[0].what();
                    if (key != null && ClientPatternHighlightStore.hasHighlight(key)) {
                        try {
                            GuiUtil.drawSlotRainbowHighlight(guiGraphics, s.x, s.y);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignore) {}
        } catch (Throwable ignore) {}
    }

    // 在 AEBaseScreen.drawText 完成某个文本绘制后，若该文本为“样板”标签，则紧接着绘制页码。
    @Inject(method = "drawText", at = @At("TAIL"), remap = false)
    private void eap$appendPageAfterPatternsLabel(GuiGraphics guiGraphics,
                                                  Text text,
                                                  @Nullable TextOverride override,
                                                  CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof GuiExPatternProvider)) {
            return;
        }

        try {
            // 解析最终用于显示的标签内容
            Component content = text.getText();
            if (override != null && override.getContent() != null) {
                content = override.getContent().copy().withStyle(content.getStyle());
            }

            // 计算“样板”文本起点与宽度，按对齐方式与缩放修正 x/y
            int imageWidth = eap$getIntField(self, "imageWidth", 0);
            int imageHeight = eap$getIntField(self, "imageHeight", 0);
            Rect2i bounds = new Rect2i(0, 0, imageWidth, imageHeight);
            Point pos = text.getPosition().resolve(bounds);

            float scale = text.getScale();

            Font font = eap$getFont(self);
            // 只关心第一行（标题类文本无换行或 maxWidth<=0）
            var contentLine = (text.getMaxWidth() <= 0)
                    ? content.getVisualOrderText()
                    : font.split(content, text.getMaxWidth()).get(0);
            int lineWidth = font.width(contentLine);

            int x = pos.getX();
            int y = pos.getY();
            // 对齐修正
            var align = text.getAlign();
            if (align == TextAlignment.CENTER) {
                int textPx = Math.round(lineWidth * scale);
                x -= textPx / 2;
            } else if (align == TextAlignment.RIGHT) {
                int textPx = Math.round(lineWidth * scale);
                x -= textPx;
            }

            // 判断是否为“样板”组标题（多语言兼容且避免标题）
            boolean isPatterns = false;
            // 1) 基于翻译键
            var contents = content.getContents();
            if (contents instanceof TranslatableContents tc) {
                String key = tc.getKey();
                if (key != null && key.endsWith(".patterns")) {
                    isPatterns = true;
                }
            }
            // 2) 基于已知本地化键的字符串解析
            if (!isPatterns) {
                String label = content.getString();
                if (label != null) {
                    if (label.equals(Component.translatable("gui.pattern_provider.patterns").getString()))
                        isPatterns = true;
                    else if (label.equals(Component.translatable("gui.extendedae.patterns").getString()))
                        isPatterns = true;
                    else if (label.equals(Component.translatable("gui.ae2.patterns").getString())) isPatterns = true;
                }
            }
            // 3) 容错：中文“样板”且在标题下方（放宽到 y>=14）或文本正好等于“样板”
            if (!isPatterns) {
                String s = content.getString();
                if (s != null && ("样板".equals(s) || (s.contains("样板") && y >= 14))) {
                    isPatterns = true;
                }
            }
            if (!isPatterns) return;

            int cur = 1;
            int max = 1;
            if (self instanceof ExPatternPageAccessor accessor) {
                cur = Math.max(0, accessor.eap$getCurrentPage()) + 1;
            }
            try {
                var fMax = self.getClass().getDeclaredField("eap$maxPageLocal");
                fMax.setAccessible(true);
                Object v = fMax.get(self);
                if (v instanceof Integer i) {
                    max = Math.max(1, i);
                }
            } catch (Throwable ignored) {
            }

            String pageText = "第" + cur + "页" + "/" + max + "页";

            ScreenStyle style = eap$getStyle(self);
            int color = 0xFFFFFFFF;
            if (style != null) {
                try {
                    color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
                } catch (Throwable ignored) {
                }
            }
            int padding = 4;
            if (scale == 1.0f) {
                guiGraphics.drawString(font, pageText, x + lineWidth + padding, y, color, false);
            } else {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(x, y, 1);
                guiGraphics.pose().scale(scale, scale, 1);
                guiGraphics.drawString(font, pageText, lineWidth + padding, 0, color, false);
                guiGraphics.pose().popPose();
            }
        } catch (Throwable ignored) {
        }
    }


    @Shadow(remap = false)
    protected void setTextContent(String id, Component content) {};

    @Inject(method = "updateBeforeRender", at = @At("RETURN"), remap = false)
    private void onUpdateBeforeRender(CallbackInfo ci) {
        try {
            AEBaseScreen<?> self = (AEBaseScreen<?>) (Object) this;
            if (self instanceof PatternProviderScreen screen){
                Component t = screen.getTitle();
                if (t != null && !t.getString().isEmpty()) {
                    this.setTextContent(AEBaseScreen.TEXT_ID_DIALOG_TITLE, t);
                }
            }
        } catch (Throwable ignored) {}
    }
}
