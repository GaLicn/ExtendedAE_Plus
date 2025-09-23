package com.extendedae_plus.compat;

import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraftforge.fml.ModList;

/**
 * 兼容性测试类
 * 用于验证模组兼容性检测是否正常工作
 */
public class CompatibilityTest {
    
    /**
     * 测试模组兼容性检测
     */
    public static void testCompatibility() {
        ExtendedAELogger.LOGGER.info("=== ExtendedAE_Plus 兼容性测试开始 ===");
        
        // 测试appflux模组检测
        boolean appfluxExists = ModList.get().isLoaded("appflux");
        ExtendedAELogger.LOGGER.info("ExtendedAE-appflux模组检测结果: {}", appfluxExists ? "存在" : "不存在");
        
        // 测试升级卡槽功能启用状态
        boolean shouldEnableUpgrades = UpgradeSlotCompat.shouldEnableUpgradeSlots();
        ExtendedAELogger.LOGGER.info("升级卡槽功能启用状态: {}", shouldEnableUpgrades ? "启用" : "禁用");
        
        // 测试Screen升级面板添加状态
        boolean shouldAddPanel = UpgradeSlotCompat.shouldAddUpgradePanelToScreen();
        ExtendedAELogger.LOGGER.info("Screen升级面板添加状态: {}", shouldAddPanel ? "启用" : "禁用");
        
        // 输出兼容性策略
        if (appfluxExists) {
            ExtendedAELogger.LOGGER.info("兼容性策略: 检测到ExtendedAE-appflux模组，将使用其升级卡槽功能");
        } else {
            ExtendedAELogger.LOGGER.info("兼容性策略: 未检测到ExtendedAE-appflux模组，将使用我们自己的升级卡槽功能");
        }
        
        ExtendedAELogger.LOGGER.info("=== ExtendedAE_Plus 兼容性测试完成 ===");
    }
    
    /**
     * 获取兼容性状态报告
     */
    public static String getCompatibilityReport() {
        boolean appfluxExists = ModList.get().isLoaded("appflux");
        boolean upgradesEnabled = UpgradeSlotCompat.shouldEnableUpgradeSlots();
        
        StringBuilder report = new StringBuilder();
        report.append("ExtendedAE_Plus 兼容性报告:\n");
        report.append("- ExtendedAE-appflux模组: ").append(appfluxExists ? "已安装" : "未安装").append("\n");
        report.append("- 升级卡槽功能: ").append(upgradesEnabled ? "启用中" : "已禁用").append("\n");
        
        if (appfluxExists && !upgradesEnabled) {
            report.append("- 兼容性状态: 正常 (使用appflux的升级功能)\n");
        } else if (!appfluxExists && upgradesEnabled) {
            report.append("- 兼容性状态: 正常 (使用我们的升级功能)\n");
        } else {
            report.append("- 兼容性状态: 异常 (配置不一致)\n");
        }
        
        return report.toString();
    }
}
