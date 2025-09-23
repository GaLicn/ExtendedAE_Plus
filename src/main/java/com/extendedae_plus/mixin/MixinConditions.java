package com.extendedae_plus.mixin;

import net.minecraftforge.fml.ModList;
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
        // 对于升级相关的Mixin，检查appflux是否存在
        if (mixinClassName.contains("PatternProviderMenuUpgradesMixin") ||
            mixinClassName.contains("PatternProviderScreenUpgradesMixin") ||
            mixinClassName.contains("PatternProviderLogicUpgradesMixin") ||
            mixinClassName.contains("PatternProviderLogicHostUpgradesMixin")) {
            
            try {
                // 检查ModList是否已初始化
                if (net.minecraftforge.fml.ModList.get() == null) {
                    System.out.println("[ExtendedAE_Plus] ModList未初始化，默认应用升级Mixin: " + mixinClassName);
                    return true; // 修改策略：未初始化时默认应用，运行时再检查
                }
                
                boolean appfluxExists = net.minecraftforge.fml.ModList.get().isLoaded("appflux");
                boolean shouldApply = !appfluxExists;
                
                System.out.println("[ExtendedAE_Plus] 升级Mixin检查: " + mixinClassName + 
                                 ", appflux存在: " + appfluxExists + 
                                 ", 应用Mixin: " + shouldApply);
                
                return shouldApply;
            } catch (Exception e) {
                System.out.println("[ExtendedAE_Plus] ModList检查失败，默认应用升级Mixin: " + mixinClassName);
                return true; // 修改策略：出错时默认应用，运行时再检查
            }
        }
        
        // 对于appflux相关的Mixin，总是加载但在运行时检查条件
        if (mixinClassName.contains("AppfluxPatternProviderLogicMixin")) {
            System.out.println("[ExtendedAE_Plus] 总是加载appflux Mixin，运行时检查条件: " + mixinClassName);
            return true; // 总是加载，在Mixin内部进行运行时检查
        }
        
        // 其他Mixin正常应用
        System.out.println("[ExtendedAE_Plus] 加载Mixin: " + mixinClassName);
        return true;
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
