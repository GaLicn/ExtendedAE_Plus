package com.extendedae_plus.content;

import appeng.api.stacks.AEKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientManualCraftingStatusStore {
    private static volatile int containerId = -1;
    private static volatile Map<AEKey, Long> manualWaiting = Collections.emptyMap();

    private ClientManualCraftingStatusStore() {
    }

    public static void setStatus(int menuContainerId, Map<AEKey, Long> snapshot) {
        containerId = menuContainerId;
        manualWaiting = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }

    public static long getManualWaitingAmount(int menuContainerId, AEKey key) {
        if (key == null || menuContainerId != containerId) {
            return 0;
        }
        return manualWaiting.getOrDefault(key, 0L);
    }

    public static void clear() {
        containerId = -1;
        manualWaiting = Collections.emptyMap();
    }
}
