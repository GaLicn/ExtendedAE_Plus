package com.extendedae_plus.mixin.extendedae.client.gui;

import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 让矩阵装配核心的样板页按真实库存槽位数初始化。
 *
 * <p>ExtendedAE 的 {@code PatternInfo} 构造器把行数写死为 36 槽（4 行 × 9），
 * 超出部分需要额外行才能显示。原先的实现是在构造完成后用反射 new 出私有内部类
 * {@code PatternRow} 再重建列表，这里改为直接替换构造器里的槽位数常量，
 * 让原版循环自己建出足够多的行，无需反射也无需重建。</p>
 */
@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiAssemblerMatrix$PatternInfo")
public abstract class GuiAssemblerMatrixPatternInfoMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 36))
    private static int extendedae_plus$useActualSlotCount(int original, long id) {
        return Math.max(original, extendedae_plus$inventorySize(id));
    }

    @Unique
    private static int extendedae_plus$inventorySize(long id) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return TileAssemblerMatrixPattern.INV_SIZE;
            }
            var be = mc.level.getBlockEntity(BlockPos.of(id));
            if (be instanceof TileAssemblerMatrixPattern tile) {
                return tile.getTerminalPatternInventory().size();
            }
            if (be instanceof PatternCorePlusBlockEntity plus) {
                return plus.getTerminalPatternInventory().size();
            }
        } catch (RuntimeException ignored) {
            // 客户端世界卸载瞬间取不到方块实体时退回原版槽位数，不影响界面打开。
        }
        return TileAssemblerMatrixPattern.INV_SIZE;
    }
}