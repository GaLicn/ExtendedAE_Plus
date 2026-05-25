package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.items.EncodingModePanel;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.me.items.ProcessingEncodingPanel;
import appeng.client.gui.widgets.ActionButton;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.screen.ProviderSelectScreen;
import com.extendedae_plus.client.gui.widgets.ScaledTextureButton;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.minecraft.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.ScreenAccessor;
import com.extendedae_plus.network.ScaleEncodingPatternC2SPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings({ "AddedMixinMembersNamePattern" })
@Mixin(value = ProcessingEncodingPanel.class, remap = false)
public abstract class ProcessingEncodingPanelMixin extends EncodingModePanel {
    @Unique
    private static final ResourceLocation EAP$SCALE_BUTTON_TEXTURE =
            new ResourceLocation(ExtendedAEPlus.MODID, "textures/gui/beizeng.png");
    @Unique
    private static final ResourceLocation EAP$SWAP_OUTPUT_TEXTURE =
            new ResourceLocation(ExtendedAEPlus.MODID, "textures/gui/zhu_fu_qie_huan.png");
    @Unique
    private static final ResourceLocation EAP$RESTORE_RATIO_TEXTURE =
            new ResourceLocation(ExtendedAEPlus.MODID, "textures/gui/huanyuan.png");

    @Unique
    private static final int EAP$SWAP_OUTPUT_LEFT = 126;
    @Unique
    private static final int EAP$SWAP_OUTPUT_BOTTOM = 159;
    @Unique
    private static final int EAP$RESTORE_RATIO_LEFT = 100;
    @Unique
    private static final int EAP$RESTORE_RATIO_BOTTOM = 159;
    @Unique
    private static final int EAP$MUL2_LEFT = 126;
    @Unique
    private static final int EAP$MUL2_BOTTOM = 149;
    @Unique
    private static final int EAP$MUL3_LEFT = 126;
    @Unique
    private static final int EAP$MUL3_BOTTOM = 138;
    @Unique
    private static final int EAP$MUL5_LEFT = 126;
    @Unique
    private static final int EAP$MUL5_BOTTOM = 127;
    @Unique
    private static final int EAP$DIV2_LEFT = 100;
    @Unique
    private static final int EAP$DIV2_BOTTOM = 149;
    @Unique
    private static final int EAP$DIV3_LEFT = 100;
    @Unique
    private static final int EAP$DIV3_BOTTOM = 138;
    @Unique
    private static final int EAP$DIV5_LEFT = 100;
    @Unique
    private static final int EAP$DIV5_BOTTOM = 127;

    @Shadow
    @Final
    private ActionButton cycleOutputBtn;

    @Unique private ScaledTextureButton eap$mul2Button;
    @Unique private ScaledTextureButton eap$mul3Button;
    @Unique private ScaledTextureButton eap$mul5Button;
    @Unique private ScaledTextureButton eap$div2Button;
    @Unique private ScaledTextureButton eap$div3Button;
    @Unique private ScaledTextureButton eap$div5Button;
    @Unique private ScaledTextureButton eap$swapOutputsButton;
    @Unique private ScaledTextureButton eap$restoreRatioButton;

