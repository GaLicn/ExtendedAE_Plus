package com.extendedae_plus.compat;

import me.towdium.jecharacters.utils.Match;

/**
 * Just Enough Characters（jecharacters）兼容层。
 *
 * <p>本类直接引用 JEC 的 {@link Match}，JVM 在链接本类时即须解析该类型，
 * 因此调用方必须先经 {@code ModCheckUtils.isLoaded(ModCheckUtils.MODID_JEC)} 判定模组已加载，
 * 再调用本类方法。缺少该判定的调用会在守卫位置抛出 {@link NoClassDefFoundError}。</p>
 *
 * <p>约束：判定必须写在调用方且使用 {@code ModCheckUtils.isLoaded(MODID_JEC)}。</p>
 */
public final class JecharactersCompat {

    private JecharactersCompat() {
    }

    /**
     * JEC 拼音/模糊匹配，对应 {@code Match.contains(String, CharSequence)}。
     *
     * @param text 被搜索的文本（供应器显示名）
     * @param key  搜索关键字
     * @return 是否命中
     */
    public static boolean contains(String text, String key) {
        return Match.contains(text, key);
    }

    /**
     * 大小写不敏感的 JEC 匹配，对应 {@code Match.contains(CharSequence, CharSequence, boolean)}，
     * 由 JEC 内部对双方统一转小写后再匹配。
     *
     * @param text 被搜索的文本（供应器显示名）
     * @param key  搜索关键字
     * @return 是否命中
     */
    public static boolean containsIgnoreCase(String text, String key) {
        return Match.contains(text, key, true);
    }
}
