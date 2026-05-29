package com.extendedae_plus.api.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.util.Map;

public interface IForcedCraftingPlan {
    KeyCounter eap$getManualMissingItems();

    Map<AEKey, Long> eap$getManualMissingSnapshot();
}
