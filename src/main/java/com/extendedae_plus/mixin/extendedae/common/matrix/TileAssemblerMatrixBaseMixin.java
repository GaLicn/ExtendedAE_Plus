package com.extendedae_plus.mixin.extendedae.common.matrix;

import appeng.api.networking.IGridNode;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

/** 原版玻璃加载完成后纳入超级矩阵的延迟重算队列。 */
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

    /** 让所有超级矩阵部件返回同一节点集合，确保整个多方块只消耗一个频道。 */
    @Inject(method = "getMultiblockNodes", at = @At("HEAD"), cancellable = true)
    private void eap$getSuperMatrixMultiblockNodes(CallbackInfoReturnable<Iterator<IGridNode>> cir) {
        if ((Object) this instanceof SuperAssemblerMatrixPart part) {
            var cluster = part.eap$getSuperMatrixCluster();
            if (cluster != null) {
                cir.setReturnValue(cluster.getGridNodes());
            }
        }
    }

    /** EAE 基类的能力入口统一转发超级集群样板库存，包含原版矩阵玻璃。 */
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private <T> void eap$exposeSuperMatrixPatternInventory(Capability<T> capability,
            Direction facing, CallbackInfoReturnable<LazyOptional<T>> cir) {
        if (capability != ForgeCapabilities.ITEM_HANDLER
                || !((Object) this instanceof SuperAssemblerMatrixPart part)) {
            return;
        }
        var cluster = part.eap$getSuperMatrixCluster();
        if (cluster != null && cluster.getCore() != null) {
            cir.setReturnValue(LazyOptional.of(
                    () -> cluster.getCore().getExposedPatternItemHandler()).cast());
        }
    }
}
