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

        // 计算每个压缩输入槽位的总量（per operation）: multiplier * template.amount
        long[] inputsCounts = new long[baseInputs.length];
        for (int i = 0; i < baseInputs.length; i++) {
            var in = baseInputs[i];
            var first = in.getPossibleInputs()[0];
            inputsCounts[i] = in.getMultiplier() * first.amount();
        }

        // 计算每个输出的数量（per operation）
        long[] outputsCounts = new long[baseOutputs.length];
        for (int i = 0; i < baseOutputs.length; i++) {
            var out = baseOutputs[i];
            outputsCounts[i] = out == null ? 0L : out.amount();
        }

        // 合并为一个数组并计算 gcd（使用早期退出优化）
        long[] combined = ArraySimplifier.combine(inputsCounts, outputsCounts);
        long gcd = ArraySimplifier.findGCDWithEarlyExit(combined);
        if (gcd <= 0) gcd = 1;

        // 如果 gcd == 1，则无需分配新的数组，直接使用 combined 作为 simplified 视图
        long[] simplified = ArraySimplifier.simplifyByGcd(combined, gcd);

        // 找到目标输出在 outputs 中的索引
        int targetOutIndex = -1;
        for (int i = 0; i < baseOutputs.length; i++) {
            if (baseOutputs[i] != null) {
                targetOutIndex = i;
                break;
            }
        }
        if (targetOutIndex == -1 && baseOutputs.length > 0) targetOutIndex = 0;

        long simplifiedTargetPerUnit = simplified[inputsCounts.length + Math.max(0, targetOutIndex)];
        if (simplifiedTargetPerUnit <= 0) simplifiedTargetPerUnit = 1;

        // 单位数：需要多少 "最简约单位" 才能满足 requestedAmount（向上取整）
        long units = (requestedAmount + simplifiedTargetPerUnit - 1) / simplifiedTargetPerUnit;

        // 构建压缩输入（ScaledInput）——模板数量为 simplifiedInputs, multiplier 为 units
        IInput[] scaledInputs = new IInput[baseInputs.length];
        for (int i = 0; i < baseInputs.length; i++) {
            var in = baseInputs[i];
            var template = in.getPossibleInputs();
            GenericStack[] scaledTemplates = new GenericStack[template.length];
            long simplifiedInputAmount = simplified[i];
            for (int j = 0; j < template.length; j++) {
                scaledTemplates[j] = new GenericStack(template[j].what(), simplifiedInputAmount);
            }
            scaledInputs[i] = new ScaledProcessingPattern.Input(scaledTemplates, units);
        }

        /* 4. 构建压缩输出 */
        GenericStack[] scaledCondensedOutputs = new GenericStack[baseOutputs.length];
        for (int i = 0; i < baseOutputs.length; i++) {
            GenericStack out = baseOutputs[i];
            if (out != null) {
                long simplifiedOutAmount = simplified[inputsCounts.length + i];
                scaledCondensedOutputs[i] = new GenericStack(out.what(), simplifiedOutAmount * units);
            }
        }

        // 构建并打印稀疏表示（按 unit * simplified / gcd 映射回原稀疏槽）
        GenericStack[] scaledSparseInputs = new GenericStack[baseSparseInputs.length];
        for (int i = 0; i < baseSparseInputs.length; i++) {
            var in = baseSparseInputs[i];
            if (in != null) {
                long scaledAmount = in.amount() * units / gcd;
                scaledSparseInputs[i] = new GenericStack(in.what(), scaledAmount);
            }
        }
        GenericStack[] scaledSparseOutputs = new GenericStack[baseSparseOutputs.length];
        for (int i = 0; i < baseSparseOutputs.length; i++) {
            var out = baseSparseOutputs[i];
            if (out != null) {
                long scaledAmount = out.amount() * units / gcd;
                scaledSparseOutputs[i] = new GenericStack(out.what(), scaledAmount);
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
}
