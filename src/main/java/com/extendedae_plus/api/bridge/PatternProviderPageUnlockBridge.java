package com.extendedae_plus.api.bridge;

/**
 * 暴露样板供应器当前可用页数/槽位数。
 * 主要用于扩展样板供应器的扩容卡逻辑与镜像同步限制。
 */
public interface PatternProviderPageUnlockBridge {
    boolean eap$isExtendedPatternProviderHost();

    int eap$getUnlockedPatternPages();

    int eap$getUnlockedPatternSlots();

    /** 返回旧倍率页迁移后保留的槽位数；新放置的供应器始终为 0。 */
    int eap$getLegacyUnlockedPatternSlots();
}
