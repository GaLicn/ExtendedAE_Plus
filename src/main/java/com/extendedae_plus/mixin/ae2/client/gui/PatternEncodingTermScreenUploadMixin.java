package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import com.extendedae_plus.api.upload.IPatternUploadTerminal;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 样板编码终端屏幕实现上传终端接口，获取 EAEP 的上传按钮。
 */
@Mixin(value = PatternEncodingTermScreen.class, remap = false)
public abstract class PatternEncodingTermScreenUploadMixin implements IPatternUploadTerminal {

    @Override
    public Rect2i getUploadAnchor() {
        return null;
    }
}
