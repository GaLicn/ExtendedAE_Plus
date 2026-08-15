package com.extendedae_plus.mixin.extendedae.common.matrix;

import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版玻璃加载后加入超级矩阵的延迟重算队列。 */
@Mixin(value = TileAssemblerMatrixBase.class, remap = false)
public abstract class TileAssemblerMatrixBaseMixin {

    @Inject(method = "onReady", at = @At("TAIL"))
    private void eap$scheduleGlassSuperMatrixRecalculation(CallbackInfo ci) {
        if ((Object) this instanceof TileAssemblerMatrixGlass glass
                && glass instanceof SuperAssemblerMatrixPart
                && glass.getLevel() instanceof ServerLevel serverLevel) {
            SuperAssemblerMatrixCalculator.scheduleRecalculate(serverLevel, glass.getBlockPos());
        }
    }
}
