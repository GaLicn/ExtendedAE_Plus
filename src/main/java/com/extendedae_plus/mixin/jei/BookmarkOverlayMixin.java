package com.extendedae_plus.mixin.jei;

import com.extendedae_plus.client.jei.JeiNetworkOverlayButton;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
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

import java.util.Set;

@Pseudo
@Mixin(value = BookmarkOverlay.class, remap = false)
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

    @WrapMethod(method = "updateBounds")
    private void eap$updateNetworkOverlayButtonBounds(
            IGuiProperties guiProperties,
            Set<ImmutableRect2i> guiExclusionAreas,
            Operation<Void> original
    ) {
        // JEI 会复用 this 和参数槽位；包装调用可保留实例，并覆盖原方法的所有返回分支。
        original.call(guiProperties, guiExclusionAreas);
        // 书签与历史按钮各间隔 2px，新按钮继续排列在历史按钮右侧。
        this.eap$networkOverlayButton.updateBounds(this.historyButton.getArea().moveRight(22));
    }

    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void eap$beginNetworkOverlayButtonFrame(CallbackInfo ci) {
        // 每帧先清除可见状态，防止按钮在 JEI 不显示的界面响应旧坐标。
        this.eap$networkOverlayButton.beginFrame();
    }

    @Inject(
            method = "drawForeground",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/gui/elements/IconButton;draw(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void eap$drawNetworkOverlayButton(
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

}
