package com.extendedae_plus.api.smartDoubling;

public interface ISmartDoublingAwarePattern {
    boolean eap$allowScaling();
    void eap$setAllowScaling(boolean allow);
    /** per-provider scaling limit: 0 means no limit */
    int eap$getScalingLimit();
    void eap$setScalingLimit(int limit);
}
