package com.extendedae_plus.mixin.jei;

import com.extendedae_plus.client.jei.JeiNetworkOverlayButton;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", remap = false)
public abstract class BookmarkOverlayMixin {
    @Shadow
    @Final
    private IconButton historyButton;

    @Unique
    private JeiNetworkOverlayButton eap$networkOverlayButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$createNetworkOverlayButton(CallbackInfo ci) {
        this.eap$networkOverlayButton = new JeiNetworkOverlayButton();
    }

    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void eap$updateNetworkOverlayButtonBounds(CallbackInfo ci) {
        // 书签与历史按钮各间隔 2px，新按钮继续排列在历史按钮右侧。
        this.eap$networkOverlayButton.updateBounds(this.historyButton.getArea().moveRight(22));
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void eap$beginNetworkOverlayButtonFrame(CallbackInfo ci) {
        // 每帧先清除可见状态，防止按钮在 JEI 不显示的界面响应旧坐标。
        this.eap$networkOverlayButton.beginFrame();
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), require = 0)
    private void eap$beginModernNetworkOverlayButtonFrame(CallbackInfo ci) {
        // 新版 JEI 会拆分前景与背景绘制，背景入口同样作为一帧的起点。
        this.eap$networkOverlayButton.beginFrame();
    }

    @Inject(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/elements/IconButton;draw(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void eap$drawNetworkOverlayButtonLegacy(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTicks,
            CallbackInfo ci
    ) {
        this.eap$networkOverlayButton.draw(guiGraphics);
    }

    @Inject(
            method = "drawForeground",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/elements/IconButton;draw(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void eap$drawNetworkOverlayButtonModern(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTicks,
            CallbackInfo ci
    ) {
        this.eap$networkOverlayButton.draw(guiGraphics);
    }

    @Inject(
            method = "drawTooltips",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/elements/IconButton;drawTooltips(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void eap$drawNetworkOverlayButtonTooltip(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        this.eap$networkOverlayButton.drawTooltip(guiGraphics, mouseX, mouseY);
    }

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true)
    private void eap$appendNetworkOverlayButtonInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        cir.setReturnValue(new CombinedInputHandler(
                "ExtendedAEPlusNetworkOverlayButton",
                this.eap$networkOverlayButton,
                cir.getReturnValue()
        ));
    }
}
