package com.extendedae_plus.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.mixin.ae2.accessor.CraftingCalculationAccessor;
import com.extendedae_plus.mixin.ae2.accessor.CraftingTreeNodeAccessor;
import com.extendedae_plus.util.PatternScaler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 注入 CraftingTreeProcess 构造器尾部：将 AEProcessingPattern 替换为 ScaledProcessingPattern
 * 以确保后续执行使用放大后的输入/输出视图。
 */
@Mixin(CraftingTreeProcess.class)
public class CraftingTreeProcessMixin {

    @ModifyVariable(method = "<init>(Lappeng/api/networking/crafting/ICraftingService;Lappeng/crafting/CraftingCalculation;Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/CraftingTreeNode;)V",
            at = @At("HEAD"), argsOnly = true)
    private static IPatternDetails extendedae_plus$replaceDetailsAtHead(IPatternDetails original, ICraftingService cc, CraftingCalculation job, IPatternDetails details, CraftingTreeNode craftingTreeNode) {
        try {
            if (!(details instanceof AEProcessingPattern proc)) return original;

            CraftingCalculationAccessor jobAcc = (CraftingCalculationAccessor) job;
            long requested = jobAcc.extendedae_plus$getRequestedAmount();

            CraftingTreeNodeAccessor parentAcc = (CraftingTreeNodeAccessor) craftingTreeNode;
            AEKey parentTarget = parentAcc.extendedae_plus$getWhat();

            System.out.println("[extendedae_plus] Replacing constructor details at HEAD for: " + parentTarget + " x " + requested);

            return PatternScaler.scale(proc, parentTarget, requested);
        } catch (Exception e) {
            System.err.println("[extendedae_plus] Error replacing pattern at HEAD: " + e.getMessage());
            e.printStackTrace();
            return original;
        }
    }
}