    protected ProcessingEncodingPanelMixin(PatternEncodingTermScreen<?> screen, WidgetContainer widgets) {
        super(screen, widgets);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void eap$initButtons(PatternEncodingTermScreen<?> screen, WidgetContainer widgets, CallbackInfo ci) {
        if (this.eap$mul2Button != null) {
            return;
        }

        this.eap$mul2Button = eap$createScaleButton(0, 0, "x2", ScaleEncodingPatternC2SPacket.Operation.MUL2);
        this.eap$mul3Button = eap$createScaleButton(16, 0, "x3", ScaleEncodingPatternC2SPacket.Operation.MUL3);
        this.eap$mul5Button = eap$createScaleButton(32, 0, "x5", ScaleEncodingPatternC2SPacket.Operation.MUL5);
        this.eap$div2Button = eap$createScaleButton(0, 16, "/2", ScaleEncodingPatternC2SPacket.Operation.DIV2);
        this.eap$div3Button = eap$createScaleButton(16, 16, "/3", ScaleEncodingPatternC2SPacket.Operation.DIV3);
        this.eap$div5Button = eap$createScaleButton(32, 16, "/5", ScaleEncodingPatternC2SPacket.Operation.DIV5);
        this.eap$swapOutputsButton = eap$createStandaloneButton(
                EAP$SWAP_OUTPUT_TEXTURE,
                Component.translatable("extendedae_plus.tooltip.swap_processing_outputs"),
                ScaleEncodingPatternC2SPacket.Operation.SWAP_OUTPUTS);
        this.eap$restoreRatioButton = eap$createStandaloneButton(
                EAP$RESTORE_RATIO_TEXTURE,
                Component.translatable("extendedae_plus.tooltip.restore_processing_ratio"),
                ScaleEncodingPatternC2SPacket.Operation.RESTORE_RATIO);

        this.eap$mul2Button.setVisibility(false);
        this.eap$mul3Button.setVisibility(false);
        this.eap$mul5Button.setVisibility(false);
        this.eap$div2Button.setVisibility(false);
        this.eap$div3Button.setVisibility(false);
        this.eap$div5Button.setVisibility(false);
        this.eap$swapOutputsButton.setVisibility(false);
        this.eap$restoreRatioButton.setVisibility(false);

        eap$ensureAdded(this.eap$mul2Button);
        eap$ensureAdded(this.eap$mul3Button);
        eap$ensureAdded(this.eap$mul5Button);
        eap$ensureAdded(this.eap$div2Button);
        eap$ensureAdded(this.eap$div3Button);
        eap$ensureAdded(this.eap$div5Button);
        eap$ensureAdded(this.eap$swapOutputsButton);
        eap$ensureAdded(this.eap$restoreRatioButton);
    }

    @Inject(method = "setVisible", at = @At("TAIL"), remap = false)
    private void eap$updateInjectedButtons(boolean visible, CallbackInfo ci) {
        boolean showInjectedButtons = visible && ProviderSelectScreen.isProcessingButtonsEnabled();
        this.cycleOutputBtn.setVisibility(visible && !showInjectedButtons);

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

        this.eap$mul2Button.setVisibility(showInjectedButtons);
        this.eap$mul3Button.setVisibility(showInjectedButtons);
        this.eap$mul5Button.setVisibility(showInjectedButtons);
        this.eap$div2Button.setVisibility(showInjectedButtons);
        this.eap$div3Button.setVisibility(showInjectedButtons);
        this.eap$div5Button.setVisibility(showInjectedButtons);
        this.eap$swapOutputsButton.setVisibility(showInjectedButtons);
        this.eap$restoreRatioButton.setVisibility(showInjectedButtons);

        if (!showInjectedButtons) {
            return;
        }

        eap$placeButton(this.eap$swapOutputsButton, EAP$SWAP_OUTPUT_LEFT, EAP$SWAP_OUTPUT_BOTTOM);
        eap$placeButton(this.eap$restoreRatioButton, EAP$RESTORE_RATIO_LEFT, EAP$RESTORE_RATIO_BOTTOM);
        eap$placeButton(this.eap$mul2Button, EAP$MUL2_LEFT, EAP$MUL2_BOTTOM);
        eap$placeButton(this.eap$mul3Button, EAP$MUL3_LEFT, EAP$MUL3_BOTTOM);
        eap$placeButton(this.eap$mul5Button, EAP$MUL5_LEFT, EAP$MUL5_BOTTOM);
        eap$placeButton(this.eap$div2Button, EAP$DIV2_LEFT, EAP$DIV2_BOTTOM);
        eap$placeButton(this.eap$div3Button, EAP$DIV3_LEFT, EAP$DIV3_BOTTOM);
        eap$placeButton(this.eap$div5Button, EAP$DIV5_LEFT, EAP$DIV5_BOTTOM);
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
                0.5f,
                Component.literal(tooltipText),
                btn -> ModNetwork.CHANNEL.sendToServer(new ScaleEncodingPatternC2SPacket(op)));
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
                0.5f,
                tooltipText,
                btn -> ModNetwork.CHANNEL.sendToServer(new ScaleEncodingPatternC2SPacket(op)));
    }

    @Unique
    private void eap$ensureAdded(ScaledTextureButton button) {
        var accessor = (ScreenAccessor) (Object) this.screen;
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
    private void eap$placeButton(ScaledTextureButton button, int left, int bottom) {
        int leftPos = ((AbstractContainerScreenAccessor<?>) (Object) this.screen).eap$getLeftPos();
        int topPos = ((AbstractContainerScreenAccessor<?>) (Object) this.screen).eap$getTopPos();
        int imageHeight = ((AbstractContainerScreenAccessor<?>) (Object) this.screen).eap$getImageHeight();
        button.setX(leftPos + left + 1);
        button.setY(topPos + imageHeight - bottom);
    }
}
