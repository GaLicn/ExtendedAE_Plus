package com.extendedae_plus.util;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.implementations.PatternAccessTermMenu;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderLogicAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * 样板供应器数据工具类
 * 用于获取样板供应器中的所有样板数据，包括输入输出物品的数量信息
 */
public class PatternProviderDataUtil {
    private static final int INVALID_SLOT = -1;
    private static final String UNKNOWN_PROVIDER = "未知供应器";

    /**
     * 判断 provider 是否可用并属于指定网格（在线且有频道/处于活跃状态）
     */
    public static boolean isProviderAvailable(@NotNull PatternProviderLogic provider, @NotNull IGrid expectedGrid) {
        try {
            IGrid grid = provider.getGrid();
            if (grid == null || !grid.equals(expectedGrid)) return false;

            if (provider instanceof PatternProviderLogicAccessor accessor) {
                IManagedGridNode mainNode = accessor.eap$mainNode();
                return mainNode != null && mainNode.isActive();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 验证样板供应器是否可用
     *
     * @param providerId 供应器ID
     * @param menu       样板访问终端菜单
     * @return 是否可用
     */
    public static boolean isProviderAvailable(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = PatternTerminalUtil.getPatternContainerById(menu, providerId);
        if (container == null) return false;
        if (!container.isVisibleInTerminal()) return false;
        return container.getGrid() != null;
    }

    /**
     * 查找 provider 中匹配给定定义的样板槽位（轻量、按需解码并早退出）
     *
     * @param provider         要搜索的 provider
     * @param targetDefinition pattern.getDefinition() 返回的对象（用于 equals 比较）
     * @return 找到的槽位索引，未找到返回 -1
     */
    public static int findSlotForPattern(@Nullable PatternProviderLogic provider, @Nullable Object targetDefinition) {
        if (provider == null || targetDefinition == null) return INVALID_SLOT;

        InternalInventory inv = provider.getPatternInv();
        if (inv == null || inv.isEmpty()) return INVALID_SLOT;

        Level level = getPatternProviderLevel(provider);
        if (level == null) return INVALID_SLOT;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || !stack.hasTag()) continue; // 快速跳过无Nbt样板
            try {
                IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
                if (details != null && targetDefinition.equals(details.getDefinition())) {
                    return i;
                }
            } catch (Exception ignored) {
            }
        }
        return INVALID_SLOT;
    }

    /**
     * 获取样板供应器的 Level 对象
     */
    @Nullable
    private static Level getPatternProviderLevel(@Nullable PatternProviderLogic provider) {
        if (!(provider instanceof PatternProviderLogicAccessor accessor)) return null;
        var host = accessor.eap$host();
        if (host == null) return null;
        BlockEntity be = host.getBlockEntity();
        return be != null ? be.getLevel() : null;
    }

    /**
     * 获取样板供应器中的空槽位数量
     *
     * @param providerId 供应器ID
     * @param menu       样板访问终端菜单（支持ExtendedAE）
     * @return 空槽位数量，如果无法访问则返回-1
     */
    public static int getAvailableSlots(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = PatternTerminalUtil.getPatternContainerById(menu, providerId);
        return getAvailableSlots(container);
    }

    /**
     * 计算供应器空槽位数量
     */
    public static int getAvailableSlots(@Nullable PatternContainer container) {
        if (container == null) return INVALID_SLOT;
        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null) return INVALID_SLOT;

        int available = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) available++;
        }
        return available;
    }

    /**
     * 获取样板供应器的显示名称
     *
     * @param providerId 供应器ID
     * @param menu       样板访问终端菜单
     * @return 显示名称，如果无法获取则返回"未知供应器"
     */
    public static String getProviderDisplayName(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = PatternTerminalUtil.getPatternContainerById(menu, providerId);
        return getProviderDisplayName(container, providerId);
    }

    /**
     * 获取供应器显示名（优先组名）
     */
    public static String getProviderDisplayName(@Nullable PatternContainer container) {
        return getProviderDisplayName(container, -1);
    }

    /**
     * 实际显示名获取逻辑
     */
    private static String getProviderDisplayName(@Nullable PatternContainer container, long providerId) {
        if (container == null) return UNKNOWN_PROVIDER;
        try {
            var group = container.getTerminalGroup();
            if (group != null) {
                // 使用 Component 序列化来保持翻译键，而不是直接 getString()
                // 这样客户端可以根据自己的语言设置进行翻译
                return Component.Serializer.toJson(group.name());
            }
        } catch (Exception ignored) {
        }
        return providerId > 0 ? "样板供应器 #" + providerId : "样板供应器";
    }
}