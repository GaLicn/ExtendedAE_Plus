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
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.extendedae_plus.util.GlobalSendMessage.sendPlayerMessage;

/**
 * gtlcore 分子操纵者样板上传
 * 用于从 AE2 的样板编码终端上传至分子操纵者（仅合成样板）。
 */
public final class GTMatrixUploadUtil {
    private GTMatrixUploadUtil() {
    }

    /**
     * 从 AE2 的样板编码终端菜单上传当前“已编码合成样板”至 gtlcore 分子操纵者（仅合成样板）
     *
     * @param player 服务器玩家
     * @param menu   PatternEncodingTermMenu
     */
    public static void uploadFromEncodingMenuToMatrix(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player == null || menu == null) return;
        // 读取已编码槽位的物品
        RestrictedInputSlot encodedSlot = ((PatternEncodingTermMenuAccessor) menu).eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) return;

        // 仅允许“合成/锻造台/切石机样板”
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
        if (!(details instanceof AECraftingPattern
                || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern)) {
            return;
        }

        // 获取 AE 网络
        IGridNode node = menu.getNetworkNode();
        if (node == null) return;

        IGrid grid = node.getGrid();
        if (grid == null) return;

        int stackCount = stack.getCount();
        ItemStack toInsert = stack.copy();

        // 收集所有可用的装配矩阵（图样模块）内部库存并逐一尝试（遵循其过滤规则）
        List<InternalInventory> inventories = findAllGTMatrixPatternInventories(grid);

        // 在尝试上传之前，检查装配矩阵是否已经存在相同样板（物品与NBT完全一致）
        if (GTMatrixContainsPattern(inventories, stack)) {
            // 直接提醒并跳过上传，并将同等数量的空白样板放回空白样板槽，否则退回玩家背包
            sendPlayerMessage(player, Component.translatable("extendedae_plus.upload_to_GTMatrix.repetition"));
            refundBlankPattern(player, menu, stackCount);
            encodedSlot.set(ItemStack.EMPTY);
            return;
        }
        // 尝试插入
        for (InternalInventory inv : inventories) {
            if (inv == null) continue;
            ItemStack remain = inv.addItems(toInsert);
            if (remain.getCount() < stackCount) {
                completeUploadSuccess(player, encodedSlot, stack, remain);
                return;
            }
        }

        // 未找到可用矩阵或全部拒收
        sendPlayerMessage(player,
                inventories.isEmpty()
                        ? Component.translatable("extendedae_plus.upload_to_matrix.fail_no_GTMatrix")
                        : Component.translatable("extendedae_plus.upload_to_GTMatrix.fail_full"));
    }

    /**
     * 在给定 AE Grid 中收集在线的GT分子的用于外部插入的内部库存。
     */
    private static List<InternalInventory> findAllGTMatrixPatternInventories(IGrid grid) {
        List<InternalInventory> result = new ArrayList<>();
        if (grid == null) return result;

        try {
            // 获取网络中所有 Pattern Tile
            Set<MEMolecularAssemblerIOPartMachine> allTiles = grid.getMachines(MEMolecularAssemblerIOPartMachine.class);

            for (MEMolecularAssemblerIOPartMachine tile : allTiles) {
                if (tile == null || !tile.isFormed() || !tile.getMainNode()
                        .isActive()) continue;
                InternalInventory inv = tile.getTerminalPatternInventory();
                if (inv != null) {
                    result.add(inv);
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 检查GT分子装配矩阵中是否已存在与给定样板完全相同的物品（含NBT）。
     */
    // todo
    private static boolean GTMatrixContainsPattern(@NotNull List<InternalInventory> inventories, @NotNull ItemStack pattern) {
        for (InternalInventory inv : inventories) {
            if (inv == null) continue;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 上传成功后处理：清空编码槽，发送提示。
     */
    private static void completeUploadSuccess(ServerPlayer player, RestrictedInputSlot encodedSlot, ItemStack stack, ItemStack remain) {
        int inserted = stack.getCount() - remain.getCount();
        if (inserted > 0) {
            stack.shrink(inserted);
            if (stack.isEmpty()) encodedSlot.set(ItemStack.EMPTY);
            sendPlayerMessage(player, Component.translatable("extendedae_plus.upload_to_GTMatrix.success"));
        }
    }

    /**
     * 当发现重复样板时返还空白样板。
     */
    private static void refundBlankPattern(ServerPlayer player, PatternEncodingTermMenu menu, int count) {
        try {
            var accessor = (PatternEncodingTermMenuAccessor) menu;
            var blankSlot = accessor.eap$getBlankPatternSlot();
            ItemStack blanks = AEItems.BLANK_PATTERN.stack(count);
            if (blankSlot != null && blankSlot.mayPlace(blanks)) {
                ItemStack remain = blankSlot.safeInsert(blanks);
                if (!remain.isEmpty() && player != null) {
                    player.getInventory()
                            .placeItemBackInInventory(remain, false);
                }
            } else if (player != null) {
                player.getInventory()
                        .placeItemBackInInventory(blanks, false);
            }
        } catch (Throwable t) {
            if (player != null) {
                player.getInventory()
                        .placeItemBackInInventory(AEItems.BLANK_PATTERN.stack(count), false);
            }
        }
    }
}
