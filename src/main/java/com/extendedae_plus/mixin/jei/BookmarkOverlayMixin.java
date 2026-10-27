package com.extendedae_plus.mixin.jei;

import com.extendedae_plus.client.jei.JeiNetworkOverlayButton;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.handlers.InputGroup;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.overlay.GuiPropertiesCache;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin {
    @Shadow
    @Final
    private IconButton historyButton;

    @Shadow
    @Final
    private GuiPropertiesCache<Screen> guiPropertiesCache;

    @Unique
    private JeiNetworkOverlayButton eap$networkOverlayButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$createNetworkOverlayButton(CallbackInfo ci) {
        this.eap$networkOverlayButton = new JeiNetworkOverlayButton();
    }

    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void eap$updateNetworkOverlayButtonBounds(
            IGuiProperties guiProperties,
            Set<ImmutableRect2i> guiExclusionAreas,
            CallbackInfo ci
    ) {
        // 跟随 JEI 实际布局，历史按钮没有空间显示时也隐藏新按钮。
        ImmutableRect2i historyArea = this.historyButton.getArea();
        this.eap$networkOverlayButton.updateBounds(historyArea.isEmpty()
                ? ImmutableRect2i.EMPTY
                : historyArea.moveRight(22));
    }

    @Inject(method = "drawForeground", at = @At("HEAD"))
    private void eap$beginNetworkOverlayButtonFrame(CallbackInfo ci) {
        // 每帧先清除可见状态，防止按钮在 JEI 不显示的界面响应旧坐标。
        this.eap$networkOverlayButton.beginFrame();
    }

    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void eap$drawNetworkOverlayButton(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTicks,
            CallbackInfo ci
    ) {
        if (this.guiPropertiesCache.hasValidScreen()) {
            this.eap$networkOverlayButton.draw(guiGraphics);
        }
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"))
    private void eap$drawNetworkOverlayButtonTooltip(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (this.guiPropertiesCache.hasValidScreen()) {
            this.eap$networkOverlayButton.drawTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true)
    private void eap$appendNetworkOverlayButtonInput(CallbackInfoReturnable<IInputTarget> cir) {
        cir.setReturnValue(new InputGroup(
                "ExtendedAEPlusNetworkOverlayButton",
                this.eap$networkOverlayButton,
                cir.getReturnValue()
        ));
    }

    @Inject(method = "containsMouse", at = @At("RETURN"), cancellable = true)
    private void eap$includeNetworkOverlayButtonArea(
            double mouseX,
            double mouseY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // 新版输入路由只向命中的区域分发事件，需要包含新增按钮。
        if (this.guiPropertiesCache.hasValidScreen()
                && this.eap$networkOverlayButton.containsMouse(mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }
}
