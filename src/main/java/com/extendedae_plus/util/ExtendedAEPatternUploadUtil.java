package com.extendedae_plus.util;

import appeng.api.inventories.InternalInventory;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.core.definitions.AEItems;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * ExtendedAE扩展样板管理终端专用的样板上传工具类
 * 兼容ExtendedAE的ContainerExPatternTerminal和原版AE2的PatternAccessTermMenu
 */
public class ExtendedAEPatternUploadUtil {

    // 最近一次通过 JEI 填充到编码终端的“处理配方”的中文名称（如：烧炼/高炉/烟熏...）
    public static volatile String lastProcessingName = null;

    public static void setLastProcessingName(String name) {
        lastProcessingName = name;
    }

    public static String mapRecipeTypeToCn(Recipe<?> recipe) {
        if (recipe == null) return null;
        RecipeType<?> type = recipe.getType();
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (key == null) return null;
        String id = key.toString();
        String path = key.getPath();
        // 常见原版类型映射
        switch (path) {
            case "smelting":
                return "烧炼"; // 熔炉
            case "blasting":
                return "高炉";
            case "smoking":
                return "烟熏";
            case "campfire_cooking":
                return "营火烹饪";
            default:
                // 其他模组类型，返回路径名，必要时可再做表扩展
                return path;
        }
    }

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
     * 从 AE2 的图样编码终端菜单上传当前“已编码图样”至 ExtendedAE 装配矩阵（仅合成图样）。
     * 不会处理“处理图样”。
     *
     * @param player 服务器玩家
     * @param menu   PatternEncodingTermMenu
     * @return 是否成功插入矩阵
     */
    public static boolean uploadFromEncodingMenuToMatrix(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null) {
            System.out.println("[EAE+][Server] uploadFromEncodingMenuToMatrix: player or menu is null");
            return false;
        }

