package com.extendedae_plus.mixin.extendedae.accessor;

/**
 * 用于访问 GuiExPatternTerminalMixin 中快速上传功能的接口
 */
public interface IGuiExPatternTerminalUploadAccessor {
    /**
     * 获取当前选择的样板供应器ID
     */
    long eap$getCurrentlyChoicePatternProvider();
    
    /**
     * 快速上传样板到当前选择的供应器
     */
    void eap$quickUploadPattern(int playerSlotIndex);
}

