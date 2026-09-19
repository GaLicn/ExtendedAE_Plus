package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.api.upload.IPatternUploadTerminal;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.network.RequestProvidersListC2SPacket;
import com.extendedae_plus.network.ReturnLastPatternC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给实现了 {@link IPatternUploadTerminal} 的样板编码终端界面加入「上传到供应器」按钮。
 * 原生 AE2 终端与第三方终端均通过该接口接入。
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class PatternEncodingTermScreenMixin<T extends AEBaseMenu> {

    private static final Logger log = LoggerFactory.getLogger(PatternEncodingTermScreenMixin.class);
    @Unique
    private IconButton eap$uploadBtn;

    @Unique
    private float eap$btnScale = 0.75f;

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void eap$addUploadButton(CallbackInfo ci) {
        // 仅对实现上传终端契约的界面注入按钮（原生 + 第三方）
        if (!(((Object) this) instanceof IPatternUploadTerminal terminal)) {
            return;
        }
        this.eap$btnScale = terminal.getUploadScale() > 0 ? terminal.getUploadScale() : 0.75f;
        final float scale = this.eap$btnScale;
        if (eap$uploadBtn == null) {
            eap$uploadBtn = new IconButton(btn -> {
                if (Screen.hasShiftDown()) {
                    PacketDistributor.sendToServer(ReturnLastPatternC2SPacket.INSTANCE);
                } else {
                    PacketDistributor.sendToServer(RequestProvidersListC2SPacket.INSTANCE);
                }
            }) {
                @Override
                protected Icon getIcon() {
                    return Icon.ARROW_UP;
                }

                @Override
                public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
                    if (this.visible) {
                        var icon = this.getIcon();
                        var blitter = icon.getBlitter();
                        if (!this.active) {
                            blitter.opacity(0.5f);
                        }

                        this.width = Math.round(16 * scale);
                        this.height = Math.round(16 * scale);

                        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();

                        if (isFocused()) {
                            guiGraphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY(), 0xFFFFFFFF);
                            guiGraphics.fill(getX() - 1, getY(), getX(), getY() + height, 0xFFFFFFFF);
                            guiGraphics.fill(getX() + width, getY(), getX() + width + 1, getY() + height, 0xFFFFFFFF);
                            guiGraphics.fill(getX() - 1, getY() + height, getX() + width + 1, getY() + height + 1, 0xFFFFFFFF);
                        }

                        var pose = guiGraphics.pose();
                        pose.pushPose();
                        pose.translate(getX(), getY(), 0.0F);
                        pose.scale(scale, scale, 1.f);
                        if (!this.isDisableBackground()) {
                            Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(0, 0).blit(guiGraphics);
                        }
                        if (Screen.hasShiftDown()) {
                            pose.translate(16.0F, 16.0F, 0.0F);
                            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
                        }
                        blitter.dest(0, 0).blit(guiGraphics);
                        pose.popPose();

                        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                    }
                }

                @Override
                public Rect2i getTooltipArea() {
                    return new Rect2i(getX(), getY(), Math.round(16 * scale), Math.round(16 * scale));
                }
            };
            eap$updateUploadButtonTooltip();
        }

        // 优先使用接口提供的锚点，其次回退 encodePattern 样式
        eap$applyButtonBounds(terminal, this.eap$btnScale);

        // 直接向 renderables / children 列表添加，避免依赖受保护方法
        var accessor = (ScreenAccessor) (Object) this;
        var renderables = accessor.eap$getRenderables();
        var children = accessor.eap$getChildren();
        if (!renderables.contains(eap$uploadBtn)) {
            renderables.add(eap$uploadBtn);
        }
        if (!children.contains(eap$uploadBtn)) {
            children.add(eap$uploadBtn);
        }
        eap$updateUploadButtonTooltip();
    }

    @Inject(method = "containerTick", at = @At("TAIL"), remap = false)
    private void eap$ensureUploadButton(CallbackInfo ci) {
        if (!(((Object) this) instanceof IPatternUploadTerminal terminal)) {
            return;
        }
        if (eap$uploadBtn == null) {
            return;
        }
        var renderables2 = ((ScreenAccessor) (Object) this).eap$getRenderables();
        if (!renderables2.contains(eap$uploadBtn)) {
            // 被其它模组清空/替换后，重新计算一次位置并补回
            eap$applyButtonBounds(terminal, this.eap$btnScale);
            var accessor2 = (ScreenAccessor) (Object) this;
            var r = accessor2.eap$getRenderables();
            var c = accessor2.eap$getChildren();
            if (!r.contains(eap$uploadBtn)) {
                r.add(eap$uploadBtn);
            }
            if (!c.contains(eap$uploadBtn)) {
                c.add(eap$uploadBtn);
            }
        }
        eap$updateUploadButtonTooltip();
    }

    /**
     * 计算并应用按钮位置与大小：优先接口锚点，回退原样式。
     */
    @Unique
    private void eap$applyButtonBounds(IPatternUploadTerminal terminal, float scale) {
        if (eap$uploadBtn == null) {
            return;
        }
        // 接口显式锚点（位置 + 大小）
        Rect2i anchor = null;
        try {
            anchor = terminal.getUploadAnchor();
        } catch (Throwable ignored) {
        }
        if (anchor != null) {
            eap$uploadBtn.setX(anchor.getX());
            eap$uploadBtn.setY(anchor.getY());
            eap$uploadBtn.setWidth(anchor.getWidth() > 0 ? anchor.getWidth() : Math.round(16 * scale));
            eap$uploadBtn.setHeight(anchor.getHeight() > 0 ? anchor.getHeight() : Math.round(16 * scale));
            return;
        }
        // 原本的样式
        try {
            ScreenStyle style = ((AEBaseScreenAccessor<?>) (Object) this).eap$getStyle();
            WidgetStyle ws = style.getWidget("encodePattern");
            int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
            int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
            int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
            int imageHeight = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageHeight();
            Rect2i bounds = new Rect2i(leftPos, topPos, imageWidth, imageHeight);
            var pos = ws.resolve(bounds);
            int baseW = ws.getWidth() > 0 ? ws.getWidth() : 12;
            int baseH = ws.getHeight() > 0 ? ws.getHeight() : 12;
            int targetW = Math.max(10, Math.round(baseW * scale));
            int targetH = Math.max(10, Math.round(baseH * scale));
            eap$uploadBtn.setWidth(targetW);
            eap$uploadBtn.setHeight(targetH);
            eap$uploadBtn.setX(pos.getX() - baseW - 2);
            eap$uploadBtn.setY(pos.getY());
        } catch (Throwable t) {
            //没找到encodePattern按钮
            log.error(t.getMessage());
        }
    }

    @Unique
    private void eap$updateUploadButtonTooltip() {
        if (eap$uploadBtn == null) {
            return;
        }
        eap$uploadBtn.setTooltip(Tooltip.create(Component.translatable(
                Screen.hasShiftDown()
                        ? "extendedae_plus.button.return_last_pattern"
                        : "extendedae_plus.button.choose_provider"
        )));
    }
}
