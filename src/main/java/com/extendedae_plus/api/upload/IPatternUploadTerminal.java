package com.extendedae_plus.api.upload;

import net.minecraft.client.renderer.Rect2i;

/**
 * 第三方样板编码终端屏幕接入接口。
 * 实现后可由 EAEP 的通用屏幕注入逻辑添加上传按钮。
 */
public interface IPatternUploadTerminal {

    /** 上传按钮的屏幕绝对坐标；返回 null 时使用 AE2 encodePattern 样式定位。 */
    Rect2i getUploadAnchor();

    /** 上传按钮缩放，返回非正数时使用 0.75。 */
    default float getUploadScale() {
        return -1f;
    }
}
