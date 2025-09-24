package com.extendedae_plus.compat;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.widgets.UpgradesPanel;
import com.extendedae_plus.util.ExtendedAELogger;

/**
 * AppliedFlux 兼容性处理工具类
 * 用于检测和处理与AppliedFlux模组的UI冲突
 */
public final class AppliedFluxCompat {
    
    private AppliedFluxCompat() {}
    
    /**
     * 检查PatternProviderScreen是否已经有AppliedFlux添加的升级面板
     * 简化版本：主要通过AppliedFlux模组加载状态来判断
     */
    public static boolean hasAppliedFluxUpgradePanel(PatternProviderScreen<?> screen) {
        // 如果AppliedFlux未加载，肯定没有其升级面板
        if (!UpgradeSlotCompat.shouldUseLowPriorityMode()) {
            return false;
        }
        
        // 如果AppliedFlux加载了，假设它会添加升级面板
        // 这是一个保守的假设，避免冲突
        ExtendedAELogger.LOGGER.debug("[AppliedFlux兼容] 检测到AppliedFlux已加载，假设存在升级面板");
        return true;
    }
    
    /**
     * 检查是否应该跳过添加我们的升级面板
     */
    public static boolean shouldSkipOurUpgradePanel(PatternProviderScreen<?> screen) {
        if (!UpgradeSlotCompat.shouldUseLowPriorityMode()) {
            return false;
        }
        
        return hasAppliedFluxUpgradePanel(screen);
    }
}
