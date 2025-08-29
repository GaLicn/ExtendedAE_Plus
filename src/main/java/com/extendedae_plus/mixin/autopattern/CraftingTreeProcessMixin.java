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
import com.extendedae_plus.api.SmartDoublingAwarePattern;
import com.extendedae_plus.content.ScaledProcessingPattern;
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
            // 若传入的 details 已经是缩放样板，且原始样板不允许缩放，则直接解包为原始样板
            if (details instanceof ScaledProcessingPattern sp) {
                var proc0 = sp.getOriginal();
                if (proc0 instanceof SmartDoublingAwarePattern aware0 && !aware0.eap$allowScaling()) {
                    LOGGER.info("[extendedae_plus] 传入已缩放样板但已禁用，解包为原始样板并跳过缩放: requested={}", RequestedAmountHolder.get());
                    return proc0;
                }
            }

            if (!(details instanceof AEProcessingPattern proc)) return original;

            // 若样板标记为不允许缩放，则直接跳过
            if (proc instanceof SmartDoublingAwarePattern aware && !aware.eap$allowScaling()) {
                LOGGER.info("[extendedae_plus] 智能翻倍已禁用，跳过缩放: pattern={} target={} requested={}", proc, craftingTreeNode, RequestedAmountHolder.get());
                return original;
            }

            CraftingTreeNodeAccessor parentAcc = (CraftingTreeNodeAccessor) craftingTreeNode;
            AEKey parentTarget = parentAcc.extendedae_plus$getWhat();
            long requested = RequestedAmountHolder.get();
            // 使用当前线程栈顶的值进行缩放，不在此处清理；构造完成后应该由调用方的 pop 恢复状态
            LOGGER.info("[extendedae_plus] 执行缩放: allowScaling={} target={} requested={}",
                    (proc instanceof SmartDoublingAwarePattern aware2 ? aware2.eap$allowScaling() : null),
                    parentTarget,
                    requested);
            var scaled = PatternScaler.scale(proc, parentTarget, requested);
            return scaled != null ? scaled : original;
        } catch (Exception e) {
            LOGGER.warn("构建倍增样板出错", e);
            e.printStackTrace();
            return original;
        }
    }
}
