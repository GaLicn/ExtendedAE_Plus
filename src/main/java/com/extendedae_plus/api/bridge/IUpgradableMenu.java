package com.extendedae_plus.api.bridge;

import appeng.menu.ToolboxMenu;

/** 向客户端界面暴露本地升级槽对应的工具箱。 */
public interface IUpgradableMenu {
    ToolboxMenu eap$getToolbox();
}
