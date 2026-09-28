package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.util.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.network.RequestProvidersListC2SPacket;
import com.extendedae_plus.network.ReturnLastPatternC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在图样编码终端界面加入一个上传按钮：
 * 点击后把当前“已编码样板”上传到任意可用的样板供应器（服务端自动选择）。
 * 通过解析 AE2 样式中 encodePattern 的坐标，将按钮放在其左侧紧挨位置。
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class PatternEncodingTermScreenMixin<T extends AEBaseMenu> {

    @Unique
    private IconButton eap$uploadBtn;

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void eap$addUploadButton(CallbackInfo ci) {
        // 仅在图样编码终端界面中添加按钮
        if (!(((Object) this) instanceof PatternEncodingTermScreen)) {
            return;
        }
        // 复用已存在的按钮实例，避免重复创建
        if (eap$uploadBtn == null) {
            eap$uploadBtn = new IconButton(btn -> {
                if (Minecraft.getInstance().hasShiftDown()) {
                    ClientPacketDistributor.sendToServer(ReturnLastPatternC2SPacket.INSTANCE);
                } else {
                    ClientPacketDistributor.sendToServer(RequestProvidersListC2SPacket.INSTANCE);
                }
            }) {
                @Override
                protected Icon getIcon() {
                    return Icon.ARROW_UP;
                }

                @Override
                public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                        float partialTick) {
                    if (!this.visible) {
                        return;
                    }
                    float scale = 0.75f;
                    var pose = graphics.pose();
                    pose.pushMatrix();
                    pose.translate(getX(), getY());
                    pose.scale(scale, scale);
                    Blitter.icon(Icon.TOOLBAR_BUTTON_BACKGROUND).dest(0, 0).blit(graphics);
                    var icon = Blitter.icon(getIcon());
                    if (!this.active) {
                        icon.opacity(0.5f);
                    }
                    icon.dest(0, 0).blit(graphics);
                    pose.popMatrix();
                }
            };
            eap$uploadBtn.setWidth(12);
            eap$uploadBtn.setHeight(12);
            eap$updateUploadButtonTooltip();
        }

        // 解析 encodePattern 的样式位置
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
            int targetW = Math.max(10, Math.round(baseW * 0.75f));
            int targetH = Math.max(10, Math.round(baseH * 0.75f));
            // 缩小为原尺寸的 0.75（稍微变大于 8x8）
            eap$uploadBtn.setWidth(targetW);
            eap$uploadBtn.setHeight(targetH);
            // 仍位于其左侧，但整体向右微移（减小间距）约 2px
            eap$uploadBtn.setX(pos.getX() - baseW - 2); // 原为 -targetW - 2，再右移 2px
            eap$uploadBtn.setY(pos.getY());
        } catch (Throwable t) {
            // 回退：放在界面右侧大致位置，避免不可见
            eap$uploadBtn.setWidth(12);
            eap$uploadBtn.setHeight(12);
            int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
            int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
            int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
            eap$uploadBtn.setX(leftPos + imageWidth - 12 - 8 + 2); // 向右微移 2px
            eap$uploadBtn.setY(topPos + 88);
        }

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
        if (!(((Object) this) instanceof PatternEncodingTermScreen)) {
            return;
        }
        if (eap$uploadBtn == null) {
            return;
        }
        var renderables2 = ((ScreenAccessor) (Object) this).eap$getRenderables();
        if (!renderables2.contains(eap$uploadBtn)) {
            // 被其它模组清空/替换后，重新计算一次位置并补回
            try {
                ScreenStyle style = ((AEBaseScreenAccessor<?>) (Object) this).eap$getStyle();
                WidgetStyle ws = style.getWidget("encodePattern");
                int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
                int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
                int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
                int imageHeight = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageHeight();
                Rect2i bounds = new Rect2i(leftPos, topPos, imageWidth, imageHeight);
                var pos = ws.resolve(bounds);
                int baseW = ws.getWidth() > 0 ? ws.getWidth() : 16;
                int baseH = ws.getHeight() > 0 ? ws.getHeight() : 16;
                int targetW = Math.max(10, Math.round(baseW * 0.75f));
                int targetH = Math.max(10, Math.round(baseH * 0.75f));
                eap$uploadBtn.setWidth(targetW);
                eap$uploadBtn.setHeight(targetH);
                eap$uploadBtn.setX(pos.getX() - targetW); // 原为 -targetW - 2，再右移 2px
                eap$uploadBtn.setY(pos.getY());
            } catch (Throwable t) {
                int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
                int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
                int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
                eap$uploadBtn.setWidth(12);
                eap$uploadBtn.setHeight(12);
                eap$uploadBtn.setX(leftPos + imageWidth - 12 - 8 + 2);
                eap$uploadBtn.setY(topPos + 88);
            }
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

    @Unique
    private void eap$updateUploadButtonTooltip() {
        if (eap$uploadBtn == null) {
            return;
        }
        eap$uploadBtn.setTooltip(Tooltip.create(Component.translatable(
                Minecraft.getInstance().hasShiftDown()
                        ? "extendedae_plus.button.return_last_pattern"
                        : "extendedae_plus.button.choose_provider"
        )));
    }
}
