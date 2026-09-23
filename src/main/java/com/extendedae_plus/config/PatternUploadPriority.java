package com.extendedae_plus.config;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * 图样编码终端自动上传的目标优先级。
 *
 * <p>自动上传每次只会向一个目标写入样板：优先目标接受后即结束，仅在优先目标
 * 拒绝时回退到另一个目标。因此该枚举决定的是尝试顺序，而不是同时上传两个目标。</p>
 *
 * <p>{@link #MATRIX} 为默认值：装配矩阵是既有行为，ECO 作为可选回退目标。</p>
 *
 * <p>实现 {@link TranslatableEnum} 以便 NeoForge 配置界面按语言文件显示条目名称；
 * 缺少该实现时界面只会渲染枚举常量名。</p>
 */
public enum PatternUploadPriority implements TranslatableEnum {
    /** 优先上传到 ExtendedAE 装配矩阵（默认），矩阵不可用时回退到 ECO 合成系统。 */
    MATRIX("extendedae_plus.configuration.uploadPriority.MATRIX"),

    /** 优先上传到 ECO 合成系统，ECO 不可用时回退到 ExtendedAE 装配矩阵。 */
    ECO("extendedae_plus.configuration.uploadPriority.ECO");

    private final String translationKey;

    PatternUploadPriority(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public Component getTranslatedName() {
        return Component.translatable(translationKey);
    }
}
