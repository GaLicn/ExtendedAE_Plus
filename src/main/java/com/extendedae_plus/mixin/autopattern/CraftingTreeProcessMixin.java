package com.extendedae_plus.mixin.autopattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.util.PatternScaler;
import com.extendedae_plus.util.RequestedAmountHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

/**
 * 注入 CraftingTreeProcess 构造器尾部：将 AEProcessingPattern 替换为 ScaledProcessingPattern
 * 以确保后续执行使用放大后的输入/输出视图。
 */
@Mixin(CraftingTreeProcess.class)
public abstract class CraftingTreeProcessMixin {

    @Shadow abstract void request(CraftingSimulationState inv, long times) throws CraftBranchFailure, InterruptedException;

    @ModifyVariable(
            method = "<init>(Lappeng/api/networking/crafting/ICraftingService;Lappeng/crafting/CraftingCalculation;Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/CraftingTreeNode;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static IPatternDetails extendedae_plus$replaceDetailsAtHead(IPatternDetails original, ICraftingService cc, CraftingCalculation job, IPatternDetails details, CraftingTreeNode craftingTreeNode) {
        try {
            if (!(details instanceof AEProcessingPattern proc)) return original;

            CraftingTreeNodeAccessor parentAcc = (CraftingTreeNodeAccessor) craftingTreeNode;
            AEKey parentTarget = parentAcc.extendedae_plus$getWhat();
            long requested = RequestedAmountHolder.get();
            // 使用当前线程栈顶的值进行缩放，不在此处清理；构造完成后应该由调用方的 pop 恢复状态
            return PatternScaler.scale(proc, parentTarget, requested);
        } catch (Exception e) {
            LOGGER.warn("构建倍增样板出错", e);
            e.printStackTrace();
            return original;
        }
    }
}
