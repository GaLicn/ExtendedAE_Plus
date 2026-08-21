package com.extendedae_plus.mixin.extendedae.common.matrix;

import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /** EAE 基类的物品处理器入口统一转发超级集群样板库存，包含原版矩阵玻璃。 */
    @Inject(method = "getPatternInv", at = @At("HEAD"), cancellable = true)
    private void eap$exposeSuperMatrixPatternInventory(Direction facing,
            CallbackInfoReturnable<IItemHandler> cir) {
        if (!((Object) this instanceof SuperAssemblerMatrixPart part)) {
            return;
        }
        var cluster = part.eap$getSuperMatrixCluster();
        if (cluster != null && cluster.getCore() != null) {
            cir.setReturnValue(cluster.getCore().getExposedPatternItemHandler(facing));
        }
    }
}
