package com.extendedae_plus.client.widget;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** AE2 文本框外观的可变高度输入框。 */
public class ResizableAETextField extends EditBox {
    private static final Blitter BLITTER = Blitter.texture("guis/text_field.png", 128, 128);
    private static final int PADDING = 2;
    private static final int EDGE_HEIGHT = 2;

    private final ScreenStyle style;
    private final Rect2i visualBounds;
    @Nullable
    private Component placeholder;

    public ResizableAETextField(ScreenStyle style, Font font, int x, int y, int width, int height) {
        super(font, x + PADDING, y + Math.max(PADDING, (height - font.lineHeight) / 2),
                width - 2 * PADDING - font.width("_"), font.lineHeight,
                Component.empty());
        this.style = style;
        this.visualBounds = new Rect2i(x, y, width, height);
        this.setBordered(false);
        this.setTextColor(style.getColor(PaletteColor.TEXTFIELD_TEXT).toARGB());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        Rect2i bounds = this.getVisualBounds();
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth()
                && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isMouseOver(mouseX, mouseY)) {
            mouseX = Mth.clamp(mouseX, this.getX(), this.getX() + this.width - 1);
            mouseY = Mth.clamp(mouseY, this.getY(), this.getY() + this.height - 1);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return this.isFocused() && this.canConsumeInput()
                && keyCode != GLFW.GLFW_KEY_TAB && keyCode != GLFW.GLFW_KEY_ESCAPE;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible()) {
            return;
        }

        int textureY = this.isFocused() ? 24 : 0;
        Rect2i bounds = this.getVisualBounds();
        this.renderBackground(graphics, bounds, textureY);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);

        if (this.placeholder != null && !this.isFocused() && this.getValue().isEmpty()) {
            graphics.drawString(Minecraft.getInstance().font, this.placeholder, this.getX(), this.getY(),
                    this.style.getColor(PaletteColor.TEXTFIELD_PLACEHOLDER).toARGB(), false);
        }
    }

    private void renderBackground(GuiGraphics graphics, Rect2i bounds, int textureY) {
        int centerWidth = Math.max(0, bounds.getWidth() - 2);
        int centerHeight = Math.max(0, bounds.getHeight() - EDGE_HEIGHT * 2);
        int sourceCenterWidth = Math.min(126, centerWidth);

        // 保留上下边框厚度，只拉伸纹理中段，使任意高度仍保持 AE2 风格。
        this.blit(graphics, 0, textureY, 1, EDGE_HEIGHT,
                bounds.getX(), bounds.getY(), 1, EDGE_HEIGHT);
        this.blit(graphics, 1, textureY, sourceCenterWidth, EDGE_HEIGHT,
                bounds.getX() + 1, bounds.getY(), centerWidth, EDGE_HEIGHT);
        this.blit(graphics, 127, textureY, 1, EDGE_HEIGHT,
                bounds.getX() + bounds.getWidth() - 1, bounds.getY(), 1, EDGE_HEIGHT);

        this.blit(graphics, 0, textureY + EDGE_HEIGHT, 1, 12 - EDGE_HEIGHT * 2,
                bounds.getX(), bounds.getY() + EDGE_HEIGHT, 1, centerHeight);
        this.blit(graphics, 1, textureY + EDGE_HEIGHT, sourceCenterWidth, 12 - EDGE_HEIGHT * 2,
                bounds.getX() + 1, bounds.getY() + EDGE_HEIGHT, centerWidth, centerHeight);
        this.blit(graphics, 127, textureY + EDGE_HEIGHT, 1, 12 - EDGE_HEIGHT * 2,
                bounds.getX() + bounds.getWidth() - 1, bounds.getY() + EDGE_HEIGHT, 1, centerHeight);

        int bottomY = bounds.getY() + bounds.getHeight() - EDGE_HEIGHT;
        this.blit(graphics, 0, textureY + 12 - EDGE_HEIGHT, 1, EDGE_HEIGHT,
                bounds.getX(), bottomY, 1, EDGE_HEIGHT);
        this.blit(graphics, 1, textureY + 12 - EDGE_HEIGHT, sourceCenterWidth, EDGE_HEIGHT,
                bounds.getX() + 1, bottomY, centerWidth, EDGE_HEIGHT);
        this.blit(graphics, 127, textureY + 12 - EDGE_HEIGHT, 1, EDGE_HEIGHT,
                bounds.getX() + bounds.getWidth() - 1, bottomY, 1, EDGE_HEIGHT);
    }

    private void blit(GuiGraphics graphics, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                      int x, int y, int width, int height) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        BLITTER.src(sourceX, sourceY, sourceWidth, sourceHeight)
                .dest(x, y, width, height)
                .blit(graphics);
    }

    public void selectAll() {
        this.moveCursorTo(0, false);
        this.setHighlightPos(this.getValue().length());
    }

    public Rect2i getTooltipArea() {
        return this.getVisualBounds();
    }

    public void setPlaceholder(@Nullable Component placeholder) {
        this.placeholder = placeholder;
    }

    private Rect2i getVisualBounds() {
        return this.visualBounds;
    }
}
