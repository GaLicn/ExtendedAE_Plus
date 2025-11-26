package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.ScreenAccessor;
import com.extendedae_plus.network.provider.RequestProvidersListC2SPacket;
import com.extendedae_plus.network.upload.EncodeWithShiftFlagC2SPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在图样编码终端界面加入一个上传按钮：
 * 点击后把当前“已编码样板”上传到任意可用的样板供应器。
 * 按钮位于 encodePattern 左侧。
 */
@SuppressWarnings({"AddedMixinMembersNamePattern"})
@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenMixin {

    @Unique private IconButton eap$uploadBtn;

    @ModifyVariable(method = "<init>", at = @At(value = "STORE"), name = "encodeBtn")
    private ActionButton eap$wrapEncodeButton(ActionButton original) {
        return new ActionButton(ActionItems.ENCODE, act -> {
            ModNetwork.CHANNEL.sendToServer(new EncodeWithShiftFlagC2SPacket(Screen.hasShiftDown()));
            var screen = (PatternEncodingTermScreen<?>) (Object) this;
            screen.getMenu().encode();
        });
    }

    // 只创建按钮
    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$createUploadButton(CallbackInfo ci) {
        if (eap$uploadBtn == null) {
            eap$uploadBtn = createUploadButton();
        }
        addButtonToScreen();
    }

    // 每帧更新按钮位置并确保加入屏幕
    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void eap$ensureUploadButton(CallbackInfo ci) {
        if (eap$uploadBtn == null) return;

        updateUploadButtonPosition();
        addButtonToScreen();
    }

    @Unique
    private IconButton createUploadButton() {
        IconButton btn = new IconButton(button ->
                ModNetwork.CHANNEL.sendToServer(new RequestProvidersListC2SPacket())
        ) {
            private final float eap$scale = 0.75f;

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
                if (!this.visible) return;

                var icon = this.getIcon();
                var blitter = icon.getBlitter();
                if (!this.active) blitter.opacity(0.5f);

                this.width = Math.round(16 * eap$scale);
                this.height = Math.round(16 * eap$scale);

                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();

                if (isFocused()) {
                    guiGraphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY(), 0xFFFFFFFF);
                    guiGraphics.fill(getX() - 1, getY(), getX(), getY() + height, 0xFFFFFFFF);
                    guiGraphics.fill(getX() + width, getY(), getX() + width + 1, getY() + height, 0xFFFFFFFF);
                    guiGraphics.fill(getX() - 1, getY() + height, getX() + width + 1, getY() + height + 1, 0xFFFFFFFF);
                }

                var pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(getX(), getY(), 0.0F);
                pose.scale(eap$scale, eap$scale, 1.f);
                if (!this.isDisableBackground()) {
                    Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(0, 0).blit(guiGraphics);
                }
                blitter.dest(0, 0).blit(guiGraphics);
                pose.popPose();

                RenderSystem.enableDepthTest();
            }

            @Override
            public Rect2i getTooltipArea() {
                return new Rect2i(getX(), getY(), Math.round(16 * eap$scale), Math.round(16 * eap$scale));
            }

            @Override
            protected Icon getIcon() {
                return Icon.ARROW_UP;
            }
        };
        btn.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.choose_provider")));
        return btn;
    }

    @Unique
    private void updateUploadButtonPosition() {
        if (eap$uploadBtn == null) return;

        AbstractContainerScreenAccessor<?> screen = (AbstractContainerScreenAccessor<?>) this;
        try {
            ScreenStyle style = ((AEBaseScreenAccessor<?>) this).eap$getStyle();
            WidgetStyle ws = style.getWidget("encodePattern");

            var bounds = new Rect2i(
                    screen.eap$getLeftPos(),
                    screen.eap$getTopPos(),
                    screen.eap$getImageWidth(),
                    screen.eap$getImageHeight()
            );

            var pos = ws.resolve(bounds);
            int baseW = ws.getWidth() > 0 ? ws.getWidth() : 16;
            int baseH = ws.getHeight() > 0 ? ws.getHeight() : 16;

            int targetW = Math.max(10, Math.round(baseW * 0.75f));
            int targetH = Math.max(10, Math.round(baseH * 0.75f));

            eap$uploadBtn.setWidth(targetW);
            eap$uploadBtn.setHeight(targetH);
            eap$uploadBtn.setX(pos.getX() - targetW);
            eap$uploadBtn.setY(pos.getY());
        } catch (Throwable t) {
            int leftPos = screen.eap$getLeftPos();
            int topPos = screen.eap$getTopPos();
            int imageWidth = screen.eap$getImageWidth();
            eap$uploadBtn.setWidth(12);
            eap$uploadBtn.setHeight(12);
            eap$uploadBtn.setX(leftPos + imageWidth - 14);
            eap$uploadBtn.setY(topPos + 88);
        }
    }

    @Unique
    private void addButtonToScreen() {
        var accessor = (ScreenAccessor) this;
        var renderables = accessor.eap$getRenderables();
        var children = accessor.eap$getChildren();
        if (!renderables.contains(eap$uploadBtn)) renderables.add(eap$uploadBtn);
        if (!children.contains(eap$uploadBtn)) children.add(eap$uploadBtn);
    }
}
