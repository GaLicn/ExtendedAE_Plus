package com.extendedae_plus.api.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.util.Map;

public interface IManualCraftingState {
    void eap$setManualWaiting(KeyCounter manualWaiting);

    long eap$getManualWaitingAmount(AEKey what);

    Map<AEKey, Long> eap$getManualWaitingSnapshot();
}
