package com.extendedae_plus.content.matrix.supermatrix;

public record SuperAssemblerMatrixStats(
        int crafterCores,
        int patternCores,
        int speedCores,
        int uploadCores,
        int fixedParallelBudget,
        int fixedPatternCapacity
) {

    public static final int PARALLEL_PER_CRAFTER_CORE = 512;
    public static final int PATTERN_SLOTS_PER_CORE = 72;

    private static final int[] CRAFT_TICKS = {20, 10, 5, 3, 2, 1};

    public SuperAssemblerMatrixStats(int crafterCores, int patternCores, int speedCores, int uploadCores) {
        this(crafterCores, patternCores, speedCores, uploadCores, -1, -1);
    }

    public static SuperAssemblerMatrixStats ultimate() {
        return new SuperAssemblerMatrixStats(0, 0, 5, 4,
                UltimateSuperAssemblerMatrixStructure.PARALLEL_BUDGET,
                UltimateSuperAssemblerMatrixStructure.PATTERN_CAPACITY);
    }

    public int parallelBudget() {
        return this.fixedParallelBudget >= 0 ? this.fixedParallelBudget : this.crafterCores * PARALLEL_PER_CRAFTER_CORE;
    }

    public int patternCapacity() {
        return this.fixedPatternCapacity >= 0 ? this.fixedPatternCapacity : this.patternCores * PATTERN_SLOTS_PER_CORE;
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
