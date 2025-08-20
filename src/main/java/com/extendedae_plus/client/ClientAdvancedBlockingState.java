package com.extendedae_plus.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientAdvancedBlockingState {
    private static final Map<String, Boolean> states = new ConcurrentHashMap<>();

    private ClientAdvancedBlockingState() {}

    public static String key(String dimension, long blockPosLong) {
        return dimension + "@" + blockPosLong;
    }

    public static void set(String key, boolean v) {
        states.put(key, v);
        System.out.println("[EPP][CLIENT][S2C] Received advancedBlocking key=" + key + ", value=" + v);
    }

    public static boolean has(String key) {
        return states.containsKey(key);
    }

    public static boolean get(String key) {
        return states.getOrDefault(key, false);
    }
}
