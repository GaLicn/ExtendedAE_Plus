package com.extendedae_plus.config;

/**
 * 图样编码终端自动上传的目标优先级。
 *
 * <p>自动上传每次只会向一个目标写入样板：优先目标接受后即结束，仅在优先目标
 * 拒绝时回退到另一个目标。因此该枚举决定的是尝试顺序，而不是同时上传两个目标。</p>
 *
 * <p>{@link #MATRIX} 为默认值：装配矩阵是既有行为，ECO 作为可选回退目标。</p>
 */
public enum PatternUploadPriority {
    /** 优先上传到 ExtendedAE 装配矩阵（默认），矩阵不可用时回退到 ECO 合成系统。 */
    MATRIX,

    /** 优先上传到 ECO 合成系统，ECO 不可用时回退到 ExtendedAE 装配矩阵。 */
    ECO
}
