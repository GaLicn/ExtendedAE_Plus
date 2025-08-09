package com.extendedae_plus.util;

import appeng.api.inventories.InternalInventory;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * ExtendedAE扩展样板管理终端专用的样板上传工具类
 * 兼容ExtendedAE的ContainerExPatternTerminal和原版AE2的PatternAccessTermMenu
 */
public class ExtendedAEPatternUploadUtil {

    /**
     * 获取玩家当前的样板访问终端菜单（支持ExtendedAE和原版AE2）
     * 
     * @param player 玩家
     * @return PatternAccessTermMenu实例，如果玩家没有打开则返回null
     */
    public static PatternAccessTermMenu getPatternAccessMenu(ServerPlayer player) {
        if (player == null || player.containerMenu == null) {
            return null;
        }
        
        // 优先检查ExtendedAE的扩展样板管理终端（使用类名检查避免直接导入）
        String containerClassName = player.containerMenu.getClass().getName();
        if (containerClassName.equals("com.glodblock.github.extendedae.container.ContainerExPatternTerminal")) {
            // ExtendedAE的容器继承自PatternAccessTermMenu，可以安全转换
            return (PatternAccessTermMenu) player.containerMenu;
        }
        
        // 兼容原版AE2的样板访问终端
        if (player.containerMenu instanceof PatternAccessTermMenu) {
            return (PatternAccessTermMenu) player.containerMenu;
        }
        
        return null;
    }

    /**
     * 检查当前菜单是否为ExtendedAE的扩展样板管理终端
     * 
     * @param player 玩家
     * @return 是否为ExtendedAE扩展终端
     */
    public static boolean isExtendedAETerminal(ServerPlayer player) {
        if (player == null || player.containerMenu == null) {
            return false;
        }
        
        String containerClassName = player.containerMenu.getClass().getName();
        return containerClassName.equals("com.glodblock.github.extendedae.container.ContainerExPatternTerminal");
    }

    /**
     * 将玩家背包中的样板上传到指定的样板供应器
     * 兼容ExtendedAE和原版AE2
     * 
     * @param player 玩家
     * @param playerSlotIndex 玩家背包槽位索引
     * @param providerId 目标样板供应器的服务器ID
     * @return 是否上传成功
     */
    public static boolean uploadPatternToProvider(ServerPlayer player, int playerSlotIndex, long providerId) {
        // 1. 验证玩家是否打开了样板访问终端
        PatternAccessTermMenu menu = getPatternAccessMenu(player);
        if (menu == null) {
            sendMessage(player, "ExtendedAE Plus: 请先打开样板访问终端或扩展样板管理终端");
            return false;
        }

        // 2. 获取玩家背包中的物品
        ItemStack playerItem = player.getInventory().getItem(playerSlotIndex);
        if (playerItem.isEmpty()) {
            sendMessage(player, "ExtendedAE Plus: 背包槽位为空");
            return false;
        }

        // 3. 验证是否是编码样板
        if (!PatternDetailsHelper.isEncodedPattern(playerItem)) {
            sendMessage(player, "ExtendedAE Plus: 该物品不是有效的编码样板");
            return false;
        }

        // 4. 获取目标样板供应器
        PatternContainer patternContainer = getPatternContainerById(menu, providerId);
        if (patternContainer == null) {
            sendMessage(player, "ExtendedAE Plus: 找不到指定的样板供应器 (ID: " + providerId + ")");
            return false;
        }

        // 5. 获取样板供应器的库存
        InternalInventory patternInventory = patternContainer.getTerminalPatternInventory();
        if (patternInventory == null) {
            sendMessage(player, "ExtendedAE Plus: 无法访问样板供应器的库存");
            return false;
        }

        // 6. 使用AE2的标准样板过滤器进行插入
        var patternFilter = new ExtendedAEPatternFilter();
        var filteredInventory = new FilteredInternalInventory(patternInventory, patternFilter);

        // 7. 尝试插入样板
        ItemStack itemToInsert = playerItem.copy();
        ItemStack remaining = filteredInventory.addItems(itemToInsert);

        if (remaining.getCount() < itemToInsert.getCount()) {
            // 插入成功（部分或全部）
            int insertedCount = itemToInsert.getCount() - remaining.getCount();
            playerItem.shrink(insertedCount);
            
            if (playerItem.isEmpty()) {
                player.getInventory().setItem(playerSlotIndex, ItemStack.EMPTY);
            }
            
            String terminalType = isExtendedAETerminal(player) ? "扩展样板管理终端" : "样板访问终端";
            sendMessage(player, "ExtendedAE Plus: 通过" + terminalType + "成功上传 " + insertedCount + " 个样板");
            return true;
        } else {
            sendMessage(player, "ExtendedAE Plus: 上传失败 - 样板供应器已满或样板无效");
            return false;
        }
    }

