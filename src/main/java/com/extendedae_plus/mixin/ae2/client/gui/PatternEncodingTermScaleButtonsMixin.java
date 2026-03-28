package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.menu.AEBaseMenu;
import appeng.parts.encoding.EncodingMode;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.gui.widgets.ScaledTextureButton;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.network.ScaleEncodingPatternC2SPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class PatternEncodingTermScaleButtonsMixin<T extends AEBaseMenu> {
    @Unique
    private static final ResourceLocation EAP$SCALE_BUTTON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "textures/gui/beizeng.png");
    @Unique
    private static final ResourceLocation EAP$SWAP_OUTPUT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "textures/gui/zhu_fu_qie_huan.png");
    @Unique
    private static final ResourceLocation EAP$RESTORE_RATIO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "textures/gui/huanyuan.png");

    @Unique
    private ScaledTextureButton eap$mul2Button;
    @Unique
    private ScaledTextureButton eap$mul3Button;
    @Unique
    private ScaledTextureButton eap$mul5Button;
    @Unique
    private ScaledTextureButton eap$div2Button;
    @Unique
    private ScaledTextureButton eap$div3Button;
    @Unique
    private ScaledTextureButton eap$div5Button;
    @Unique
    private ScaledTextureButton eap$swapOutputsButton;
    @Unique
    private ScaledTextureButton eap$restoreRatioButton;

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void eap$initScaleButtons(CallbackInfo ci) {
        if (!(((Object) this) instanceof PatternEncodingTermScreen<?>)) {
            return;
        }

        if (this.eap$mul2Button == null) {
            this.eap$mul2Button = eap$createScaleButton(0, 0, "x2",
                    ScaleEncodingPatternC2SPacket.Operation.MUL2);
            this.eap$mul3Button = eap$createScaleButton(16, 0, "x3",
                    ScaleEncodingPatternC2SPacket.Operation.MUL3);
            this.eap$mul5Button = eap$createScaleButton(32, 0, "x5",
                    ScaleEncodingPatternC2SPacket.Operation.MUL5);
            this.eap$div2Button = eap$createScaleButton(0, 16, "/2",
                    ScaleEncodingPatternC2SPacket.Operation.DIV2);
            this.eap$div3Button = eap$createScaleButton(16, 16, "/3",
                    ScaleEncodingPatternC2SPacket.Operation.DIV3);
            this.eap$div5Button = eap$createScaleButton(32, 16, "/5",
                    ScaleEncodingPatternC2SPacket.Operation.DIV5);
            this.eap$swapOutputsButton = eap$createStandaloneButton(
                    EAP$SWAP_OUTPUT_TEXTURE,
                    Component.translatable("extendedae_plus.tooltip.swap_processing_outputs"),
                    ScaleEncodingPatternC2SPacket.Operation.SWAP_OUTPUTS
            );
            this.eap$restoreRatioButton = eap$createStandaloneButton(
                    EAP$RESTORE_RATIO_TEXTURE,
                    Component.translatable("extendedae_plus.tooltip.restore_processing_ratio"),
                    ScaleEncodingPatternC2SPacket.Operation.RESTORE_RATIO
            );
        }

        eap$ensureAdded(this.eap$mul2Button);
        eap$ensureAdded(this.eap$mul3Button);
        eap$ensureAdded(this.eap$mul5Button);
        eap$ensureAdded(this.eap$div2Button);
        eap$ensureAdded(this.eap$div3Button);
        eap$ensureAdded(this.eap$div5Button);
        eap$ensureAdded(this.eap$swapOutputsButton);
        eap$ensureAdded(this.eap$restoreRatioButton);
    }

    @Inject(method = "containerTick", at = @At("TAIL"), remap = false)
    private void eap$updateScaleButtons(CallbackInfo ci) {
        if (!(((Object) this) instanceof PatternEncodingTermScreen<?> screen)) {
            return;
        }
        if (this.eap$mul2Button == null) {
            return;
        }

        eap$ensureAdded(this.eap$mul2Button);
        eap$ensureAdded(this.eap$mul3Button);
        eap$ensureAdded(this.eap$mul5Button);
        eap$ensureAdded(this.eap$div2Button);
        eap$ensureAdded(this.eap$div3Button);
        eap$ensureAdded(this.eap$div5Button);
        eap$ensureAdded(this.eap$swapOutputsButton);
        eap$ensureAdded(this.eap$restoreRatioButton);

        boolean visible = screen.getMenu().getMode() == EncodingMode.PROCESSING;
        this.eap$mul2Button.setVisibility(visible);
        this.eap$mul3Button.setVisibility(visible);
        this.eap$mul5Button.setVisibility(visible);
        this.eap$div2Button.setVisibility(visible);
        this.eap$div3Button.setVisibility(visible);
        this.eap$div5Button.setVisibility(visible);
        this.eap$swapOutputsButton.setVisibility(visible);
        this.eap$restoreRatioButton.setVisibility(visible);

        if (!visible) {
            return;
        }

        Rect2i bounds = eap$getScreenBounds();
        if (bounds == null) {
            return;
        }
        eap$placeButton(this.eap$div2Button, "chu_2", bounds);
        eap$placeButton(this.eap$div3Button, "chu_3", bounds);
        eap$placeButton(this.eap$div5Button, "chu_5", bounds);
        eap$placeButton(this.eap$mul2Button, "cheng_2", bounds);
        eap$placeButton(this.eap$mul3Button, "cheng_3", bounds);
        eap$placeButton(this.eap$mul5Button, "cheng_5", bounds);
        eap$placeButton(this.eap$swapOutputsButton, "zhu_fu_qie_huan", bounds);
        eap$placeButton(this.eap$restoreRatioButton, "huan_yuan_mo_ren", bounds);
    }

    @Unique
    private ScaledTextureButton eap$createScaleButton(int srcX, int srcY, String tooltipText,
            ScaleEncodingPatternC2SPacket.Operation op) {
        return new ScaledTextureButton(
                EAP$SCALE_BUTTON_TEXTURE,
                48,
                32,
                srcX,
                srcY,
                16,
                16,
                0.375f,
                Component.literal(tooltipText),
                btn -> PacketDistributor.sendToServer(new ScaleEncodingPatternC2SPacket(op))
        );
    }

    @Unique
    private ScaledTextureButton eap$createStandaloneButton(ResourceLocation texture, Component tooltipText,
            ScaleEncodingPatternC2SPacket.Operation op) {
        return new ScaledTextureButton(
                texture,
                16,
                16,
                0,
                0,
                16,
                16,
                0.375f,
                tooltipText,
                btn -> PacketDistributor.sendToServer(new ScaleEncodingPatternC2SPacket(op))
        );
    }

    @Unique
    private void eap$ensureAdded(ScaledTextureButton button) {
        var accessor = (ScreenAccessor) (Object) this;
        var renderables = accessor.eap$getRenderables();
        var children = accessor.eap$getChildren();
        if (!renderables.contains(button)) {
            renderables.add(button);
        }
        if (!children.contains(button)) {
            children.add(button);
        }
    }

    @Unique
    private Rect2i eap$getScreenBounds() {
        int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getLeftPos();
        int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getTopPos();
        int imageWidth = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageWidth();
        int imageHeight = ((AbstractContainerScreenAccessor<?>) (Object) this).eap$getImageHeight();
        return new Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    @Unique
    private void eap$placeButton(ScaledTextureButton button, String widgetId, Rect2i bounds) {
        ScreenStyle style = ((AEBaseScreenAccessor<?>) (Object) this).eap$getStyle();
        WidgetStyle widgetStyle = style.getWidget(widgetId);
        var pos = widgetStyle.resolve(bounds);
        button.setX(pos.getX());
        button.setY(pos.getY());
    }
}
