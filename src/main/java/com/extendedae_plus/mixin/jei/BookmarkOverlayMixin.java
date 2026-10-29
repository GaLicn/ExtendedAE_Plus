package com.extendedae_plus.mixin.jei;

import com.extendedae_plus.client.jei.JeiNetworkOverlayButton;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

    @WrapMethod(method = "updateBounds")
    private void eap$updateNetworkOverlayButtonBounds(
            IGuiProperties guiProperties,
            Set<ImmutableRect2i> guiExclusionAreas,
            Operation<Void> original
    ) {
        // JEI 优化后的布局方法也会复用 this 槽，包装后再访问按钮实例。
        original.call(guiProperties, guiExclusionAreas);
        // 跟随 JEI 实际布局，历史按钮没有空间显示时也隐藏新按钮。
        ImmutableRect2i historyArea = this.historyButton.getArea();
        this.eap$networkOverlayButton.updateBounds(historyArea.isEmpty()
                ? ImmutableRect2i.EMPTY
                : historyArea.moveRight(22));
    }

    @WrapMethod(method = "drawForeground")
    private void eap$drawNetworkOverlayButton(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTicks,
            Operation<Void> original
    ) {
        // JEI 在有效界面分支提前返回，包装调用可保证原绘制结束后仍绘制新增按钮。
        this.eap$networkOverlayButton.beginFrame();
        original.call(minecraft, guiGraphics, mouseX, mouseY, partialTicks);
        if (this.guiPropertiesCache.hasValidScreen()) {
            this.eap$networkOverlayButton.draw(guiGraphics);
        }
    }

    @WrapMethod(method = "drawTooltips")
    private void eap$drawNetworkOverlayButtonTooltip(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            Operation<Void> original
    ) {
        original.call(minecraft, guiGraphics, mouseX, mouseY);
        if (this.guiPropertiesCache.hasValidScreen()) {
            this.eap$networkOverlayButton.drawTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @WrapMethod(method = "createInputHandler")
    private IInputTarget eap$appendNetworkOverlayButtonInput(Operation<IInputTarget> original) {
        return new InputGroup(
                "ExtendedAEPlusNetworkOverlayButton",
                this.eap$networkOverlayButton,
                original.call()
        );
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
