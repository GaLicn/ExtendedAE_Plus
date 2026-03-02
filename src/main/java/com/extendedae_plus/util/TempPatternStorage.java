package com.extendedae_plus.util;

import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时样板存储
 * 用于在用户选择供应器之前临时保存创建的样板
 */
public class TempPatternStorage {
    private static final Map<UUID, ItemStack> TEMP_PATTERNS = new ConcurrentHashMap<>();
    
    /**
     * 存储临时样板
     * @param playerId 玩家UUID
     * @param pattern 样板物品
     */
    public static void store(UUID playerId, ItemStack pattern) {
        TEMP_PATTERNS.put(playerId, pattern.copy());
    }
    
    /**
     * 获取并移除临时样板
     * @param playerId 玩家UUID
     * @return 样板物品，如果不存在返回空
     */
    public static ItemStack retrieve(UUID playerId) {
        return TEMP_PATTERNS.remove(playerId);
    }
    
    /**
     * 检查是否有临时样板
     * @param playerId 玩家UUID
     * @return 是否存在
     */
    public static boolean has(UUID playerId) {
        return TEMP_PATTERNS.containsKey(playerId);
    }
    
    /**
     * 清除玩家的临时样板
     * @param playerId 玩家UUID
     */
    public static void clear(UUID playerId) {
        TEMP_PATTERNS.remove(playerId);
    }
}
