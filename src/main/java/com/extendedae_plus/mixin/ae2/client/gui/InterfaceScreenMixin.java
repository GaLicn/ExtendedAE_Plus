package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.NewIcon;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 AE2 的 ME 接口界面注入倍增/除法按钮（x2/÷2、x5/÷5、x10/÷10）。
 * 点击逻辑暂时留空，仅完成按钮创建、注册与布局维护。
 */
@Mixin(AEBaseScreen.class)
public abstract class InterfaceScreenMixin<T extends AEBaseMenu> {

    @Unique private ActionEPPButton eap$x2Button;
    @Unique private ActionEPPButton eap$divideBy2Button;
    @Unique private ActionEPPButton eap$x5Button;
    @Unique private ActionEPPButton eap$divideBy5Button;
    @Unique private ActionEPPButton eap$x10Button;
    @Unique private ActionEPPButton eap$divideBy10Button;

    @Unique private int eap$lastLeftPos = -1;
    @Unique private int eap$lastTopPos = -1;
    @Unique private int eap$lastImageWidth = -1;
    @Unique private int eap$lastImageHeight = -1;

    @Inject(method = "init", at = @At("TAIL"))
    private void eap$addScaleButtons(CallbackInfo ci) {
        // 仅在 InterfaceScreen 实例中添加
        if (!(((Object) this) instanceof InterfaceScreen)) {
            return;
        }
        try { LogUtils.getLogger().info("[EAP][InterfaceMixin] init tail reached, preparing scale buttons."); } catch (Throwable ignored) {}
        // 避免重复创建
        if (eap$x2Button == null) {
            eap$x2Button = new ActionEPPButton((b) -> {
                // 点击逻辑留空
            }, NewIcon.MULTIPLY2);
            eap$x2Button.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.multiply2")));
            eap$x2Button.setVisibility(true);
        }
        if (eap$divideBy2Button == null) {
            eap$divideBy2Button = new ActionEPPButton((b) -> {
                // 点击逻辑留空
            }, NewIcon.DIVIDE2);
            eap$divideBy2Button.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.divide2")));
            eap$divideBy2Button.setVisibility(true);
        }
        if (eap$x5Button == null) {
            eap$x5Button = new ActionEPPButton((b) -> {
                // 点击逻辑留空
            }, NewIcon.MULTIPLY5);
            eap$x5Button.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.multiply5")));
            eap$x5Button.setVisibility(true);
        }
        if (eap$divideBy5Button == null) {
            eap$divideBy5Button = new ActionEPPButton((b) -> {
                // 点击逻辑留空
            }, NewIcon.DIVIDE5);
            eap$divideBy5Button.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.divide5")));
            eap$divideBy5Button.setVisibility(true);
        }
        if (eap$x10Button == null) {
            eap$x10Button = new ActionEPPButton((b) -> {
                // 点击逻辑留空
            }, NewIcon.MULTIPLY10);
            eap$x10Button.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.multiply10")));
            eap$x10Button.setVisibility(true);
        }
        if (eap$divideBy10Button == null) {
            eap$divideBy10Button = new ActionEPPButton((b) -> {
                // 点击逻辑留空
            }, NewIcon.DIVIDE10);
            eap$divideBy10Button.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.divide10")));
            eap$divideBy10Button.setVisibility(true);
        }

        // 注册到渲染与交互列表
        var accessor = (ScreenAccessor) (Object) this;
        if (!accessor.eap$getRenderables().contains(eap$divideBy2Button)) accessor.eap$getRenderables().add(eap$divideBy2Button);
        if (!accessor.eap$getChildren().contains(eap$divideBy2Button)) accessor.eap$getChildren().add(eap$divideBy2Button);
        if (!accessor.eap$getRenderables().contains(eap$x2Button)) accessor.eap$getRenderables().add(eap$x2Button);
        if (!accessor.eap$getChildren().contains(eap$x2Button)) accessor.eap$getChildren().add(eap$x2Button);
        if (!accessor.eap$getRenderables().contains(eap$divideBy5Button)) accessor.eap$getRenderables().add(eap$divideBy5Button);
        if (!accessor.eap$getChildren().contains(eap$divideBy5Button)) accessor.eap$getChildren().add(eap$divideBy5Button);
        if (!accessor.eap$getRenderables().contains(eap$x5Button)) accessor.eap$getRenderables().add(eap$x5Button);
        if (!accessor.eap$getChildren().contains(eap$x5Button)) accessor.eap$getChildren().add(eap$x5Button);
        if (!accessor.eap$getRenderables().contains(eap$divideBy10Button)) accessor.eap$getRenderables().add(eap$divideBy10Button);
        if (!accessor.eap$getChildren().contains(eap$divideBy10Button)) accessor.eap$getChildren().add(eap$divideBy10Button);
        if (!accessor.eap$getRenderables().contains(eap$x10Button)) accessor.eap$getRenderables().add(eap$x10Button);
        if (!accessor.eap$getChildren().contains(eap$x10Button)) accessor.eap$getChildren().add(eap$x10Button);

        // 初次定位
        eap$relayoutButtons();
        try { LogUtils.getLogger().info("[EAP][InterfaceMixin] buttons added and laid out."); } catch (Throwable ignored) {}
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void eap$ensureButtons(CallbackInfo ci) {
        if (!(((Object) this) instanceof InterfaceScreen)) {
            return;
        }
        var accessor = (ScreenAccessor) (Object) this;
        // 若被其他模组清空，补回
        if (eap$divideBy2Button != null && !accessor.eap$getRenderables().contains(eap$divideBy2Button)) {
            accessor.eap$getRenderables().add(eap$divideBy2Button);
            accessor.eap$getChildren().add(eap$divideBy2Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added divide2 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$x2Button != null && !accessor.eap$getRenderables().contains(eap$x2Button)) {
            accessor.eap$getRenderables().add(eap$x2Button);
            accessor.eap$getChildren().add(eap$x2Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added x2 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$divideBy5Button != null && !accessor.eap$getRenderables().contains(eap$divideBy5Button)) {
            accessor.eap$getRenderables().add(eap$divideBy5Button);
            accessor.eap$getChildren().add(eap$divideBy5Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added divide5 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$x5Button != null && !accessor.eap$getRenderables().contains(eap$x5Button)) {
            accessor.eap$getRenderables().add(eap$x5Button);
            accessor.eap$getChildren().add(eap$x5Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added x5 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$divideBy10Button != null && !accessor.eap$getRenderables().contains(eap$divideBy10Button)) {
            accessor.eap$getRenderables().add(eap$divideBy10Button);
            accessor.eap$getChildren().add(eap$divideBy10Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added divide10 button to renderables."); } catch (Throwable ignored) {}
        }
        if (eap$x10Button != null && !accessor.eap$getRenderables().contains(eap$x10Button)) {
            accessor.eap$getRenderables().add(eap$x10Button);
            accessor.eap$getChildren().add(eap$x10Button);
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] re-added x10 button to renderables."); } catch (Throwable ignored) {}
        }
        // 尺寸变化时重新定位
        int curLeft = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
        int curTop = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
        int curImgW = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
        int curImgH = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageHeight();
        if (curLeft != eap$lastLeftPos || curTop != eap$lastTopPos || curImgW != eap$lastImageWidth || curImgH != eap$lastImageHeight) {
            eap$lastLeftPos = curLeft;
            eap$lastTopPos = curTop;
            eap$lastImageWidth = curImgW;
            eap$lastImageHeight = curImgH;
            eap$relayoutButtons();
            try { LogUtils.getLogger().info("[EAP][InterfaceMixin] relayout due to bounds change: left={}, top={}, w={}, h={}", curLeft, curTop, curImgW, curImgH); } catch (Throwable ignored) {}
        }
    }

    @Unique
    private void eap$relayoutButtons() {
        try {
            int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
            int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
            int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
            // 按照样板供应器界面一致的布局：界面右缘外侧竖排
            int bx = leftPos + imageWidth + 1;
            int by = topPos + 20;
            int spacing = 22;
            if (eap$divideBy2Button != null) { eap$divideBy2Button.setX(bx); eap$divideBy2Button.setY(by); }
            if (eap$x2Button != null) { eap$x2Button.setX(bx); eap$x2Button.setY(by + spacing); }
            if (eap$divideBy5Button != null) { eap$divideBy5Button.setX(bx); eap$divideBy5Button.setY(by + spacing * 2); }
            if (eap$x5Button != null) { eap$x5Button.setX(bx); eap$x5Button.setY(by + spacing * 3); }
            if (eap$divideBy10Button != null) { eap$divideBy10Button.setX(bx); eap$divideBy10Button.setY(by + spacing * 4); }
            if (eap$x10Button != null) { eap$x10Button.setX(bx); eap$x10Button.setY(by + spacing * 5); }
        } catch (Throwable ignored) {}
    }
}
