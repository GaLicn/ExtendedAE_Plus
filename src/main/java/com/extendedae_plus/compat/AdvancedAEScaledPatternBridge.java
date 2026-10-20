package com.extendedae_plus.compat;

import appeng.crafting.pattern.AEProcessingPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.crafting.ScaledProcessingPatternAdv;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * AdvancedAE 缩放样板的桥接入口。
 *
 * <p>本类与 {@link ScaledProcessingPatternAdv} 都直接引用 AdvancedAE 的类型，因此只允许在
 * {@code ModCheckUtils.isAAELoading()} 为真时触碰。类本身没有 AAE 类型的字段或父类，
 * 对 AAE 类型的解析推迟到首次调用方法时（JVM 懒解析），未安装 AAE 时加载本类不会失败。</p>
 */
public final class AdvancedAEScaledPatternBridge {

    /**
     * 探测 AdvancedAE 扩展样板是否可用，同时完成类链接的预热。
     *
     * <p>只在启动阶段调用一次：此后 {@link #tryCreate} 走普通 Java 调用，不经过反射。</p>
     */
    public static boolean isAvailable() {
        try {
            // 引用字面量会触发 ScaledProcessingPatternAdv 的加载与链接，
            // AAE 缺失或与编译版本不兼容时在这里抛出 LinkageError。
            Class<?> patternClass = ScaledProcessingPatternAdv.class;
            patternClass.getConstructor(AEProcessingPattern.class, long.class);
            return true;
        } catch (LinkageError | ReflectiveOperationException e) {
            // AAE 在场但类结构对不上（版本不匹配或依赖缺失），退回普通样板。
            EAP$LOGGER.warn("[样板缩放] AdvancedAE 扩展样板不可用，退回普通样板", e);
            return false;
        }
    }

    /**
     * 创建 AdvancedAE 扩展样板。
     *
     * @return 不是 AdvancedAE 样板时返回 {@code null}，由调用方回退到普通样板
     */
    public static ScaledProcessingPattern tryCreate(AEProcessingPattern base, long multiplier) {
        return base instanceof AdvProcessingPattern adv
                ? new ScaledProcessingPatternAdv(adv, multiplier)
                : null;
    }

    private AdvancedAEScaledPatternBridge() {}
}
