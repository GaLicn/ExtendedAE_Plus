package com.extendedae_plus.client.gui.widgets;

import appeng.client.gui.Icon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ScaledTextureButton extends Button {
    private final OnPress delegateOnPress;
    private final ResourceLocation texture;
    private final int textureWidth;
    private final int textureHeight;
    private final int srcX;
    private final int srcY;
    private final int srcWidth;
    private final int srcHeight;
    private final float scale;

    public ScaledTextureButton(ResourceLocation texture, int textureWidth, int textureHeight,
            int srcX, int srcY, int srcWidth, int srcHeight, float scale,
            Component tooltipText, OnPress onPress) {
        super(0, 0, Math.round(srcWidth * scale), Math.round(srcHeight * scale),
                Component.empty(), btn -> {
                }, DEFAULT_NARRATION);
        this.delegateOnPress = onPress;
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.srcX = srcX;
        this.srcY = srcY;
        this.srcWidth = srcWidth;
        this.srcHeight = srcHeight;
        this.scale = scale;
        if (tooltipText != null) {
            this.setTooltip(Tooltip.create(tooltipText));
        }
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    @Override
    public void onPress() {
        this.delegateOnPress.onPress(this);
        this.setFocused(false);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        this.width = Math.round(this.srcWidth * this.scale);
        this.height = Math.round(this.srcHeight * this.scale);

        int yOffset = this.isHovered() ? 1 : 0;
        Icon bgIcon = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(getX(), getY() + yOffset, 0.0F);
        pose.scale(scale, scale, 1.0F);

        bgIcon.getBlitter().dest(0, 0).zOffset(2).blit(guiGraphics);

        if (!this.active) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
        }
        guiGraphics.blit(this.texture, 0, 0, this.srcX, this.srcY, this.srcWidth, this.srcHeight,
                this.textureWidth, this.textureHeight);
        if (!this.active) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        pose.popPose();
        RenderSystem.enableDepthTest();
    }
}
