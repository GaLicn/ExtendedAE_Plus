package com.extendedae_plus.api;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 用于更新输入框布局并在 AEBaseScreen.drawBG 中渲染背景的回调接口。
 */
public interface IInputBackgroundRenderer {
    /** 在组件布局更新后同步输入框位置与状态。 */
    void eap$updateInputLayout();

    /**
     * 在背景层绘制输入框外部背景
     * @param guiGraphics 图形上下文
     */
    void eap$renderInputBackground(GuiGraphics guiGraphics);
}
