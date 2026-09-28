package com.extendedae_plus.api.upload;

import net.minecraft.client.renderer.Rect2i;

public interface IPatternUploadTerminal {
    Rect2i getUploadAnchor();

    default float getUploadScale() {
        return -1f;
    }
}
