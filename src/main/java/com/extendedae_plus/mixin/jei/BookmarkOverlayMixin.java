package com.extendedae_plus.mixin.jei;

import com.extendedae_plus.client.jei.JeiNetworkOverlayButton;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ScreenPropertiesCache;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin {
    @Shadow
    @Final
    private IngredientGridWithNavigation contents;

    @Shadow
    @Final
    private ScreenPropertiesCache screenPropertiesCache;

    @Unique
    private JeiNetworkOverlayButton eap$networkOverlayButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$createNetworkOverlayButton(CallbackInfo ci) {
        this.eap$networkOverlayButton = new JeiNetworkOverlayButton();
    }

    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void eap$updateNetworkOverlayButtonBounds(IGuiProperties guiProperties, CallbackInfo ci) {
        int bookmarkButtonX = this.contents.hasRoom() ? this.contents.getBackgroundArea().x() : 6;
        int buttonY = guiProperties.getScreenHeight() - 26;
        // 书签与历史按钮各间隔 2px，新按钮继续排列在历史按钮右侧。
        this.eap$networkOverlayButton.updateBounds(new ImmutableRect2i(bookmarkButtonX + 44, buttonY, 20, 20));
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void eap$beginNetworkOverlayButtonFrame(CallbackInfo ci) {
        // 每帧先清除可见状态，防止按钮在 JEI 不显示的界面响应旧坐标。
        this.eap$networkOverlayButton.beginFrame();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void eap$drawNetworkOverlayButton(
            Minecraft minecraft,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTicks,
            CallbackInfo ci
    ) {
        if (this.screenPropertiesCache.hasValidScreen()) {
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
        if (this.screenPropertiesCache.hasValidScreen()) {
            this.eap$networkOverlayButton.drawTooltip(guiGraphics, mouseX, mouseY);
        }
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
