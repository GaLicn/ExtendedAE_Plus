package com.extendedae_plus.util.smartDoubling;

import appeng.api.crafting.IPatternDetails;

import java.util.IdentityHashMap;
import java.util.Map;

/** 跟踪单个 CPU 作业内各样板的自适应批量上限。 */
public final class AdaptiveBatchLimitTracker {
    private static final int PROBE_SUCCESS_THRESHOLD = 16;

    private final Map<IPatternDetails, Long> limits = new IdentityHashMap<>();
    private final Map<IPatternDetails, Integer> successes = new IdentityHashMap<>();
    private Object owner;

    public void beginJob(Object job) {
        if (this.owner != job) {
            this.owner = job;
            this.clearLimits();
        }
    }

    public long getLimit(IPatternDetails pattern) {
        return this.limits.getOrDefault(pattern, Long.MAX_VALUE);
    }

    public void recordRejected(IPatternDetails pattern, long multiplier) {
        long reducedLimit = Math.max(1L, multiplier / 2L);
        this.limits.merge(pattern, reducedLimit, Math::min);
        this.successes.remove(pattern);
    }

    public void recordAccepted(IPatternDetails pattern, long multiplier) {
        Long currentLimit = this.limits.get(pattern);
        if (currentLimit == null) {
            return;
        }
        if (multiplier > currentLimit) {
            this.limits.put(pattern, multiplier);
            this.successes.remove(pattern);
            return;
        }
        if (multiplier < currentLimit) {
            return;
        }

        int successes = this.successes.getOrDefault(pattern, 0) + 1;
        if (successes < PROBE_SUCCESS_THRESHOLD) {
            this.successes.put(pattern, successes);
            return;
        }

        long nextLimit = currentLimit > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : currentLimit * 2L;
        if (nextLimit == Long.MAX_VALUE) {
            this.limits.remove(pattern);
        } else {
            this.limits.put(pattern, nextLimit);
        }
        this.successes.remove(pattern);
    }

    public void clearWhenIdle(Object job) {
        if (job == null) {
            this.owner = null;
            this.clearLimits();
        }
    }

    private void clearLimits() {
        this.limits.clear();
        this.successes.clear();
    }
}
