package com.extendedae_plus.api.smartDoubling;

public interface ISmartDoublingAwarePattern {
    boolean eap$allowScaling();
    void eap$setAllowScaling(boolean allow);

    // 翻倍限制：0 表示无限制
    int eap$getMultiplierLimit();
    void eap$setMultiplierLimit(int limit);
}
