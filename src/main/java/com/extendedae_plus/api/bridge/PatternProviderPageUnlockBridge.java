package com.extendedae_plus.api.bridge;

/**
 * 暴露样板供应器当前已解锁的页数与槽位数。
 */
public interface PatternProviderPageUnlockBridge {
    boolean eap$isExtendedPatternProviderHost();

    int eap$getUnlockedPatternPages();

    int eap$getUnlockedPatternSlots();
}