        // 读取已编码槽位的物品
        var encodedSlot = ((com.extendedae_plus.mixin.accessor.PatternEncodingTermMenuAccessor) (Object) menu)
                .epp$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        System.out.println("[EAE+][Server] Encoded slot stack: " + stack + ", count=" + stack.getCount());
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            sendMessage(player, "ExtendedAE Plus: 没有可上传的编码样板");
            System.out.println("[EAE+][Server] Fail: stack empty or not encoded pattern");
            return false;
        }

        // 仅允许“合成图样”
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
        System.out.println("[EAE+][Server] Decoded details: " + (details == null ? "null" : details.getClass().getName()));
        if (!(details instanceof AECraftingPattern)) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_not_crafting");
            System.out.println("[EAE+][Server] Fail: not AECraftingPattern");
            return false;
        }

        // 获取 AE 网络
        IGridNode node = menu.getNetworkNode();
        System.out.println("[EAE+][Server] Grid node: " + node);
        if (node == null) {
            sendMessage(player, "ExtendedAE Plus: 当前不在有效的 AE 网络中");
            System.out.println("[EAE+][Server] Fail: grid node null");
            return false;
        }
        IGrid grid = node.getGrid();
        System.out.println("[EAE+][Server] Grid: " + grid);
        if (grid == null) {
            sendMessage(player, "ExtendedAE Plus: 当前不在有效的 AE 网络中");
            System.out.println("[EAE+][Server] Fail: grid null");
            return false;
        }

        // 在尝试上传之前，检查装配矩阵是否已经存在相同样板（物品与NBT完全一致）
        if (matrixContainsPattern(grid, stack)) {
            // 直接提醒并跳过上传，并将同等数量的空白样板放回空白样板槽，否则退回玩家背包
            if (player != null) {
                player.sendSystemMessage(Component.literal("ExtendedAE Plus: 装配矩阵已存在相同样板，已跳过上传并返还空白样板"));
            }
            try {
                var accessor = (com.extendedae_plus.mixin.accessor.PatternEncodingTermMenuAccessor) (Object) menu;
                var blankSlot = accessor.epp$getBlankPatternSlot();
                ItemStack blanks = AEItems.BLANK_PATTERN.stack(stack.getCount());
                if (blankSlot != null && blankSlot.mayPlace(blanks)) {
                    ItemStack remain = blankSlot.safeInsert(blanks);
                    if (!remain.isEmpty() && player != null) {
                        player.getInventory().placeItemBackInInventory(remain, false);
                    }
                } else if (player != null) {
                    player.getInventory().placeItemBackInInventory(blanks, false);
                }
            } catch (Throwable t) {
                System.out.println("[EAE+][Server] Failed to return blank patterns: " + t);
                if (player != null) {
                    // 兜底：直接还给玩家背包
                    player.getInventory().placeItemBackInInventory(AEItems.BLANK_PATTERN.stack(stack.getCount()), false);
                }
            }
            // 清空编码样板槽，防止再次输出
            encodedSlot.set(ItemStack.EMPTY);
            System.out.println("[EAE+][Server] Skip: duplicate pattern already present in matrix, returned blanks and cleared encoded slot");
            return false;
        }

        // 收集所有可用的装配矩阵（图样模块）内部库存并逐一尝试（遵循其过滤规则）
        List<InternalInventory> inventories = findAllMatrixPatternInventories(grid);
        System.out.println("[EAE+][Server] Matrix internal inventories count: " + inventories.size());
        if (!inventories.isEmpty()) {
            for (int i = 0; i < inventories.size(); i++) {
                var inv = inventories.get(i);
                ItemStack toInsert = stack.copy();
                System.out.println("[EAE+][Server] Try insert via internal inventory[" + i + "], count=" + toInsert.getCount());
                ItemStack remain = inv.addItems(toInsert);
                System.out.println("[EAE+][Server] Internal inventory[" + i + "] remain count=" + remain.getCount());
                if (remain.getCount() < stack.getCount()) {
                    int inserted = stack.getCount() - remain.getCount();
                    stack.shrink(inserted);
                    if (stack.isEmpty()) {
                        encodedSlot.set(ItemStack.EMPTY);
                    }
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    System.out.println("[EAE+][Server] Success via internal inventory[" + i + "]: inserted=" + inserted);
                    return true;
                }
            }
            // 所有内部库存都无法接收
            System.out.println("[EAE+][Server] All internal inventories refused or full. Trying capability fallback.");
        }

        // 回退：尝试 Forge 能力（可能为聚合图样仓），同样遍历所有矩阵
        List<IItemHandler> handlers = findAllMatrixPatternHandlers(grid);
        System.out.println("[EAE+][Server] Fallback Matrix item handlers count: " + handlers.size());
        if (!handlers.isEmpty()) {
            for (int i = 0; i < handlers.size(); i++) {
                var cap = handlers.get(i);
                ItemStack toInsert = stack.copy();
                System.out.println("[EAE+][Server] Try insert via capability[" + i + "], count=" + toInsert.getCount());
                ItemStack remain = insertIntoAnySlot(cap, toInsert);
                System.out.println("[EAE+][Server] Capability[" + i + "] remain count=" + remain.getCount());
                if (remain.getCount() < stack.getCount()) {
                    int inserted = stack.getCount() - remain.getCount();
                    stack.shrink(inserted);
                    if (stack.isEmpty()) {
                        encodedSlot.set(ItemStack.EMPTY);
                    }
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    System.out.println("[EAE+][Server] Success via capability[" + i + "]: inserted=" + inserted);
                    return true;
                }
            }
        }

        // 未找到可用矩阵或全部拒收
        if (inventories.isEmpty() && handlers.isEmpty()) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_no_matrix");
            System.out.println("[EAE+][Server] Fail: no formed matrix found");
        } else {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_full");
            System.out.println("[EAE+][Server] Fail: all matrices full or cannot accept pattern");
        }
        return false;
    }

    /**
     * 在给定 AE Grid 中收集所有已成型且在线的装配矩阵“图样模块”的用于外部插入的内部库存。
     * 优先使用 TileAssemblerMatrixPattern#getExposedInventory（仅允许插入，且已带AE过滤规则）。
     */
    private static List<InternalInventory> findAllMatrixPatternInventories(IGrid grid) {
        List<InternalInventory> result = new ArrayList<>();
        try {
            var tiles = grid.getMachines(com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern.class);
            int idx = 0;
            for (com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern tile : tiles) {
                if (tile != null && tile.isFormed() && tile.getMainNode().isActive()) {
                    var inv = tile.getExposedInventory();
                    if (inv != null) {
                        result.add(inv);
                        System.out.println("[EAE+][Server] Found matrix internal inventory at index " + idx);
                    }
                }
                idx++;
            }
        } catch (Throwable t) {
            System.out.println("[EAE+][Server] findAllMatrixPatternInventories exception: " + t);
        }
        return result;
    }

    /**
     * 在给定 AE Grid 中收集所有已成型的装配矩阵的聚合图样仓 IItemHandler（若可用）。
     */
    private static List<IItemHandler> findAllMatrixPatternHandlers(IGrid grid) {
        List<IItemHandler> result = new ArrayList<>();
        try {
            Set<TileAssemblerMatrixBase> matrices = grid.getMachines(TileAssemblerMatrixBase.class);
            int idx = 0;
            for (TileAssemblerMatrixBase tile : matrices) {
                if (tile != null && tile.isFormed()) {
                    var capOpt = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                    if (capOpt != null) {
                        var handler = capOpt.orElse(null);
                        if (handler != null) {
                            result.add(handler);
                            System.out.println("[EAE+][Server] Found matrix capability handler at index " + idx);
                        }
                    }
                }
                idx++;
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 尝试将整个物品栈插入到 IItemHandler 的任意槽位，返回剩余物品。
     */
    private static ItemStack insertIntoAnySlot(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if (handler == null || remaining.isEmpty()) return remaining;
        for (int i = 0; i < handler.getSlots(); i++) {
            remaining = handler.insertItem(i, remaining, false);
            if (remaining.isEmpty()) break;
        }
        return remaining;
    }

    /**
     * 检查装配矩阵（所有已成型矩阵的图样仓）中是否已存在与给定样板完全相同的物品（含NBT）。
     */
    private static boolean matrixContainsPattern(IGrid grid, ItemStack pattern) {
        if (grid == null || pattern == null || pattern.isEmpty()) return false;
        try {
            // 先检查提供外部插入视图的内部库存
            List<InternalInventory> inventories = findAllMatrixPatternInventories(grid);
            for (InternalInventory inv : inventories) {
                if (inv == null) continue;
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (!s.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameTags(s, pattern)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[EAE+][Server] matrixContainsPattern (InternalInventory) exception: " + t);
        }
        try {
            // 再检查聚合能力视图
            List<IItemHandler> handlers = findAllMatrixPatternHandlers(grid);
            for (IItemHandler h : handlers) {
                if (h == null) continue;
                int slots = h.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (!s.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameTags(s, pattern)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[EAE+][Server] matrixContainsPattern (Capability) exception: " + t);
        }
        return false;
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
        // 静默：不再向玩家左下角发送任何提示信息
        // 如需恢复，取消下面注释即可：
        // if (player != null) {
        //     player.sendSystemMessage(Component.literal(message));
        // }
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

    /**
     * 从 AE2 的图样编码终端菜单上传当前“已编码图样”至当前网络中任意可用的样板供应器。
     * 策略：
     * 1) 仅当 encoded 槽位存在有效编码样板时执行；
     * 2) 通过 menu.getNetworkNode() 获取 IGrid，遍历在线的 PatternContainer；
     * 3) 仅选择在终端中可见（isVisibleInTerminal）且库存存在空位的供应器；
     * 4) 使用 AE2 的标准 FilteredInternalInventory + Pattern 过滤器尝试插入；
     * 5) 成功后清空 encoded 槽位，返回 true；否则返回 false。
     */
    public static boolean uploadFromEncodingMenuToAnyProvider(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null) {
            return false;
        }
        // 读取已编码槽位的物品（通过 accessor）
        var encodedSlot = ((com.extendedae_plus.mixin.accessor.PatternEncodingTermMenuAccessor) (Object) menu)
                .epp$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        // 获取 AE 网络
        IGridNode node = menu.getNetworkNode();
        if (node == null) {
            return false;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return false;
        }

        // 遍历在线的 PatternContainer，寻找第一个可见且有空位的供应器
        try {
            for (var machineClass : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                    for (var container : grid.getActiveMachines(containerClass)) {
                        if (container == null || !container.isVisibleInTerminal()) {
                            continue;
                        }
                        InternalInventory inv = container.getTerminalPatternInventory();
                        if (inv == null || inv.size() <= 0) {
                            continue;
                        }
                        boolean hasEmpty = false;
                        for (int i = 0; i < inv.size(); i++) {
                            if (inv.getStackInSlot(i).isEmpty()) {
                                hasEmpty = true;
                                break;
                            }
                        }
                        if (!hasEmpty) {
                            continue;
                        }

                        // 按 AE2 样板过滤规则尝试插入
                        var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
                        ItemStack toInsert = stack.copy();
                        ItemStack remain = filtered.addItems(toInsert);
                        if (remain.getCount() < toInsert.getCount()) {
                            int inserted = toInsert.getCount() - remain.getCount();
                            stack.shrink(inserted);
                            if (stack.isEmpty()) {
                                encodedSlot.set(ItemStack.EMPTY);
                            } else {
                                encodedSlot.set(stack);
                            }
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // 忽略异常以避免噪声
        }
        return false;
    }

    /**
     * 将图样编码终端的“已编码图样”上传到指定的样板供应器（通过 providerId 定位）。
     */
    public static boolean uploadFromEncodingMenuToProvider(ServerPlayer player, PatternEncodingTermMenu menu, long providerId) {
        if (player == null || menu == null) {
            return false;
        }
        var encodedSlot = ((com.extendedae_plus.mixin.accessor.PatternEncodingTermMenuAccessor) (Object) menu)
                .epp$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        PatternAccessTermMenu accessMenu = getPatternAccessMenu(player);
        if (accessMenu == null) {
            return false;
        }
        PatternContainer container = getPatternContainerById(accessMenu, providerId);
        if (container == null || !container.isVisibleInTerminal()) {
            return false;
        }
        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null || inv.size() <= 0) {
            return false;
        }

        var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
        ItemStack toInsert = stack.copy();
        ItemStack remain = filtered.addItems(toInsert);
        if (remain.getCount() < toInsert.getCount()) {
            int inserted = toInsert.getCount() - remain.getCount();
            stack.shrink(inserted);
            if (stack.isEmpty()) {
                encodedSlot.set(ItemStack.EMPTY);
            } else {
                encodedSlot.set(stack);
            }
            return true;
        }
        return false;
    }

    /**
     * 列出当前菜单中所有供应器的服务器ID（原样返回 byId 的 key 集合）。
     */
    public static java.util.List<Long> getAllProviderIds(PatternAccessTermMenu menu) {
        java.util.List<Long> result = new java.util.ArrayList<>();
        if (menu == null) return result;
        try {
            java.lang.reflect.Field byIdField = findByIdField(menu.getClass());
            if (byIdField == null) return result;
            byIdField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Object> byId = (java.util.Map<Long, Object>) byIdField.get(menu);
            if (byId != null) {
                result.addAll(byId.keySet());
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 基于编码终端菜单的 AE Grid 遍历，列出“可在终端中可见且有空位”的供应器容器。
     * 返回顺序稳定：按 grid 的 machineClasses 顺序，再按 activeMachines 迭代顺序。
     */
    public static List<PatternContainer> listAvailableProvidersFromGrid(PatternEncodingTermMenu menu) {
        List<PatternContainer> list = new ArrayList<>();
        if (menu == null) return list;
        try {
            IGridNode node = menu.getNetworkNode();
            if (node == null) return list;
            IGrid grid = node.getGrid();
            if (grid == null) return list;
            for (var machineClass : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                    for (var container : grid.getActiveMachines(containerClass)) {
                        if (container == null || !container.isVisibleInTerminal()) continue;
                        InternalInventory inv = container.getTerminalPatternInventory();
                        if (inv == null || inv.size() <= 0) continue;
                        boolean hasEmpty = false;
                        for (int i = 0; i < inv.size(); i++) {
                            if (inv.getStackInSlot(i).isEmpty()) { hasEmpty = true; break; }
                        }
                        if (hasEmpty) list.add(container);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 获取供应器显示名（优先组名） */
    public static String getProviderDisplayName(PatternContainer container) {
        if (container == null) return "未知供应器";
        try {
            var group = container.getTerminalGroup();
            if (group != null) return group.name().getString();
        } catch (Throwable ignored) {
        }
        return "样板供应器";
    }

    /** 计算供应器空槽位数量 */
    public static int getAvailableSlots(PatternContainer container) {
        if (container == null) return -1;
        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null) return -1;
        int available = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) available++;
        }
        return available;
    }

    /**
     * 基于“索引”的定向上传：使用 listAvailableProvidersFromGrid(menu) 的顺序，
     * 将编码槽样板插入到第 index 个供应器。
     */
    public static boolean uploadFromEncodingMenuToProviderByIndex(ServerPlayer player, PatternEncodingTermMenu menu, int index) {
        if (player == null || menu == null || index < 0) return false;
        List<PatternContainer> list = listAvailableProvidersFromGrid(menu);
        if (index >= list.size()) return false;
        var container = list.get(index);
        if (container == null) return false;

        var encodedSlot = ((com.extendedae_plus.mixin.accessor.PatternEncodingTermMenuAccessor) (Object) menu)
                .epp$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null || inv.size() <= 0) return false;
        var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
        ItemStack toInsert = stack.copy();
        ItemStack remain = filtered.addItems(toInsert);
        if (remain.getCount() < toInsert.getCount()) {
            int inserted = toInsert.getCount() - remain.getCount();
            stack.shrink(inserted);
            if (stack.isEmpty()) {
                encodedSlot.set(ItemStack.EMPTY);
            } else {
                encodedSlot.set(stack);
            }
            return true;
        }
        return false;
    }
}
