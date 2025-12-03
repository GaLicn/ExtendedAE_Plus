package com.extendedae_plus.mixin.extendedae.client.gui;

import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.util.List;


@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiAssemblerMatrix$PatternInfo")
public abstract class GuiAssemblerMatrixPatternInfoMixin {

    @Shadow
    @Final
    private List<Object> internalRows;

    @Unique
    private static Constructor<?> extendedae_plus$patternRowCtor;

    //通过反射获取目标类中的PatternRow内部类,new出来对象
    @Unique
    private static Object extendedae_plus$createPatternRow(long patternID, int offset, int slots) {
        try {
            if (extendedae_plus$patternRowCtor == null) {
                var clazz = Class.forName("com.glodblock.github.extendedae.client.gui.GuiAssemblerMatrix$PatternRow");
                extendedae_plus$patternRowCtor = clazz.getDeclaredConstructor(long.class, int.class, int.class);
                extendedae_plus$patternRowCtor.setAccessible(true);
            }
            return extendedae_plus$patternRowCtor.newInstance(patternID, offset, slots);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create PatternRow", e);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void extendedae_plus$rebuildRows(long id, CallbackInfo ci) {
        int invSize = TileAssemblerMatrixPattern.INV_SIZE;//样板核心槽位数变量
        try {
            //根据目标核心类型设置用于客户端渲染的槽位数值
            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                BlockPos pos = BlockPos.of(id);
                var be = mc.level.getBlockEntity(pos);
                if (be instanceof TileAssemblerMatrixPattern tile) {
                    invSize = tile.getTerminalPatternInventory().size();
                } else if (be instanceof PatternCorePlusBlockEntity plus) {
                    invSize = plus.getTerminalPatternInventory().size();
                }
            }
        } catch (Throwable ignored) {
        }

        if (invSize <= TileAssemblerMatrixPattern.INV_SIZE) {
            return;
        }

        this.internalRows.clear();

        int left = invSize;
        int offset = 0;
        do {
            this.internalRows.add(extendedae_plus$createPatternRow(id, offset, Math.min(left, 9)));
            left -= 9;
            offset += 9;
        } while (left > 0);
    }
}


