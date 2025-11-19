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
            // === MAE2 兼容 ===
            if (mixinClassName.contains("CraftingCPUClusterMixin")) {
                return !ModCheckUtils.isLoaded(ModCheckUtils.MODID_MAE2);
            }

            // === AAE 兼容 ===
            if (mixinClassName.startsWith("com.extendedae_plus.mixin.advancedae")) {
                return ModCheckUtils.isLoaded(ModCheckUtils.MODID_AAE);
            }

            // === GuideME 版本兼容 ===
            if (mixinClassName.startsWith("com.extendedae_plus.mixin.guideme.")) {
                return ModCheckUtils.isLoadedAndLowerThan(ModCheckUtils.MODID_GUIDEME, "20.1.14");
            }

            if (mixinClassName.contains("CraftConfirmScreen")) {
                return !ModCheckUtils.isLoaded(ModCheckUtils.MODID_EPA);
            }

            return true;
        } catch (Exception e) {
            System.err.println("[ExtendedAE_Plus] 检查 Mixin 条件时出错: " + e.getMessage());
            return true; // 出错默认加载，避免意外禁用
        }
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
