package com.extendedae_plus.api;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 用于在 AEBaseScreen.drawBG 中渲染输入框背景的回调接口
 */
public interface IInputBackgroundRenderer {
    /**
     * 在背景层绘制输入框外部背景
     * @param guiGraphics 图形上下文
     */
    void eap$renderInputBackground(GuiGraphics guiGraphics);
}
