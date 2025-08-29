package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.content.ScaledProcessingPattern;
import com.extendedae_plus.api.SmartDoublingAwarePattern;

import java.util.Arrays;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

public final class PatternScaler {
    private PatternScaler() {
    }

    public static ScaledProcessingPattern scale(AEProcessingPattern base, AEKey target, long requestedAmount) {
        if (base == null) throw new IllegalArgumentException("base");
        if (target == null) throw new IllegalArgumentException("target");

        // 双保险：若样板标记为不允许缩放，直接放弃缩放（返回 null 表示调用方应保持原样板）
        if (base instanceof SmartDoublingAwarePattern aware && !aware.eap$allowScaling()) {
            LOGGER.info("[extendedae_plus] PatternScaler: 智能翻倍禁用，跳过缩放 target={} requested={}", target, requestedAmount);
            return null;
        }

        GenericStack[] baseSparseInputs = base.getSparseInputs();
        GenericStack[] baseSparseOutputs = base.getSparseOutputs();
        IInput[] baseInputs = base.getInputs();
        GenericStack[] baseOutputs = base.getOutputs();

        // 新逻辑：不再对样板进行单位化处理
        // 找到目标输出在 outputs 中的索引（尝试匹配 target，否则取第一个非空输出）
        int targetOutIndex = -1;
        for (int i = 0; i < baseOutputs.length; i++) {
            var out = baseOutputs[i];
            if (out != null && target != null && out.what() != null && out.what().equals(target)) {
                targetOutIndex = i;
                break;
            }
        }
        if (targetOutIndex == -1) {
            for (int i = 0; i < baseOutputs.length; i++) {
                if (baseOutputs[i] != null) {
                    targetOutIndex = i;
                    break;
                }
            }
        }
        if (targetOutIndex == -1 && baseOutputs.length > 0) targetOutIndex = 0;

        long perOperationTarget = 1L;
        if (targetOutIndex >= 0 && baseOutputs[targetOutIndex] != null) {
            long amt = baseOutputs[targetOutIndex].amount();
            if (amt > 0) perOperationTarget = amt;
        }

        // 使用最小整数倍（ceil）策略：直接选择满足请求的最小倍数
        long multiplier = 1L;
        if (requestedAmount > 0) {
            long needed = requestedAmount / perOperationTarget + ((requestedAmount % perOperationTarget) == 0 ? 0 : 1);
            multiplier = needed <= 1L ? 1L : needed;
        }

        // 构建压缩输入（将每个输入的 multiplier 翻倍，保留每个模板的原始数量）
        IInput[] scaledInputs = new IInput[baseInputs.length];
        for (int i = 0; i < baseInputs.length; i++) {
            var in = baseInputs[i];
            var template = in.getPossibleInputs();
            GenericStack[] scaledTemplates = new GenericStack[template.length];
            for (int j = 0; j < template.length; j++) {
                scaledTemplates[j] = new GenericStack(template[j].what(), template[j].amount());
            }
            scaledInputs[i] = new ScaledProcessingPattern.Input(scaledTemplates, in.getMultiplier() * multiplier);
        }

        /* 4. 构建压缩输出 */
        GenericStack[] scaledCondensedOutputs = new GenericStack[baseOutputs.length];
        for (int i = 0; i < baseOutputs.length; i++) {
            GenericStack out = baseOutputs[i];
            if (out != null) {
                scaledCondensedOutputs[i] = new GenericStack(out.what(), out.amount() * multiplier);
            }
        }

        // 构建并打印稀疏表示（直接按 multiplier 放大）
        GenericStack[] scaledSparseInputs = new GenericStack[baseSparseInputs.length];
        for (int i = 0; i < baseSparseInputs.length; i++) {
            var in = baseSparseInputs[i];
            if (in != null) {
                scaledSparseInputs[i] = new GenericStack(in.what(), in.amount() * multiplier);
            }
        }
        GenericStack[] scaledSparseOutputs = new GenericStack[baseSparseOutputs.length];
        for (int i = 0; i < baseSparseOutputs.length; i++) {
            var out = baseSparseOutputs[i];
            if (out != null) {
                scaledSparseOutputs[i] = new GenericStack(out.what(), out.amount() * multiplier);
            }
        }


        /* Debug 输出 */
        LOGGER.info("[extendedae_plus] 正在缩放样板： 目标物品: {}  请求数量: {}  缩放后输入: {}  缩放后输出: {}  缩放后稀疏输入: {}  缩放后稀疏输出: {}",
                target,
                requestedAmount,
                Arrays.toString(scaledInputs),
                Arrays.toString(scaledCondensedOutputs),
                Arrays.toString(scaledSparseInputs),
                Arrays.toString(scaledSparseOutputs));


        return new ScaledProcessingPattern(base,
                base.getDefinition(),
                scaledSparseInputs,
                scaledSparseOutputs,
                scaledInputs,
                scaledCondensedOutputs);
    }
}
