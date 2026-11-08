package com.extendedae_plus.api.bridge;

/**
 * 桥接接口：把「升级槽内容已变更」显式通知到供应器逻辑。
 *
 * <p>复用 appflux 注入的升级槽时，其变更回调不一定覆盖到本模组的处理路径，
 * 装卡后需要主动走一次统一回调，保证 updatePatterns() 与存档标记一定发生。</p>
 */
public interface PatternProviderLogicUpgradeCompatBridge {
    /**
     * 当升级槽内容发生变化时调用此方法。
     * 无论是否安装 AppFlux，所有升级槽变更都应该触发此回调。
     */
    void eap$onCompatUpgradesChangedHook();
}