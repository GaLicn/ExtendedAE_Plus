package com.extendedae_plus.mixin.ae2;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import com.extendedae_plus.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(PatternAccessTermScreen.class)
public class PatternAccessTermScreenMixin {
    // 在绘制前景的最后阶段叠加显示样板输出数量
    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void injectDrawCraftingAmount(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        PatternAccessTermScreen<?> screen = (PatternAccessTermScreen<?>)(Object) this;

        // 调用GuiUtil的通用渲染方法
        GuiUtil.renderPatternAmounts(guiGraphics, screen);
    }
}
