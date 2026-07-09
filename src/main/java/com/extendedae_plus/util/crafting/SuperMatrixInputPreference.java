package com.extendedae_plus.util.crafting;

import appeng.api.stacks.AEKey;
import appeng.crafting.execution.InputTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 超级矩阵执行期选料顺序调整。
 */
public final class SuperMatrixInputPreference {

    private static final ThreadLocal<Set<AEKey>> ACTIVE_INTERMEDIATES = new ThreadLocal<>();

    private SuperMatrixInputPreference() {
    }

    /** 进入一次“超级矩阵服务的样板”提取：设置本作业中间产物集合。 */
    public static void push(Set<AEKey> intermediates) {
        ACTIVE_INTERMEDIATES.set(intermediates);
    }

    /** 退出提取：清除线程局部状态。务必在 finally 中调用。 */
    public static void pop() {
        ACTIVE_INTERMEDIATES.remove();
    }

    /** 当前是否处于需要重排的上下文。 */
    public static Set<AEKey> current() {
        return ACTIVE_INTERMEDIATES.get();
    }

    /**
     * 稳定重排候选料：非中间产物在前、中间产物在后，组内保持原顺序。
     * 若不需要重排（没上下文、无中间产物、或候选全同类）返回 null，调用方保持原结果。
     */
    public static List<InputTemplate> reorderOrNull(Iterable<InputTemplate> original) {
        Set<AEKey> intermediates = ACTIVE_INTERMEDIATES.get();
        if (intermediates == null || intermediates.isEmpty()) {
            return null;
        }

        List<InputTemplate> leaves = null;
        List<InputTemplate> inter = null;
        for (var t : original) {
            if (intermediates.contains(t.key())) {
                if (inter == null) {
                    inter = new ArrayList<>(4);
                }
                inter.add(t);
            } else {
                if (leaves == null) {
                    leaves = new ArrayList<>(4);
                }
                leaves.add(t);
            }
        }

        // 只有当同时存在“叶子”和“中间产物”候选时，重排才有意义。
        if (inter == null || leaves == null) {
            return null;
        }
        leaves.addAll(inter);
        return leaves;
    }
}
