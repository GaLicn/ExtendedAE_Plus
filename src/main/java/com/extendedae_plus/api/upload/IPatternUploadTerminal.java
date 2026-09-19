package com.extendedae_plus.api.upload;

import net.minecraft.client.renderer.Rect2i;

/**
 * 第三方样板编码终端屏幕接入接口。
 * 实现后 EAEP 会自动在该屏幕注入「上传到供应器」按钮。
 */
public interface IPatternUploadTerminal {

    /**
     * 上传按钮的锚点（屏幕绝对坐标，含宽高）。
     * 返回 {@code null} 时用EAEP的定位方式，从Screen中寻找encodePattern按钮定位它后指定上传按钮位置。
     */
    Rect2i getUploadAnchor();

    /**
     * 上传按钮缩放（1.0 = 16x16）。返回 {@code <= 0} 时使用 EAEP 默认值 0.75。
     */
    default float getUploadScale() {
        return -1f;
    }
}
