package com.extendedae_plus.util.entitySpeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

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
     */
    public static Pattern compilePattern(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            LOGGER.warn("Invalid pattern: {}", raw);
            throw new IllegalArgumentException("Pattern is null or empty");
        }
        raw = raw.trim();

        // Try regex first if it contains regex metacharacters
        if (raw.contains(".*") || raw.matches(".*[\\[\\(\\+\\{\\\\].*")) {
            try {
                return Pattern.compile("^" + raw + "$");
            } catch (PatternSyntaxException e) {
                LOGGER.warn("Failed to compile regex pattern '{}': {}", raw, e.getMessage());
                // Fallback to glob
            }
        }

        // Convert glob to regex
        if (raw.contains("*") || raw.contains("?")) {
            StringBuilder sb = new StringBuilder("^");
            for (char c : raw.toCharArray()) {
                switch (c) {
                    case '*': sb.append(".*"); break;
                    case '?': sb.append('.'); break;
                    default:
                        if (".\\+[]{}()^$|".indexOf(c) >= 0) {
                            sb.append('\\');
                        }
                        sb.append(c);
                }
            }
            sb.append('$');
            return Pattern.compile(sb.toString());
        }

        // Literal match
        return Pattern.compile("^" + Pattern.quote(raw) + "$");
    }

    /**
     * 解析倍率条目，如 'modid:block 2x'。
     */
    public static MultiplierEntry parseMultiplierEntry(String entry) {
        if (entry == null || entry.trim().isEmpty()) return null;
        String[] parts = entry.trim().split("\\s+");
        if (parts.length < 2) {
            LOGGER.warn("Invalid multiplier entry: {}", entry);
            return null;
        }
        String key = parts[0];
        String val = parts[1].toLowerCase();
        if (val.endsWith("x")) val = val.substring(0, val.length() - 1);
        double multiplier;
        try {
            multiplier = Double.parseDouble(val);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid multiplier value in '{}': {}", entry, val);
            return null;
        }
        try {
            Pattern pattern = compilePattern(key);
            return new MultiplierEntry(pattern, multiplier);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Failed to compile pattern in '{}': {}", entry, e.getMessage());
            return null;
        }
    }

    /**
     * 检查方块是否在黑名单中。
     */
    public static boolean isBlockBlacklisted(String blockId, List<? extends String> blacklist) {
        if (blockId == null) return false;
        return getCachedBlacklist(blacklist).stream().anyMatch(p -> p.matcher(blockId).matches());
    }

    /**
     * 获取方块的倍率。
     */
    public static double getMultiplierForBlock(String blockId, List<? extends String> multipliers) {
        if (blockId == null) return 1.0;
        double maxMultiplier = 1.0;
        for (MultiplierEntry me : getCachedMultiplierEntries(multipliers)) {
            if (me.pattern.matcher(blockId).matches()) {
                maxMultiplier = Math.max(maxMultiplier, me.multiplier);
            }
        }
        return maxMultiplier;
    }

    public static List<Pattern> compilePatterns(List<? extends String> raw) {
        List<Pattern> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.trim().isEmpty()) continue;
            try {
                out.add(compilePattern(s));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Failed to compile pattern '{}': {}", s, e.getMessage());
            }
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
    public static List<Pattern> getCachedBlacklist(List<? extends String> source) {
        List<String> normalized = normalizeSource(source);
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
    public static List<MultiplierEntry> getCachedMultiplierEntries(List<? extends String> source) {
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
    private static List<String> normalizeSource(List<? extends String> source) {
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
            cachedBlacklistSourceSnapshot = null;
            cachedMultiplierSourceSnapshot = null;
        }
    }
}