    /**
     * 批量上传样板到指定供应器（支持ExtendedAE和原版AE2）
     * 
     * @param player 玩家
     * @param playerSlotIndices 玩家背包槽位索引数组
     * @param providerId 目标样板供应器ID
     * @return 成功上传的样板数量
     */
    public static int uploadMultiplePatterns(ServerPlayer player, int[] playerSlotIndices, long providerId) {
        int successCount = 0;
        
        for (int slotIndex : playerSlotIndices) {
            if (uploadPatternToProvider(player, slotIndex, providerId)) {
                successCount++;
            }
        }
        
        String terminalType = isExtendedAETerminal(player) ? "扩展样板管理终端" : "样板访问终端";
        sendMessage(player, "ExtendedAE Plus: 通过" + terminalType + "批量上传完成，成功上传 " + successCount + " 个样板");
        return successCount;
    }

    /**
     * 检查样板供应器是否有足够的空槽位
     * 
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单（支持ExtendedAE）
     * @param requiredSlots 需要的槽位数
     * @return 是否有足够的空槽位
     */
    public static boolean hasEnoughSlots(long providerId, PatternAccessTermMenu menu, int requiredSlots) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return false;
        }

        InternalInventory inventory = container.getTerminalPatternInventory();
        if (inventory == null) {
            return false;
        }

        int availableSlots = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                availableSlots++;
                if (availableSlots >= requiredSlots) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取样板供应器中的空槽位数量
     * 
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单（支持ExtendedAE）
     * @return 空槽位数量，如果无法访问则返回-1
     */
    public static int getAvailableSlots(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return -1;
        }

        InternalInventory inventory = container.getTerminalPatternInventory();
        if (inventory == null) {
            return -1;
        }

        int availableSlots = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                availableSlots++;
            }
        }

        return availableSlots;
    }

    /**
     * 通过服务器ID获取PatternContainer
     * 兼容ExtendedAE的ContainerExPatternTerminal和原版PatternAccessTermMenu
     * 
     * @param menu 样板访问终端菜单
     * @param providerId 供应器服务器ID
     * @return PatternContainer实例，如果不存在则返回null
     */
    private static PatternContainer getPatternContainerById(PatternAccessTermMenu menu, long providerId) {
        try {
            // 通过反射访问byId字段（ExtendedAE继承了这个字段）
            Field byIdField = findByIdField(menu.getClass());
            if (byIdField == null) {
                System.err.println("ExtendedAE Plus: 无法找到byId字段");
                return null;
            }
            
            byIdField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<Long, Object> byId = (Map<Long, Object>) byIdField.get(menu);
            
            Object containerTracker = byId.get(providerId);
            if (containerTracker == null) {
                return null;
            }

            // 从ContainerTracker中获取PatternContainer
            Field containerField = findContainerField(containerTracker.getClass());
            if (containerField == null) {
                System.err.println("ExtendedAE Plus: 无法找到container字段");
                return null;
            }
            
            containerField.setAccessible(true);
            return (PatternContainer) containerField.get(containerTracker);
            
        } catch (Exception e) {
            System.err.println("ExtendedAE Plus: 无法获取PatternContainer，错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 在类层次结构中查找byId字段
     */
    private static Field findByIdField(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("byId");
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 在类层次结构中查找container字段
     */
    private static Field findContainerField(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("container");
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 发送消息给玩家
     * 
     * @param player 玩家
     * @param message 消息内容
     */
    private static void sendMessage(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
        // 如果玩家为null，静默忽略（用于测试环境）
    }

    /**
     * ExtendedAE兼容的样板过滤器
     * 使用AE2的PatternDetailsHelper进行样板验证
     */
    private static class ExtendedAEPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }
    }

    /**
     * 获取样板供应器的显示名称
     * 
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单
     * @return 显示名称，如果无法获取则返回"未知供应器"
     */
    public static String getProviderDisplayName(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return "未知供应器";
        }

        try {
            // 尝试获取供应器的组信息来构建显示名称
            var group = container.getTerminalGroup();
            if (group != null) {
                return group.name().getString();
            }
        } catch (Exception e) {
            // 忽略异常，使用默认名称
        }

        return "样板供应器 #" + providerId;
    }

    /**
     * 验证样板供应器是否可用
     * 
     * @param providerId 供应器ID
     * @param menu 样板访问终端菜单
     * @return 是否可用
     */
    public static boolean isProviderAvailable(long providerId, PatternAccessTermMenu menu) {
        PatternContainer container = getPatternContainerById(menu, providerId);
        if (container == null) {
            return false;
        }

        // 检查是否在终端中可见
        if (!container.isVisibleInTerminal()) {
            return false;
        }

        // 检查是否连接到网络
        return container.getGrid() != null;
    }

    /**
     * 获取当前终端类型的描述
     * 
     * @param player 玩家
     * @return 终端类型描述
     */
    public static String getTerminalTypeDescription(ServerPlayer player) {
        if (isExtendedAETerminal(player)) {
            return "ExtendedAE扩展样板管理终端";
        } else if (getPatternAccessMenu(player) != null) {
            return "AE2样板访问终端";
        } else {
            return "未知终端类型";
        }
    }
}
