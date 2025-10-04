package com.extendedae_plus.util;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 样板供应器UI辅助工具类
 * 用于在样板供应器界面中获取PatternProviderLogic对象
 */
public class PatternProviderUIHelper {

    /**
     * 获取当前打开的样板供应器的PatternProviderLogic
     * 
     * @return PatternProviderLogic对象，如果当前没有打开样板供应器界面则返回null
     */
    public static PatternProviderLogic getCurrentPatternProvider() {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;
        
        if (currentScreen instanceof PatternProviderScreen<?> patternScreen) {
            PatternProviderMenu menu = patternScreen.getMenu();
            
            // 通过反射安全地访问protected字段
            try {
                var logicField = PatternProviderMenu.class.getDeclaredField("logic");
                logicField.setAccessible(true);
                return (PatternProviderLogic) logicField.get(menu);
            } catch (Exception e) {
                // 如果反射失败，返回null
                return null;
            }
        }
        
        return null;
    }

    /**
     * 检查当前是否打开了样板供应器界面
     * 
     * @return 如果当前打开的是样板供应器界面则返回true
     */
    public static boolean isPatternProviderScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof PatternProviderScreen;
    }

    /**
     * 获取当前样板供应器界面的Screen对象
     * 
     * @return PatternProviderScreen对象，如果当前没有打开样板供应器界面则返回null
     */
    public static PatternProviderScreen<?> getCurrentPatternProviderScreen() {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;
        
        if (currentScreen instanceof PatternProviderScreen<?> patternScreen) {
            return patternScreen;
        }
        
        return null;
    }

    /**
     * 在当前样板供应器中执行样板数量倍增
     * 
     * @param multiplier 倍数（必须大于0）
     * @return 缩放操作结果，如果当前没有打开样板供应器界面则返回null
     */
    public static PatternProviderDataUtil.PatternScalingResult multiplyCurrentPatternAmounts(double multiplier) {
        PatternProviderLogic patternProvider = getCurrentPatternProvider();
        if (patternProvider == null) {
            return null;
        }
        
        return PatternProviderDataUtil.multiplyPatternAmounts(patternProvider, multiplier);
    }

    /**
     * ExtendedAE风格的样板复制倍增
     * 提供更好的错误处理和恢复机制
     * 
     * @param multiplier 倍数（必须大于0）
     * @return 缩放操作结果，如果当前没有打开样板供应器界面则返回null
     */
    public static PatternProviderDataUtil.PatternScalingResult duplicateCurrentPatternAmountsExtendedAEStyle(double multiplier) {
        PatternProviderLogic patternProvider = getCurrentPatternProvider();
        if (patternProvider == null) {
            return null;
        }
        
        return PatternProviderDataUtil.duplicatePatternAmountsExtendedAEStyle(patternProvider, multiplier);
    }

    /**
     * 在当前样板供应器中执行样板数量倍除
     * 
     * @param divisor 除数（必须大于0）
     * @return 缩放操作结果，如果当前没有打开样板供应器界面则返回null
     */
    public static PatternProviderDataUtil.PatternScalingResult divideCurrentPatternAmounts(double divisor) {
        PatternProviderLogic patternProvider = getCurrentPatternProvider();
        if (patternProvider == null) {
            return null;
        }
        
        return PatternProviderDataUtil.dividePatternAmounts(patternProvider, divisor);
    }

    /**
     * 预览当前样板供应器的缩放效果
     * 
     * @param scaleFactor 缩放因子
     * @return 预览结果列表，如果当前没有打开样板供应器界面则返回空列表
     */
    public static java.util.List<PatternProviderDataUtil.PatternScalingPreview> previewCurrentPatternScaling(double scaleFactor) {
        PatternProviderLogic patternProvider = getCurrentPatternProvider();
        if (patternProvider == null) {
            return new java.util.ArrayList<>();
        }
        
        return PatternProviderDataUtil.previewPatternScaling(patternProvider, scaleFactor);
    }
}
