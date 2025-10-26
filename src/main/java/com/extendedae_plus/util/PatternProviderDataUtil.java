package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 样板供应器数据工具类
 * 用于获取样板供应器中的所有样板数据，包括输入输出物品的数量信息
 */
public class PatternProviderDataUtil {
    /**
     * 判断 provider 是否可用并属于指定网格（在线且有频道/处于活跃状态）
     */
    public static boolean isProviderAvailable(PatternProviderLogic provider, IGrid expectedGrid) {
        if (provider == null || expectedGrid == null) return false;
        try {
            var grid = provider.getGrid();
            if (grid == null || !grid.equals(expectedGrid)) return false;

            // 使用 accessor 获取 mainNode，再调用 isActive
            if (provider instanceof PatternProviderLogicAccessor accessor) {
                var mainNode = accessor.eap$mainNode();
                if (mainNode == null) return false;
                try {
                    var isActiveMethod = mainNode.getClass().getMethod("isActive");
                    Object active = isActiveMethod.invoke(mainNode);
                    if (active instanceof Boolean && !((Boolean) active)) return false;
                } catch (NoSuchMethodException nsme) {
                    // 没有 isActive 方法时，退回到检查 channels
                    try {
                        var getChannels = mainNode.getClass().getMethod("getChannels");
                        Object channels = getChannels.invoke(mainNode);
                        if (channels instanceof java.util.Collection) {
                            if (((java.util.Collection<?>) channels).isEmpty()) return false;
                        }
                    } catch (Exception ignored) {
                        // 无法判断 channels 时，认为不可用
                        return false;
                    }
                }
            } else {
                // 没有 accessor 的情况，尽量通过反射判断 mainNode.channels
                try {
                    var mainNodeField = provider.getClass().getDeclaredField("mainNode");
                    mainNodeField.setAccessible(true);
                    var mainNode = mainNodeField.get(provider);
                    if (mainNode == null) return false;
                    var getChannelsMethod = mainNode.getClass().getMethod("getChannels");
                    Object channels = getChannelsMethod.invoke(mainNode);
                    if (channels instanceof java.util.Collection) {
                        return !((java.util.Collection<?>) channels).isEmpty();
                    }
                } catch (Exception e) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查找 provider 中匹配给定定义的样板槽位（轻量、按需解码并早退出）
     * @param patternProvider 要搜索的 provider
     * @param targetDefinition pattern.getDefinition() 返回的对象（用于 equals 比较）
     * @return 找到的槽位索引，未找到返回 -1
     */
    public static int findSlotForPattern(PatternProviderLogic patternProvider, Object targetDefinition) {
        if (patternProvider == null || targetDefinition == null) return -1;
        InternalInventory inv = patternProvider.getPatternInv();
        if (inv == null) return -1;
        Level level = getPatternProviderLevel(patternProvider);
        if (level == null) return -1;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            try {
                IPatternDetails d = PatternDetailsHelper.decodePattern(s, level);
                if (d != null && d.getDefinition().equals(targetDefinition)) {
                    return i;
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    /**
     * ExtendedAE风格：安全获取样板供应器的Level对象
     */
    private static Level getPatternProviderLevel(PatternProviderLogic patternProvider) {
        if (patternProvider == null) return null;
        try {
            if (patternProvider instanceof PatternProviderLogicAccessor accessor) {
                var host = accessor.eap$host();
                if (host != null) {
                    BlockEntity be = host.getBlockEntity();
                    if (be != null) {
                        return be.getLevel();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}