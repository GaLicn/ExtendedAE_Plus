package com.extendedae_plus.mixin.ae2;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 2000)
public abstract class CraftingCPUClusterMixin {
    // 1) 提升“单方块线程上限”的常量，避免抛出 IAE 的 IllegalArgumentException
    @ModifyExpressionValue(
            method = "addBlockEntity(Lappeng/blockentity/crafting/CraftingBlockEntity;)V",
            at = @At(value = "CONSTANT", args = "intValue=16")
    )
    private int extendedae_plus$raisePerUnitLimit(int original) {
        // 放宽到极大值，完全取消单方块 16 线程的硬限制
        return Integer.MAX_VALUE;
    }

    // 2) 保持统计使用原始线程值（若存在多处调用），不再返回固定 16
    @WrapOperation(
            method = "addBlockEntity(Lappeng/blockentity/crafting/CraftingBlockEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/blockentity/crafting/CraftingBlockEntity;getAcceleratorThreads()I",
                    ordinal = 1
            )
    )
    private int extendedae_plus$onGetThreadsForLimitCheck(CraftingBlockEntity instance, Operation<Integer> original) {
        // 返回原始线程数，确保总并行单元不被错误下限
        return original.call(instance);
    }
}
