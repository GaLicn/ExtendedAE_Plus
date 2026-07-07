package com.extendedae_plus.content.matrix.supermatrix;

public record SuperAssemblerMatrixStats(
        int crafterCores,
        int patternCores,
        int speedCores,
        int uploadCores
) {

    public static final int PARALLEL_PER_CRAFTER_CORE = 108;
    public static final int PATTERN_SLOTS_PER_CORE = 72;

    private static final int[] CRAFT_TICKS = {20, 10, 5, 3, 2, 1};

    public int parallelBudget() {
        return this.crafterCores * PARALLEL_PER_CRAFTER_CORE;
    }

    public int patternCapacity() {
        return this.patternCores * PATTERN_SLOTS_PER_CORE;
    }

    public int speedTier() {
        return Math.min(5, this.speedCores);
    }

    public int singleCraftTicks() {
        return CRAFT_TICKS[this.speedTier()];
    }

    public boolean uploadEnabled() {
        return this.uploadCores > 0;
    }
}
