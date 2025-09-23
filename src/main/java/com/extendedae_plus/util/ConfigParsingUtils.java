package com.extendedae_plus.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 配置解析工具类：用于解析黑名单与倍率配置的字符串
 */
public final class ConfigParsingUtils {
    private ConfigParsingUtils() {}

    public static final class MultiplierEntry {
        public final Pattern pattern;
        public final double multiplier;

        public MultiplierEntry(Pattern pattern, double multiplier) {
            this.pattern = pattern;
            this.multiplier = multiplier;
        }
    }

    /**
     * 编译用户提供的匹配串。支持简单的 glob 语法（'*' 和 '?'）以及完整的正则表达式。
     * 规则：
     * - 如果字符串包含 ".*" 或其他正则元字符，优先尝试按正则编译（若失败则回退到 glob 转换）。
     * - 否则若包含 '*' 或 '?'，将按 glob 语法转换为正则。
     * - 否则按字面量匹配处理。
     */
    public static Pattern compilePattern(String raw) {
        if (raw == null) throw new IllegalArgumentException("pattern is null");
        raw = raw.trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("pattern is empty");

        // If it looks like regex (contains '.*' or regex metachar), try regex first
        if (raw.contains(".*") || raw.matches(".*[\\[\\(\\+\\{\\\\].*")) {
            try {
                return Pattern.compile("^" + raw + "$");
            } catch (PatternSyntaxException ignored) {
                // fallback to glob below
            }
        }

        // If contains glob chars, convert to regex
        if (raw.contains("*") || raw.contains("?")) {
            StringBuilder sb = new StringBuilder();
            sb.append('^');
            for (char c : raw.toCharArray()) {
                switch (c) {
                    case '*': sb.append(".*"); break;
                    case '?': sb.append('.'); break;
                    default:
                        // escape regex special chars
                        if (".\\+[]{}()^$|".indexOf(c) >= 0) {
                            sb.append('\\');
                        }
                        sb.append(c);
                }
            }
            sb.append('$');
            return Pattern.compile(sb.toString());
        }

        // Otherwise treat as a literal (match exact)
        return Pattern.compile("^" + Pattern.quote(raw) + "$");
    }

    /**
     * Parse multiplier entries like 'modid:block 2x' into MultiplierEntry objects.
     * Accepts values with optional trailing 'x' (case-insensitive).
     */
    public static MultiplierEntry parseMultiplierEntry(String entry) {
        if (entry == null) return null;
        String[] parts = entry.trim().split("\\s+");
        if (parts.length < 2) return null;
        String key = parts[0];
        String val = parts[1].toLowerCase();
        if (val.endsWith("x")) val = val.substring(0, val.length() - 1);
        double m;
        try {
            m = Double.parseDouble(val);
        } catch (NumberFormatException ex) {
            return null;
        }
        try {
            Pattern p = compilePattern(key);
            return new MultiplierEntry(p, m);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<Pattern> compilePatterns(List<? extends String> raw) {
        List<Pattern> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            try { out.add(compilePattern(s)); } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    public static List<MultiplierEntry> parseMultiplierList(List<? extends String> raw) {
        List<MultiplierEntry> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            MultiplierEntry me = parseMultiplierEntry(s);
            if (me != null) out.add(me);
        }
        return out;
    }

    // ------------------ 全局缓存与接口 ------------------
    private static volatile List<Pattern> cachedBlacklist = null;
    private static volatile List<MultiplierEntry> cachedMultiplierEntries = null;
    private static volatile List<String> cachedBlacklistSourceSnapshot = null;
    private static volatile List<String> cachedMultiplierSourceSnapshot = null;
    private static final Object CACHE_LOCK = new Object();

    /**
     * 获取已解析并缓存的黑名单（线程安全、懒加载）。
     */
    public static List<Pattern> getCachedBlacklist(java.util.List<? extends String> source) {
        List<String> normalized = normalizeSource(source);

        // fast path: identical snapshot reference or equal contents
        if (cachedBlacklist != null && listEquals(cachedBlacklistSourceSnapshot, normalized)) {
            return Collections.unmodifiableList(cachedBlacklist);
        }

        synchronized (CACHE_LOCK) {
            if (cachedBlacklist == null || !listEquals(cachedBlacklistSourceSnapshot, normalized)) {
                cachedBlacklist = compilePatterns(normalized);
                cachedBlacklistSourceSnapshot = normalized.isEmpty() ? Collections.emptyList() : new ArrayList<>(normalized);
            }
            return Collections.unmodifiableList(cachedBlacklist);
        }
    }

    /**
     * 获取已解析并缓存的倍率列表（线程安全、懒加载）。
     */
    public static List<MultiplierEntry> getCachedMultiplierEntries(java.util.List<? extends String> source) {
        List<String> normalized = normalizeSource(source);

        if (cachedMultiplierEntries != null && listEquals(cachedMultiplierSourceSnapshot, normalized)) {
            return Collections.unmodifiableList(cachedMultiplierEntries);
        }

        synchronized (CACHE_LOCK) {
            if (cachedMultiplierEntries == null || !listEquals(cachedMultiplierSourceSnapshot, normalized)) {
                cachedMultiplierEntries = parseMultiplierList(normalized);
                cachedMultiplierSourceSnapshot = normalized.isEmpty() ? Collections.emptyList() : new ArrayList<>(normalized);
            }
            return Collections.unmodifiableList(cachedMultiplierEntries);
        }
    }

    // Normalize the incoming source list: trim entries, drop blanks, keep stable ordering
    private static List<String> normalizeSource(java.util.List<? extends String> source) {
        List<String> out = new ArrayList<>();
        if (source == null) return out;
        for (String s : source) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    // Null-safe equality for two lists of strings. Uses size + element equals.
    private static boolean listEquals(List<String> a, List<String> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    /**
     * 清空缓存，下一次获取时将重新从提供的源解析（或调用方可以重新调用 getter）。
     */
    public static void reload() {
        synchronized (CACHE_LOCK) {
            cachedBlacklist = null;
            cachedMultiplierEntries = null;
        }
    }
}


