package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 与装配矩阵 (assembler matrix) 上传 / 检查 相关的工具方法。
 * 保留原有逻辑与容错行为。
 */
public final class MatrixUploadUtil {
    private MatrixUploadUtil() {}
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
     * 从 AE2 的图样编码终端菜单上传当前“已编码图样”至 ExtendedAE 装配矩阵（仅合成图样）。
     * 不会处理“处理图样”。
     *
     * @param player 服务器玩家
     * @param menu   PatternEncodingTermMenu
     * @return 是否成功插入矩阵
     */
    public static boolean uploadFromEncodingMenuToMatrix(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null) {
            return false;
        }

        // 读取已编码槽位的物品
        RestrictedInputSlot encodedSlot = ((PatternEncodingTermMenuAccessor)menu).eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        // 仅允许“合成/锻造台/切石机图样”
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
        if (!(details instanceof AECraftingPattern
                || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern)) {
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

        // 在尝试上传之前，检查装配矩阵是否已经存在相同样板（物品与NBT完全一致）
        if (matrixContainsPattern(grid, stack)) {
            // 直接提醒并跳过上传，并将同等数量的空白样板放回空白样板槽，否则退回玩家背包
            if (player != null) {
                player.sendSystemMessage(Component.literal("ExtendedAE Plus: 装配矩阵已存在相同样板，已跳过上传并返还空白样板"));
            }
            try {
                var accessor = (PatternEncodingTermMenuAccessor) (Object) menu;
                var blankSlot = accessor.eap$getBlankPatternSlot();
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
                if (player != null) {
                    // 兜底：直接还给玩家背包
                    player.getInventory().placeItemBackInInventory(AEItems.BLANK_PATTERN.stack(stack.getCount()), false);
                }
            }
            // 清空编码样板槽，防止再次输出
            encodedSlot.set(ItemStack.EMPTY);
            return false;
        }

        // 收集所有可用的装配矩阵（图样模块）内部库存并逐一尝试（遵循其过滤规则）
        List<InternalInventory> inventories = findAllMatrixPatternInventories(grid);
        if (!inventories.isEmpty()) {
            for (int i = 0; i < inventories.size(); i++) {
                var inv = inventories.get(i);
                ItemStack toInsert = stack.copy();
                ItemStack remain = inv.addItems(toInsert);
                if (remain.getCount() < stack.getCount()) {
                    int inserted = stack.getCount() - remain.getCount();
                    stack.shrink(inserted);
                    if (stack.isEmpty()) {
                        encodedSlot.set(ItemStack.EMPTY);
                    }
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
            // 所有内部库存都无法接收 -> 尝试 capability 回退
        }

        // 回退：尝试 Forge 能力（可能为聚合图样仓），同样遍历所有矩阵
        List<IItemHandler> handlers = findAllMatrixPatternHandlers(grid);
        if (!handlers.isEmpty()) {
            for (int i = 0; i < handlers.size(); i++) {
                var cap = handlers.get(i);
                ItemStack toInsert = stack.copy();
                ItemStack remain = insertIntoAnySlot(cap, toInsert);
                if (remain.getCount() < stack.getCount()) {
                    int inserted = stack.getCount() - remain.getCount();
                    stack.shrink(inserted);
                    if (stack.isEmpty()) {
                        encodedSlot.set(ItemStack.EMPTY);
                    }
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
        }

        // 未找到可用矩阵或全部拒收
        if (inventories.isEmpty() && handlers.isEmpty()) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_no_matrix");
        } else {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail_full");
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
            var tiles = grid.getMachines(TileAssemblerMatrixPattern.class);
            for (TileAssemblerMatrixPattern tile : tiles) {
                if (tile != null && tile.isFormed() && tile.getMainNode().isActive() && clusterHasSingleUploadCore(tile)) {
                    var inv = tile.getExposedInventory();
                    if (inv != null) {
                        result.add(inv);
                    }
                }
            }
        } catch (Throwable t) {
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
            for (TileAssemblerMatrixBase tile : matrices) {
                if (tile != null && tile.isFormed() && clusterHasSingleUploadCore(tile)) {
                    var capOpt = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                    if (capOpt != null) {
                        var handler = capOpt.orElse(null);
                        if (handler != null) {
                            result.add(handler);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
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
                    if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, pattern)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
        }
        try {
            // 再检查聚合能力视图
            List<IItemHandler> handlers = findAllMatrixPatternHandlers(grid);
            for (IItemHandler h : handlers) {
                if (h == null) continue;
                int slots = h.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, pattern)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
        }
        return false;
    }

    /**
     * 判断给定矩阵集群中是否存在“装配矩阵上传核心”。
     * 要求：至少存在 1 个即可，不限制数量。
     * 传入任意属于该集群的 Tile（如 Pattern/Crafter/Frame 等）。
     */
    private static boolean clusterHasSingleUploadCore(TileAssemblerMatrixBase any) {
        try {
            if (any == null || any.getCluster() == null) return false;
            int cores = 0;
            var it = any.getCluster().getBlockEntities();
            while (it.hasNext()) {
                var te = it.next();
                if (te instanceof UploadCoreBlockEntity) {
                    cores++;
                }
            }
            return cores >= 1; // 至少一个即可
        } catch (Throwable t) {
            return false;
        }
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
}
