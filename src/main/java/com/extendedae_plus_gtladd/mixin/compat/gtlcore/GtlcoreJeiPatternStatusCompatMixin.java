package com.extendedae_plus_gtladd.mixin.compat.gtlcore;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.event.TickEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 关闭 GTLCore 在 JEI 原料列表与书签列表中绘制的“可合成”角标。
 *
 * GTLCore 的 PatternStatusRenderMixin 会在 IngredientListRenderer.renderBatch 返回后，
 * 对该批次里每个元素调用 JeiPatternStatus.draw；书签界面同样经由 IngredientGrid ->
 * IngredientListRenderer 渲染，因此无法在渲染返回后擦除像素，只能在绘制入口取消。
 *
 * tick 一并取消：它唯一的产物就是供 draw 使用的 craftable 快照，禁用绘制后继续每 20 tick
 * 发送一次 JeiPatternQuery 只会产生无用的网络流量。
 *
 * 目标类在未安装 GTLCore 时不存在，故使用 @Pseudo 并显式 require = 0。
 */
@Pseudo
@Mixin(targets = "org.gtlcore.gtlcore.client.ae2.JeiPatternStatus", remap = false)
public abstract class GtlcoreJeiPatternStatusCompatMixin {

    // 用方法名而非完整描述符匹配：这两个方法在 JeiPatternStatus 中没有重载，
    // 可避免不同 GTLCore 版本间 SRG 描述符差异导致匹配失败。
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void eap$disableCraftableLabel(GuiGraphics graphics, Object ingredient, int x, int y, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void eap$disablePatternStatusQuery(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}