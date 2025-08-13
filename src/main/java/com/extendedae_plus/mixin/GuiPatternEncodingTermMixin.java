package com.extendedae_plus.mixin;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import org.spongepowered.asm.mixin.Mixin;

// 保留空的 Mixin 外壳，不再注入任何“上传到矩阵”相关逻辑或按钮
@Mixin(PatternEncodingTermScreen.class)
public abstract class GuiPatternEncodingTermMixin {
}
