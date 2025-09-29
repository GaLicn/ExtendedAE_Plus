package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.api.SmartDoublingAwarePattern;
import com.extendedae_plus.config.EAEPConfig;
import com.extendedae_plus.content.ScaledProcessingPattern;

import java.util.ArrayList;
import java.util.List;

public final class PatternScaler {
    private PatternScaler() {
    }

    public static ScaledProcessingPattern scale(AEProcessingPattern base, AEKey target, long requestedAmount) {
        if (base == null) throw new IllegalArgumentException("base");
        if (target == null) throw new IllegalArgumentException("target");

        // 双保险：若样板标记为不允许缩放，直接放弃缩放（返回 null 表示调用方应保持原样板）
        if (base instanceof SmartDoublingAwarePattern aware && !aware.eap$allowScaling()) {
            return null;
        }

        List<GenericStack> baseSparseInputs = base.getSparseInputs();
        List<GenericStack> baseSparseOutputs = base.getSparseOutputs();
        IInput[] baseInputs = base.getInputs();
        List<GenericStack> baseOutputs = base.getOutputs();

        // 新逻辑：不再对样板进行单位化处理
        // 找到目标输出在 outputs 中的索引（尝试匹配 target，否则取第一个非空输出）
        int targetOutIndex = -1;
        for (int i = 0; i < baseOutputs.size(); i++) {
            var out = baseOutputs.get(i);
            if (out != null && out.what() != null && out.what().equals(target)) {
                targetOutIndex = i;
                break;
            }
        }
        if (targetOutIndex == -1) {
            for (int i = 0; i < baseOutputs.size(); i++) {
                if (baseOutputs.get(i) != null) {
                    targetOutIndex = i;
                    break;
                }
            }
        }
        if (targetOutIndex == -1 && !baseOutputs.isEmpty()) targetOutIndex = 0;

        long perOperationTarget = 1L;
        if (targetOutIndex >= 0 && baseOutputs.get(targetOutIndex) != null) {
            long amt = baseOutputs.get(targetOutIndex).amount();
            if (amt > 0) perOperationTarget = amt;
        }

        // 使用最小整数倍（ceil）策略：直接选择满足请求的最小倍数
        long multiplier = 1L;
        if (requestedAmount > 0) {
            long needed = requestedAmount / perOperationTarget + ((requestedAmount % perOperationTarget) == 0 ? 0 : 1);
            multiplier = Math.max(needed, 1L);
        }
        // 应用配置的最大倍数上限（0 表示不限制）
        try {
            int maxMul = EAEPConfig.SMART_SCALING_MAX_MULTIPLIER.get();
            if (maxMul > 0 && multiplier > maxMul) {
                multiplier = maxMul;
            }
        } catch (Throwable ignore) {
            // 配置读取异常时不施加上限
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

        // 构建压缩输出（List）
        List<GenericStack> scaledCondensedOutputs = new ArrayList<>(baseOutputs.size());
        for (GenericStack out : baseOutputs) {
            if (out != null) {
                scaledCondensedOutputs.add(new GenericStack(out.what(), out.amount() * multiplier));
            } else {
                scaledCondensedOutputs.add(null);
            }
        }

        // 构建稀疏表示（List，直接按 multiplier 放大）
        List<GenericStack> scaledSparseInputs = new ArrayList<>(baseSparseInputs.size());
        for (GenericStack in : baseSparseInputs) {
            if (in != null) {
                scaledSparseInputs.add(new GenericStack(in.what(), in.amount() * multiplier));
            } else {
                scaledSparseInputs.add(null);
            }
        }
        List<GenericStack> scaledSparseOutputs = new ArrayList<>(baseSparseOutputs.size());
        for (GenericStack out : baseSparseOutputs) {
            if (out != null) {
                scaledSparseOutputs.add(new GenericStack(out.what(), out.amount() * multiplier));
            } else {
                scaledSparseOutputs.add(null);
            }
        }

        return new ScaledProcessingPattern(base,
                base.getDefinition(),
                scaledSparseInputs,
                scaledSparseOutputs,
                scaledInputs,
                scaledCondensedOutputs);
    }
}
