package com.extendedae_plus.mixin;

import com.extendedae_plus.util.ModCheckUtils;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin条件加载插件
 * 用于根据模组存在情况动态加载不同的Mixin
 */
public class MixinConditions implements IMixinConfigPlugin {

    private static final String MODID_GTOCORE = "gtocore";
    
    @Override
    public void onLoad(String mixinPackage) {
        // 初始化时调用
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            // GTOCore 已重写这些 AE2 类，旧注入点会与其同优先级合并并直接失效。
            if (ModCheckUtils.isLoaded(MODID_GTOCORE) && isGtoCoreConflict(mixinClassName)) {
                return false;
            }

            // EMI 版本变化后旧版 BoM 注入点已不存在，保留其它 EMI 集成功能。
            if (!ModCheckUtils.isLoaded(ModCheckUtils.MODID_EMI)
                    && mixinClassName.startsWith("com.extendedae_plus.mixin.emi.")) {
                return false;
            }

            // 整合包使用 EMI 1.1.24，旧 BoM render 注入点已被移除。
            if (mixinClassName.equals("com.extendedae_plus.mixin.emi.BoMScreenMixin")
                    && !ModCheckUtils.isLoadedAndLowerThan(ModCheckUtils.MODID_EMI, "1.1.24")) {
                return false;
            }

            // JEI 15.20 起 BookmarkOverlay 不再暴露旧 contents 字段。
            if (mixinClassName.equals("com.extendedae_plus.mixin.jei.BookmarkOverlayMixin")
                    && !ModCheckUtils.isLoadedAndLowerThan(ModCheckUtils.MODID_JEI, "15.20.0")) {
                return false;
            }

            // === MAE2 兼容 ===
            if (mixinClassName.contains("CraftingCPUClusterMixin")) {
                return !ModCheckUtils.isLoaded(ModCheckUtils.MODID_MAE2);
            }

            // === AAE 兼容 ===
            if (mixinClassName.startsWith("com.extendedae_plus.mixin.advancedae")) {
                return ModCheckUtils.isLoaded(ModCheckUtils.MODID_AAE);
            }

            // WTLib 兼容目标类只在 WTLib 存在时应用，避免伪目标解析触发硬依赖。
            if (mixinClassName.startsWith("com.extendedae_plus.mixin.ae2WTlib")) {
                return ModCheckUtils.isLoaded(ModCheckUtils.MODID_AE2WTLIB);
            }

            // === AppFlux 兼容 ===
            if (mixinClassName.startsWith("com.extendedae_plus.mixin.appflux")) {
                return ModCheckUtils.isLoaded(ModCheckUtils.MODID_APPFLUX);
            }

            // === JEI 兼容 ===
            if (mixinClassName.startsWith("com.extendedae_plus.mixin.jei.")) {
                return ModCheckUtils.isLoaded("jei");
            }

            // === GuideME 版本兼容 ===
            if (mixinClassName.startsWith("com.extendedae_plus.mixin.guideme.")) {
                return ModCheckUtils.isLoadedAndLowerThan(ModCheckUtils.MODID_GUIDEME, "20.1.14");
            }

            return true;
        } catch (Exception e) {
            System.err.println("[ExtendedAE_Plus] 检查 Mixin 条件时出错: " + e.getMessage());
            return true; // 出错默认加载，避免意外禁用
        }
    }

    private static boolean isGtoCoreConflict(String mixinClassName) {
        return mixinClassName.equals("com.extendedae_plus.mixin.ae2.autopattern.PatternProviderLogicContainsModifyMixin")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.helpers.patternprovider.PatternProviderLogicAdvancedMixin")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.compat.PatternProviderLogicCompatMixin")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.crafting.CraftingCpuLogicManualWaitingMixin")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.autopattern.CraftingCpuLogicPatternPowerMixin")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.accessor.CraftingCpuLogicAccessor")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobAccessor")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.accessor.ExecutingCraftingJobTaskProgressAccessor")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.autopattern.CraftingSimulationStateAccessor")
                || mixinClassName.equals("com.extendedae_plus.mixin.ae2.autopattern.CraftingSimulationStateMixin");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 接受目标类
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 应用前调用
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 应用后调用
    }
}
