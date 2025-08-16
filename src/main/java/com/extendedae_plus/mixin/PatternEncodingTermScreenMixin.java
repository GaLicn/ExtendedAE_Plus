package com.extendedae_plus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;

import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.mixin.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;

/**
 * 在图样编码终端界面加入一个上传按钮：
 * 点击后把当前“已编码样板”上传到任意可用的样板供应器（服务端自动选择）。
 * 通过解析 AE2 样式中 encodePattern 的坐标，将按钮放在其左侧紧挨位置。
 */
@Mixin(AEBaseScreen.class)
public abstract class PatternEncodingTermScreenMixin<T extends AEBaseMenu> {

    @Unique
    private IconButton extendedae_plus$uploadBtn;

    @Inject(method = "init", at = @At("TAIL"))
    private void extendedae_plus$addUploadButton(CallbackInfo ci) {
        // 仅在图样编码终端界面中添加按钮
        if (!(((Object) this) instanceof PatternEncodingTermScreen)) {
            return;
        }
        // 复用已存在的按钮实例，避免重复创建
        if (extendedae_plus$uploadBtn == null) {
            extendedae_plus$uploadBtn = new IconButton(btn -> ModNetwork.CHANNEL
                    .sendToServer(new com.extendedae_plus.network.RequestProvidersListC2SPacket())) {
            @Override
            protected Icon getIcon() {
                return Icon.ARROW_UP;
            }
            };
            extendedae_plus$uploadBtn.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.choose_provider")));
        }

        // 解析 encodePattern 的样式位置
        try {
            ScreenStyle style = ((AEBaseScreenAccessor<?>) (Object) this).extendedae_plus$getStyle();
            WidgetStyle ws = style.getWidget("encodePattern");
            int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getLeftPos();
            int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getTopPos();
            int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getImageWidth();
            int imageHeight = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getImageHeight();
            Rect2i bounds = new Rect2i(leftPos, topPos, imageWidth, imageHeight);
            var pos = ws.resolve(bounds);
            int targetW = ws.getWidth() > 0 ? ws.getWidth() : 18;
            int targetH = ws.getHeight() > 0 ? ws.getHeight() : 18;
            // 尺寸与 encodePattern 一致
            extendedae_plus$uploadBtn.setWidth(targetW);
            extendedae_plus$uploadBtn.setHeight(targetH);
            // 放在其左侧紧挨（预留 2px 间距）
            extendedae_plus$uploadBtn.setX(pos.getX() - targetW - 2);
            extendedae_plus$uploadBtn.setY(pos.getY());
        } catch (Throwable t) {
            // 回退：放在界面右侧大致位置，避免不可见
            extendedae_plus$uploadBtn.setWidth(18);
            extendedae_plus$uploadBtn.setHeight(18);
            int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getLeftPos();
            int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getTopPos();
            int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getImageWidth();
            extendedae_plus$uploadBtn.setX(leftPos + imageWidth - 18 - 8);
            extendedae_plus$uploadBtn.setY(topPos + 88);
        }

        // 直接向 renderables / children 列表添加，避免依赖受保护方法
        var accessor = (ScreenAccessor) (Object) this;
        var renderables = accessor.extendedae_plus$getRenderables();
        var children = accessor.extendedae_plus$getChildren();
        if (!renderables.contains(extendedae_plus$uploadBtn)) {
            renderables.add(extendedae_plus$uploadBtn);
        }
        if (!children.contains(extendedae_plus$uploadBtn)) {
            children.add(extendedae_plus$uploadBtn);
        }
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void extendedae_plus$ensureUploadButton(CallbackInfo ci) {
        if (!(((Object) this) instanceof PatternEncodingTermScreen)) {
            return;
        }
        if (extendedae_plus$uploadBtn == null) {
            return;
        }
        var renderables2 = ((ScreenAccessor) (Object) this).extendedae_plus$getRenderables();
        if (!renderables2.contains(extendedae_plus$uploadBtn)) {
            // 被其它模组清空/替换后，重新计算一次位置并补回
            try {
                ScreenStyle style = ((AEBaseScreenAccessor<?>) (Object) this).extendedae_plus$getStyle();
                WidgetStyle ws = style.getWidget("encodePattern");
                int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getLeftPos();
                int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getTopPos();
                int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getImageWidth();
                int imageHeight = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getImageHeight();
                Rect2i bounds = new Rect2i(leftPos, topPos, imageWidth, imageHeight);
                var pos = ws.resolve(bounds);
                int targetW = ws.getWidth() > 0 ? ws.getWidth() : 18;
                int targetH = ws.getHeight() > 0 ? ws.getHeight() : 18;
                extendedae_plus$uploadBtn.setWidth(targetW);
                extendedae_plus$uploadBtn.setHeight(targetH);
                extendedae_plus$uploadBtn.setX(pos.getX() - targetW - 2);
                extendedae_plus$uploadBtn.setY(pos.getY());
            } catch (Throwable t) {
                int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getLeftPos();
                int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getTopPos();
                int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).extendedae_plus$getImageWidth();
                extendedae_plus$uploadBtn.setWidth(18);
                extendedae_plus$uploadBtn.setHeight(18);
                extendedae_plus$uploadBtn.setX(leftPos + imageWidth - 18 - 8);
                extendedae_plus$uploadBtn.setY(topPos + 88);
            }
            var accessor2 = (ScreenAccessor) (Object) this;
            var r = accessor2.extendedae_plus$getRenderables();
            var c = accessor2.extendedae_plus$getChildren();
            if (!r.contains(extendedae_plus$uploadBtn)) {
                r.add(extendedae_plus$uploadBtn);
            }
            if (!c.contains(extendedae_plus$uploadBtn)) {
                c.add(extendedae_plus$uploadBtn);
            }
        }
    }
}
