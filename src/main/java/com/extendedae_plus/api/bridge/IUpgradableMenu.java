package com.extendedae_plus.api.bridge;

import appeng.menu.ToolboxMenu;

/**
 * 提供给 AE2 菜单以暴露升级槽与工具箱面板的接口。
 * PatternProviderMenu 的 mixin 会在未安装 appflux 时实现该接口，
 * 以便在界面上显示升级卡槽（用于放置频道卡）。
 */
public interface IUpgradableMenu {
    ToolboxMenu eap$getToolbox();
}
