package com.extendedae_plus.compat;

import appeng.api.upgrades.IUpgradeInventory;
import net.minecraftforge.fml.ModList;

/**
 * 升级卡槽兼容性管理类
 * 检测ExtendedAE-appflux模组是否存在，如果存在则使用其升级卡槽功能
 * 否则使用我们自己的实现
 */
public class UpgradeSlotCompat {
    private static final String APPFLUX_MOD_ID = "appflux";
    
    /**
     * 检测Applied Flux模组是否存在
     * @return true如果存在，false如果不存在
     */
    public static boolean isAppfluxPresent() {
        return ModList.get().isLoaded(APPFLUX_MOD_ID);
    }
    
    /**
     * 检测是否应该启用我们的升级卡槽功能
     * @return true如果应该启用，false如果检测到appflux模组存在
     */
    public static boolean shouldEnableUpgradeSlots() {
        boolean appfluxExists = isAppfluxPresent();
        return !appfluxExists;
    }
    
    /**
     * 检测是否应该启用频道卡功能
     * 频道卡是我们独有的功能，即使appflux存在也应该启用
     * @return 总是返回true，因为频道卡功能不与appflux冲突
     */
    public static boolean shouldEnableChannelCard() {
        return true; // 频道卡功能总是启用，因为appflux没有实现这个功能
    }
    
    /**
     * 检测是否应该在Screen中添加升级面板
     * @return true如果应该添加，false如果检测到appflux模组存在
     */
    public static boolean shouldAddUpgradePanelToScreen() {
        return shouldEnableUpgradeSlots();
    }

    /**
     * 兼容性升级菜单接口
     */
    public interface IUpgradeableMenuCompat {
        IUpgradeInventory getCompatUpgrades();
    }
}
