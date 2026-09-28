package com.extendedae_plus.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ScaledTextureButton extends Button {
    private final OnPress delegateOnPress;
    private final Identifier texture;
    private final int textureWidth, textureHeight, srcX, srcY, srcWidth, srcHeight;
    private final float scale;

    public ScaledTextureButton(Identifier texture, int textureWidth, int textureHeight,
            int srcX, int srcY, int srcWidth, int srcHeight, float scale,
            Component tooltipText, OnPress onPress) {
        super(0, 0, Math.round(srcWidth * scale), Math.round(srcHeight * scale), Component.empty(),
                btn -> {}, DEFAULT_NARRATION);
        this.delegateOnPress = onPress;
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.srcX = srcX;
        this.srcY = srcY;
        this.srcWidth = srcWidth;
        this.srcHeight = srcHeight;
        this.scale = scale;
        if (tooltipText != null) setTooltip(Tooltip.create(tooltipText));
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        delegateOnPress.onPress(this);
        setFocused(false);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        graphics.blit(texture, getX(), getY(), srcX, srcY,
                Math.round(srcWidth * scale), Math.round(srcHeight * scale), textureWidth, textureHeight);
    }
}
