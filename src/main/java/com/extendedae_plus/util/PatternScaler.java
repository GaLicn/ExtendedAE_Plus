package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.content.ScaledProcessingPattern;

import java.util.Arrays;

public final class PatternScaler {
    private PatternScaler() {
    }

    public static ScaledProcessingPattern scale(AEProcessingPattern base, AEKey target, long requestedAmount) {
        if (base == null) throw new IllegalArgumentException("base");
        if (target == null) throw new IllegalArgumentException("target");

        GenericStack[] baseSparseInputs = base.getSparseInputs();
        GenericStack[] baseSparseOutputs = base.getSparseOutputs();
        IInput[] baseInputs = base.getInputs();
        GenericStack[] baseOutputs = base.getOutputs();

        /* 1. 构建缩放后的 sparseInputs */
        GenericStack[] scaledSparseInputs = new GenericStack[baseSparseInputs.length];
        for (int i = 0; i < baseSparseInputs.length; i++) {
            GenericStack in = baseSparseInputs[i];
            if (in != null) {
                scaledSparseInputs[i] = new GenericStack(in.what(), requestedAmount);
            }
        }

        /* 2. 构建缩放后的 sparseOutputs */
        GenericStack[] scaledSparseOutputs = new GenericStack[baseSparseOutputs.length];
        for (int i = 0; i < baseSparseOutputs.length; i++) {
            GenericStack out = baseSparseOutputs[i];
            if (out != null) {
                scaledSparseOutputs[i] = new GenericStack(out.what(), requestedAmount);
            }
        }

        /* 3. 构建压缩输入（ScaledInput） */
        IInput[] scaledInputs = new IInput[baseInputs.length];
        for (int i = 0; i < baseInputs.length; i++) {
            var in = baseInputs[i];
            var template = in.getPossibleInputs();

            GenericStack[] scaledTemplates = new GenericStack[template.length];
            for (int j = 0; j < template.length; j++) {
                scaledTemplates[j] = new GenericStack(template[j].what(), 1);
            }
            scaledInputs[i] = new ScaledProcessingPattern.Input(scaledTemplates, requestedAmount);
        }

        /* 4. 构建压缩输出 */
        GenericStack[] scaledCondensedOutputs = new GenericStack[baseOutputs.length];
        for (int i = 0; i < baseOutputs.length; i++) {
            GenericStack out = baseOutputs[i];
            if (out != null) {
                scaledCondensedOutputs[i] = new GenericStack(out.what(), requestedAmount);
            }
        }

        /* Debug 输出 */
        System.out.println("[extendedae_plus] 正在缩放样板：");
        System.out.println("  原始样板: " + base);
        System.out.println("  目标物品: " + target);
        System.out.println("  请求数量: " + requestedAmount);
        System.out.println("  缩放后输入: " + Arrays.toString(scaledInputs));
        System.out.println("  缩放后输出: " + Arrays.toString(scaledCondensedOutputs));
        System.out.println("  缩放后稀疏输入: " + Arrays.toString(scaledSparseInputs));
        System.out.println("  缩放后稀疏输出: " + Arrays.toString(scaledSparseOutputs));


        return new ScaledProcessingPattern(base,
                base.getDefinition(),
                scaledSparseInputs,
                scaledSparseOutputs,
                scaledInputs,
                scaledCondensedOutputs);
    }

    private static long safeMul(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }
}
