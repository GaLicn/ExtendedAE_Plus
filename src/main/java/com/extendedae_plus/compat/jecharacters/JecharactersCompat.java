package com.extendedae_plus.compat.jecharacters;

import me.towdium.jecharacters.utils.Match;

/**
 * Just Enough Characters 的拼音匹配桥接。
 *
 * <p>本类直接引用 {@code me.towdium.jecharacters.utils.Match}，加载即需要 JEC 在场，
 * 因此只能在 {@link com.extendedae_plus.util.ModCheckUtils#isJecharactersLoading()}
 * 为真之后调用。方法签名只使用 JDK 类型，避免把 JEC 类型带进调用方的 StackMapTable。</p>
 */
public final class JecharactersCompat {
    private JecharactersCompat() {
    }

    /**
     * 拼音/模糊包含匹配，语义与原反射调用一致：先做原样匹配，失败再做一次小写化匹配。
     *
     * <p>显式转型为 {@link CharSequence} 以命中
     * {@code Match.contains(CharSequence, CharSequence)} 这一重载。</p>
     *
     * @param name 供应器显示名
     * @param key  搜索关键字
     * @return 是否匹配
     */
    public static boolean contains(String name, String key) {
        if (name == null || key == null) {
            return false;
        }
        if (Match.contains((CharSequence) name, (CharSequence) key)) {
            return true;
        }
        return Match.contains((CharSequence) name.toLowerCase(), (CharSequence) key.toLowerCase());
    }
}
