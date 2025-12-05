package com.extendedae_plus.api.bridge;

/**
 * 桥接接口：用于将升级槽变更事件从 PatternProviderLogicUpgradesMixin 传递到 PatternProviderLogicCompatMixin
 * 实现统一的升级槽变更回调路径
 */
public interface PatternProviderLogicUpgradeCompatBridge {
    /**
     * 当升级槽内容发生变化时调用此方法
     * 无论是否安装 AppFlux，所有升级槽变更都应该触发此回调
     */
    void eap$onCompatUpgradesChangedHook();
}
