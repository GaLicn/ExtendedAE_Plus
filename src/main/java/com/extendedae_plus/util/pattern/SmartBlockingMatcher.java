package com.extendedae_plus.util.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 智能阻挡的匹配判定。
 * <p>
 * 若对“每个输入槽 × 每个候选”逐个调用 {@link PatternProviderTarget#containsPatternInput(Set)}，
 * 每次调用都会把目标库存的可用堆栈完整遍历一遍，槽位和候选一多就是数量级的开销，
 * 在网络存储上表现为下单时服务端 TPS 骤降。
 * <p>
 * 这里改为只遍历一次：传入一个“记录命中槽位、但恒返回 false”的集合，
 * 让 {@code containsPatternInput} 走完整个库存的同时把命中的槽位记下来，
 * 之后在内存里判断是否所有槽位都命中。
 */
public final class SmartBlockingMatcher {
    private SmartBlockingMatcher() {
    }

    /**
     * 判定目标是否同时包含模式的每一个输入槽（每个槽匹配任意一个候选即可）。
     */
    public static boolean matchesAllInputs(PatternProviderTarget target, IPatternDetails pattern) {
        var inputs = pattern.getInputs();
        if (inputs.length == 0) {
            // 与原逐槽判定保持一致：没有输入槽时视为“全部匹配”
            return true;
        }

        AEKey[][] slotCandidates = new AEKey[inputs.length][];
        for (int i = 0; i < inputs.length; i++) {
            var possible = inputs[i].getPossibleInputs();
            if (possible.length == 0) {
                return false;
            }

            AEKey[] keys = new AEKey[possible.length];
            for (int j = 0; j < possible.length; j++) {
                keys[j] = possible[j].what().dropSecondary();
            }
            slotCandidates[i] = keys;
        }

        ProbeSet probe = new ProbeSet(slotCandidates);
        target.containsPatternInput(probe);

        if (probe.isFallback()) {
            // 目标实现不是通过 Set.contains 查询，探测不到命中，退回逐槽判定以保证正确性
            return matchesSlotBySlot(target, slotCandidates);
        }
        return probe.allSatisfied();
    }

    private static boolean matchesSlotBySlot(PatternProviderTarget target, AEKey[][] slotCandidates) {
        for (AEKey[] keys : slotCandidates) {
            boolean slotMatched = false;
            for (AEKey key : keys) {
                if (target.containsPatternInput(Collections.singleton(key))) {
                    slotMatched = true;
                    break;
                }
            }
            if (!slotMatched) {
                return false;
            }
        }
        return true;
    }

    private static final class ProbeSet extends AbstractSet<AEKey> {
        /** 候选（dropSecondary 后）到所属输入槽的映射 */
        private final Map<AEKey, BitSet> keyToSlots = new HashMap<>();
        private final BitSet satisfied;
        private final int slotCount;
        private boolean fallback;

        ProbeSet(AEKey[][] slotCandidates) {
            this.slotCount = slotCandidates.length;
            this.satisfied = new BitSet(slotCount);
            for (int i = 0; i < slotCount; i++) {
                for (AEKey key : slotCandidates[i]) {
                    keyToSlots.computeIfAbsent(key, k -> new BitSet(slotCount)).set(i);
                }
            }
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof AEKey key) {
                BitSet slots = keyToSlots.get(key);
                if (slots != null) {
                    satisfied.or(slots);
                }
            }
            // 恒返回 false：既不打断实现方的遍历，也只利用其副作用
            return false;
        }

        @Override
        public Iterator<AEKey> iterator() {
            // 实现方若迭代集合而不调用 contains，就探测不到命中，标记后走回退路径
            fallback = true;
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return keyToSlots.size();
        }

        boolean allSatisfied() {
            return satisfied.cardinality() == slotCount;
        }

        boolean isFallback() {
            return fallback;
        }
    }
}