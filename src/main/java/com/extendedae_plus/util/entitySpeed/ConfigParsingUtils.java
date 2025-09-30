package com.extendedae_plus.util.entitySpeed;

import com.extendedae_plus.config.ModConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * 配置解析工具类：用于解析黑名单与倍率配置的字符串
 */
public final class ConfigParsingUtils {
    private ConfigParsingUtils() {}

    public record MultiplierEntry(Pattern pattern, double multiplier) { }

    // 直接引用配置
    private static final Supplier<String[]> BLACKLIST_SUPPLIER = () -> ModConfig.INSTANCE.entityTickerBlackList;
    private static final Supplier<String[]> MULTIPLIERS_SUPPLIER = () -> ModConfig.INSTANCE.entityTickerMultipliers;

    // 缓存字段
    private static volatile Map<String, Pattern> cachedBlacklist = null;
    private static volatile Map<String, MultiplierEntry> cachedMultiplierEntries = null;
    private static volatile String[] cachedBlacklistSourceSnapshot = null;
    private static volatile String[] cachedMultiplierSourceSnapshot = null;
    private static volatile Map<String, Boolean> blacklistResultCache = new HashMap<>();
    private static volatile Map<String, Double> multiplierResultCache = new HashMap<>();
    private static final Object CACHE_LOCK = new Object();

    /*
      初始化缓存，在模组加载时调用
     */
    static {
        reload();
    }

    /**
     * 编译用户提供的匹配串。支持简单的 glob 语法（'*' 和 '?'）以及完整的正则表达式。
     */
    public static Pattern compilePattern(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            EAP$LOGGER.warn("Invalid pattern: {}", raw);
            throw new IllegalArgumentException("Pattern is null or empty");
        }
        raw = raw.trim();

        // Try regex first if it contains regex metacharacters
        if (raw.contains(".*") || raw.matches(".*[\\[\\(\\+\\{\\\\].*")) {
            try {
                return Pattern.compile("^" + raw + "$");
            } catch (PatternSyntaxException e) {
                EAP$LOGGER.warn("Failed to compile regex pattern '{}': {}", raw, e.getMessage());
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
            EAP$LOGGER.warn("Invalid multiplier entry: {}", entry);
            return null;
        }
        String key = parts[0];
        String val = parts[1].toLowerCase();
        if (val.endsWith("x")) val = val.substring(0, val.length() - 1);
        double multiplier;
        try {
            multiplier = Double.parseDouble(val);
        } catch (NumberFormatException e) {
            EAP$LOGGER.warn("Invalid multiplier value in '{}': {}", entry, val);
            return null;
        }
        try {
            Pattern pattern = compilePattern(key);
            return new MultiplierEntry(pattern, multiplier);
        } catch (IllegalArgumentException e) {
            EAP$LOGGER.warn("Failed to compile pattern in '{}': {}", entry, e.getMessage());
            return null;
        }
    }

    /**
     * 检查方块是否在黑名单中。
     */
    public static boolean isBlockBlacklisted(String blockId) {
        if (blockId == null || BLACKLIST_SUPPLIER.get() == null || BLACKLIST_SUPPLIER.get().length == 0) {
            return false;
        }
        return blacklistResultCache.computeIfAbsent(blockId, id ->
                getCachedBlacklist().values().stream().anyMatch(p -> p.matcher(id).matches())
        );
    }

    /**
     * 获取方块的倍率。
     */
    public static double getMultiplierForBlock(String blockId) {
        if (blockId == null || MULTIPLIERS_SUPPLIER.get() == null || MULTIPLIERS_SUPPLIER.get().length == 0) {
            return 1.0;
        }
        return multiplierResultCache.computeIfAbsent(blockId, id -> {
            double maxMultiplier = 1.0;
            for (MultiplierEntry me : getCachedMultiplierEntries().values()) {
                if (me.pattern.matcher(id).matches()) {
                    maxMultiplier = Math.max(maxMultiplier, me.multiplier);
                }
            }
            return maxMultiplier;
        });
    }

    /**
     * 编译黑名单配置为 Map。
     */
    public static Map<String, Pattern> compilePatterns(String[] raw) {
        Map<String, Pattern> out = new HashMap<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.trim().isEmpty()) continue;
            String trimmed = s.trim();
            try {
                out.put(trimmed, compilePattern(trimmed));
            } catch (IllegalArgumentException e) {
                EAP$LOGGER.warn("Failed to compile pattern '{}': {}", trimmed, e.getMessage());
            }
        }
        return out;
    }

    /**
     * 解析倍率配置为 Map。
     */
    public static Map<String, MultiplierEntry> parseMultiplierList(String[] raw) {
        Map<String, MultiplierEntry> out = new HashMap<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.trim().isEmpty()) continue;
            String trimmed = s.trim();
            MultiplierEntry me = parseMultiplierEntry(trimmed);
            if (me != null) out.put(trimmed, me);
        }
        return out;
    }

    /**
     * 获取已解析并缓存的黑名单（线程安全、懒加载）。
     */
    private static Map<String, Pattern> getCachedBlacklist() {
        String[] source = BLACKLIST_SUPPLIER.get();
        if (cachedBlacklist != null && Arrays.equals(cachedBlacklistSourceSnapshot, source)) {
            return Collections.unmodifiableMap(cachedBlacklist);
        }
        synchronized (CACHE_LOCK) {
            if (cachedBlacklist == null || !Arrays.equals(cachedBlacklistSourceSnapshot, source)) {
                cachedBlacklist = compilePatterns(source);
                cachedBlacklistSourceSnapshot = source == null ? new String[0] : source.clone();
            }
            return Collections.unmodifiableMap(cachedBlacklist);
        }
    }

    /**
     * 获取已解析并缓存的倍率列表（线程安全、懒加载）。
     */
    private static Map<String, MultiplierEntry> getCachedMultiplierEntries() {
        String[] source = MULTIPLIERS_SUPPLIER.get();
        if (cachedMultiplierEntries != null && Arrays.equals(cachedMultiplierSourceSnapshot, source)) {
            return Collections.unmodifiableMap(cachedMultiplierEntries);
        }
        synchronized (CACHE_LOCK) {
            if (cachedMultiplierEntries == null || !Arrays.equals(cachedMultiplierSourceSnapshot, source)) {
                cachedMultiplierEntries = parseMultiplierList(source);
                cachedMultiplierSourceSnapshot = source == null ? new String[0] : source.clone();
            }
            return Collections.unmodifiableMap(cachedMultiplierEntries);
        }
    }

    /**
     * 清空缓存，下一次获取时将重新解析。
     */
    public static void reload() {
        synchronized (CACHE_LOCK) {
            cachedBlacklist = null;
            cachedMultiplierEntries = null;
            cachedBlacklistSourceSnapshot = null;
            cachedMultiplierSourceSnapshot = null;
            blacklistResultCache.clear();
            multiplierResultCache.clear();
        }
    }
}