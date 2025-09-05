package com.extendedae_plus.content;

import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 客户端本地的高亮存储，仅作用于接收该客户端。
 * 使用 AEKey 作为标识，渲染时可通过 AEKey 与本地解码的 IPatternDetails 比对。
 */
public final class ClientPatternHighlightStore {
    private static final Set<AEKey> HIGHLIGHTS = Collections.synchronizedSet(new HashSet<>());

    private ClientPatternHighlightStore() {}

    public static void setHighlight(AEKey key, boolean highlight) {
        if (key == null) return;
        if (highlight) HIGHLIGHTS.add(key);
        else HIGHLIGHTS.remove(key);
    }

    public static boolean hasHighlight(AEKey key) {
        if (key == null) return false;
        return HIGHLIGHTS.contains(key);
    }

    public static void clearAll() { HIGHLIGHTS.clear(); }
}


