package com.extendedae_plus.api;

/**
 * 由 {@code GuiExPatternProviderMixin} 实现，用于从通用的 Screen Mixin 中更新按钮布局。
 */
public interface ExPatternButtonsAccessor {
    /**
     * 在每帧调用以维护扩展样板供应器右侧按钮的可见性、重注册（窗口尺寸变化）与定位。
     */
    void eap$updateButtonsLayout();
}
