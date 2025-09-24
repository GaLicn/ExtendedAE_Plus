package com.extendedae_plus.compat;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.ModList;

public final class UpgradeSlotCompat {
    private static Boolean APPFLUX_LOADED;

    private UpgradeSlotCompat() {}

    private static boolean isAppfluxLoaded() {
        if (APPFLUX_LOADED == null) {
            try {
                APPFLUX_LOADED = ModList.get().isLoaded("appflux");
            } catch (Throwable t) {
                // 早期阶段或运行环境差异
                APPFLUX_LOADED = false;
            }
        }
        return APPFLUX_LOADED;
    }

    // 是否由我们提供升级槽（当未安装 appflux 时）
    public static boolean shouldEnableUpgradeSlots() {
        return !isAppfluxLoaded();
    }

    // 是否启用频道卡支持（两种情况下都启用）
    public static boolean shouldEnableChannelCard() {
        return true;
    }

    // 客户端界面是否需要显示升级面板（装/不装 appflux 均显示；
    // appflux 提供的升级槽会以 SlotSemantics.UPGRADE 出现在菜单中，我们只负责渲染面板）
    public static boolean shouldAddUpgradePanelToScreen() {
        return true;
    }
}
