package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import com.extendedae_plus.api.upload.IPatternUploadTerminal;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;

/** 原生 AE2 编码终端实现通用上传终端契约。 */
@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenUploadMixin implements IPatternUploadTerminal {

    @Override
    public Rect2i getUploadAnchor() {
        return null;
    }
}
