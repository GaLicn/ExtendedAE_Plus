package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.TextOverride;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.Text;
import appeng.menu.slot.AppEngSlot;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.content.ClientPatternHighlightStore;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.ScreenAccessor;
import com.extendedae_plus.network.crafting.CraftingMonitorJumpC2SPacket;
import com.extendedae_plus.network.crafting.CraftingMonitorOpenProviderC2SPacket;
import com.extendedae_plus.util.GuiUtil;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEBaseScreen.class)
public abstract class AEBaseScreenMixin {
    /**
     * 在 AEBaseScreen 的 mouseClicked 入口拦截 CraftingCPUScreen 的 Shift+点击操作。
     * 左键：发送 CraftingMonitorJumpC2SPacket（跳转至样板所在界面）。
     * 右键：发送 CraftingMonitorOpenProviderC2SPacket（打开样板供应器UI）。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void eap$craftingCpuShiftClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 仅处理 CraftingCPUScreen 实例
        Object self = this;
        if (!(self instanceof CraftingCPUScreen<?> screen)) {
            return;
        }

        // 仅在按下 Shift 且为左右键时触发
        if (!Screen.hasShiftDown() || (button != 0 && button != 1)) {
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

            // 左键发送跳转包，右键发送打开供应器包
            if (button == 0) {
                ModNetwork.CHANNEL.sendToServer(new CraftingMonitorJumpC2SPacket(key));
            } else {
                ModNetwork.CHANNEL.sendToServer(new CraftingMonitorOpenProviderC2SPacket(key));
            }

            cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 为所有可见的样板槽位添加数量显示
     */
    @Inject(method = "renderAppEngSlot", at = @At("TAIL"), remap = false)
    private void eap$renderSlotAmounts(GuiGraphics guiGraphics, AppEngSlot appEngSlot, CallbackInfo ci) {
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
        Font font = ((ScreenAccessor) this).eap$getFont();
        GuiUtil.drawAmountText(guiGraphics, font, amountText, appEngSlot.x, appEngSlot.y, 0.6f);

        try {
            var details = PatternDetailsHelper.decodePattern(itemStack, Minecraft.getInstance().level, false);
            if (details != null && details.getOutputs() != null && details.getOutputs().length > 0) {
                AEKey key = details.getOutputs()[0].what();
                if (key != null && ClientPatternHighlightStore.hasHighlight(key)) {
                    GuiUtil.drawSlotRainbowHighlight(guiGraphics, appEngSlot.x, appEngSlot.y);
                }
            }
        } catch (Throwable ignore) {}
    }

    // 在 AEBaseScreen.drawText 完成某个文本绘制后，若该文本为“样板”标签，则紧接着绘制页码。
    @Inject(method = "drawText", at = @At("TAIL"), remap = false)
    private void eap$appendPageAfterPatternsLabel(GuiGraphics guiGraphics,
                                                  Text text,
                                                  @Nullable TextOverride override,
                                                  CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof GuiExPatternProvider)) return;

        // 判断是否是“样板”标题
        Component content = text.getText();
        if (!"gui.ae2.Patterns".equals(content.getContents() instanceof TranslatableContents tc ? tc.getKey() : null)) {
            return;
        }

        try {
            // ---- 获取页码 ----
            int cur = 1;
            int max = 1;
            if (self instanceof IExPatternPage accessor) {
                cur = Math.max(0, accessor.eap$getCurrentPage()) + 1;
                max = Math.max(max, accessor.eap$getMaxPageLocal());
            }

            // ---- 构造翻译文本 ----
            Component pageText = Component.translatable("gui.extendedae.pattern_page", cur, max);

            // ---- 计算绘制坐标 ----
            AbstractContainerScreenAccessor<?> screen = (AbstractContainerScreenAccessor<?>) this;
            int imageWidth = screen.eap$getImageWidth();
            int imageHeight = screen.eap$getImageHeight();
            Point pos = text.getPosition().resolve(
                    new Rect2i(0, 0, imageWidth, imageHeight)
            );

            Font font = ((ScreenAccessor) this).eap$getFont();
            float scale = text.getScale();
            int lineWidth = font.width(content.getVisualOrderText());

            int x = pos.getX() + lineWidth + 4; // 右侧偏移4像素
            int y = pos.getY();

            // ---- 绘制 ----
            int color = 0xFFFFFFFF;
            ScreenStyle style = ((AEBaseScreenAccessor<?>) this).eap$getStyle();
            if (style != null) {
                color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
            }

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 1);
            if (scale != 1.0f) guiGraphics.pose().scale(scale, scale, 1);
            guiGraphics.drawString(font, pageText, 0, 0, color, false);
            guiGraphics.pose().popPose();
        } catch (Throwable ignored) {}
    }
}
