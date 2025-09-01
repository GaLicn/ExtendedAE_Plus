package com.extendedae_plus.content;

import appeng.api.crafting.IPatternDetails;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class PatternHighlightStore {
    // 使用同步的 WeakHashMap 存储高亮状态，键为 IPatternDetails，值为 Boolean
    private static final Map<IPatternDetails, Boolean> HIGHLIGHTS = Collections.synchronizedMap(new WeakHashMap<>());

    // 私有构造方法，防止实例化
    private PatternHighlightStore() {}

    /**
     * 设置指定 details 的高亮状态。
     * @param details 需要设置的 IPatternDetails 实例
     * @param highlighted 是否高亮
     */
    public static void setHighlight(IPatternDetails details, boolean highlighted) {
        if (details == null) return;
        if (highlighted) {
            HIGHLIGHTS.put(details, Boolean.TRUE); // 设置为高亮
        } else {
            HIGHLIGHTS.remove(details); // 移除高亮
        }
    }

    /**
     * 获取指定 details 的高亮状态。
     * @param details 需要查询的 IPatternDetails 实例
     * @return 是否高亮
     */
    public static boolean getHighlight(IPatternDetails details) {
        if (details == null) return false;
        Boolean v = HIGHLIGHTS.get(details);
        return v != null && v;
    }

    /**
     * 清空所有高亮状态（在供应器界面关闭时调用）。
     */
    public static void clearAll() {
        HIGHLIGHTS.clear();
    }
}