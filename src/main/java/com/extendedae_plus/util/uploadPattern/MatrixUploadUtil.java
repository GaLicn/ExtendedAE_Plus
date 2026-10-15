package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 装配矩阵侧的样板上传工具：收集矩阵图样库存、判定重复、写入样板并记录上传位置。
 *
 * <p>供应器侧与上传记录的存储由 {@link ProviderUploadUtil} 负责；本类在上传成功后
 * 通过 {@code ProviderUploadUtil.recordMatrixUpload} 落盘「最近一次上传」记录，
 * 依赖方向与 1.20 分支一致（矩阵 -> 供应器）。</p>
 */
public final class MatrixUploadUtil {

    private MatrixUploadUtil() {
    }

    private record MatrixInventoryTarget(InternalInventory insertInventory,
                                         InternalInventory patternInventory,
                                         BlockPos pos,
                                         boolean plus) {}

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
        var encodedSlot = ((PatternEncodingTermMenuAccessor) (Object) menu)
                .eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            sendMessage(player, "extendedae_plus.message.upload.no_pattern");
            return false;
        }

        // 仅允许“合成/锻造台/切石机图样”
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
        if (!(details instanceof AECraftingPattern
                || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern)) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail");
            return false;
        }

        // 获取 AE 网络
        IGrid grid = ProviderUploadUtil.resolveGrid(menu);
        if (grid == null) {
            sendMessage(player, "extendedae_plus.message.network.invalid");
            return false;
        }

        // 在尝试上传之前，检查装配矩阵是否已经存在相同样板（物品与NBT完全一致）
        if (matrixContainsPattern(grid, stack)) {
            // 直接提醒并跳过上传，并将同等数量的空白样板放回空白样板槽，否则退回玩家背包
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
            }
            // 回收逻辑与供应器重复命中共用，避免两处漂移
            ProviderUploadUtil.recycleEncodedPatternAsBlanks(player, menu, stack);
            return false;
        }

        // 收集所有可用的装配矩阵（图样模块）内部库存并逐一尝试（遵循其过滤规则）
        List<MatrixInventoryTarget> inventories = findAllMatrixPatternInventories(grid);
        if (!inventories.isEmpty()) {
            for (MatrixInventoryTarget target : inventories) {
                if (target == null || target.insertInventory() == null || target.patternInventory() == null) continue;
                ItemStack toInsert = stack.copy();
                ItemStack[] before = ProviderUploadUtil.snapshotInventory(target.patternInventory(), target.patternInventory().size());
                ItemStack remain = target.insertInventory().addItems(toInsert);
                if (remain.getCount() < stack.getCount()) {
                    int inserted = stack.getCount() - remain.getCount();
                    stack.shrink(inserted);
                    if (stack.isEmpty()) {
                        encodedSlot.set(ItemStack.EMPTY);
                    }
                    ProviderUploadUtil.recordMatrixUpload(
                            player,
                            target.pos(),
                            player.level().dimension().location().toString(),
                            target.plus(),
                            ProviderUploadUtil.findLastChangedSlot(target.patternInventory(), before, target.patternInventory().size())
                    );
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
            // 所有内部库存都无法接收 -> 尝试 capability 回退
        }

        // 回退：尝试 Forge 能力（可能为聚合图样仓），同样遍历所有矩阵
        List<?> handlers = findAllMatrixPatternHandlers(grid);
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
     * 将给定的已编码样板直接上传到装配矩阵（不依赖编码终端槽位）。
     *
     * @param player 服务器玩家
     * @param pattern 已编码样板
     * @param grid AE 网络
     * @return 是否成功插入矩阵
     */
    public static boolean uploadPatternToMatrix(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (player == null || grid == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
            return false;
        }

        IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, player.level());
        if (!(details instanceof AECraftingPattern
                || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern)) {
            sendMessage(player, "extendedae_plus.upload_to_matrix.fail");
            return false;
        }

        if (matrixContainsPattern(grid, pattern)) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
            }
            return false;
        }

        List<MatrixInventoryTarget> inventories = findAllMatrixPatternInventories(grid);
        if (!inventories.isEmpty()) {
            for (MatrixInventoryTarget target : inventories) {
                if (target == null || target.insertInventory() == null || target.patternInventory() == null) continue;
                ItemStack toInsert = pattern.copy();
                ItemStack[] before = ProviderUploadUtil.snapshotInventory(target.patternInventory(), target.patternInventory().size());
                ItemStack remain = target.insertInventory().addItems(toInsert);
                if (remain.getCount() < toInsert.getCount()) {
                    ProviderUploadUtil.recordMatrixUpload(
                            player,
                            target.pos(),
                            player.level().dimension().location().toString(),
                            target.plus(),
                            ProviderUploadUtil.findLastChangedSlot(target.patternInventory(), before, target.patternInventory().size())
                    );
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
        }

        List<?> handlers = findAllMatrixPatternHandlers(grid);
        if (!handlers.isEmpty()) {
            for (Object cap : handlers) {
                ItemStack toInsert = pattern.copy();
                ItemStack remain = insertIntoAnySlot(cap, toInsert);
                if (remain.getCount() < toInsert.getCount()) {
                    sendMessage(player, "extendedae_plus.upload_to_matrix.success");
                    return true;
                }
            }
        }

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
    private static List<MatrixInventoryTarget> findAllMatrixPatternInventories(IGrid grid) {
        List<MatrixInventoryTarget> result = new ArrayList<>();
        if(grid== null) return result;
        try {
            // 超级矩阵由主控聚合所有混合核心库存，不依赖装配矩阵上传核心。
            for (SuperAssemblerMatrixBlockEntity superMatrix : findSuperMatrices(grid)) {
                if (superMatrix == null || !superMatrix.isVisibleInTerminal() || superMatrix.getGrid() != grid) {
                    continue;
                }
                InternalInventory inventory = superMatrix.getTerminalPatternInventory();
                if (inventory != null && inventory.size() > 0) {
                    result.add(new MatrixInventoryTarget(inventory, inventory,
                            superMatrix.getBlockPos(), true));
                }
            }

            Set<TileAssemblerMatrixPattern> allTiles = grid.getMachines(TileAssemblerMatrixPattern.class);
            Set<PatternCorePlusBlockEntity> myAllTiles = grid.getMachines(PatternCorePlusBlockEntity.class);

            // 用 Set 记录已经扫描过的集群，避免重复调用 clusterHasSingleUploadCore
            Set<ClusterAssemblerMatrix> scannedClusters = new HashSet<>();

            for (TileAssemblerMatrixPattern tile : allTiles) {
                if (tile == null || !tile.isFormed() || !tile.getMainNode().isActive()) continue;

                ClusterAssemblerMatrix cluster = tile.getCluster();
                if (cluster == null) continue;

                // 如果该集群已经扫描过，或者该集群含 UploadCore，则处理 tile
                if (scannedClusters.contains(cluster) || clusterHasSingleUploadCore(cluster)) {
                    scannedClusters.add(cluster); // 标记为已扫描

                    InternalInventory insertInv = tile.getExposedInventory();
                    InternalInventory patternInv = tile.getTerminalPatternInventory();
                    if (insertInv != null && patternInv != null) {
                        result.add(new MatrixInventoryTarget(insertInv, patternInv, tile.getBlockPos(), false));
                    }
                }
            }

            for (PatternCorePlusBlockEntity myTile : myAllTiles) {
                if (myTile == null || !myTile.isFormed() || !myTile.getMainNode().isActive()) continue;

                ClusterAssemblerMatrix cluster = myTile.getCluster();
                if (cluster == null) continue;

                // 如果该集群已经扫描过，或者该集群含 UploadCore，则处理 tile
                if (scannedClusters.contains(cluster) || clusterHasSingleUploadCore(cluster)) {
                    scannedClusters.add(cluster); // 标记为已扫描

                    InternalInventory insertInv = myTile.getExposedInventory();
                    InternalInventory patternInv = myTile.getTerminalPatternInventory();
                    if (insertInv != null && patternInv != null) {
                        result.add(new MatrixInventoryTarget(insertInv, patternInv, myTile.getBlockPos(), true));
                    }
                }
            }
        } catch (Throwable t) {
        }
        return result;
    }

    /**
     * AE2 按节点实际类索引机器，需遍历框架/墙等具体子类才能找到超级矩阵主控。
     */
    private static List<SuperAssemblerMatrixBlockEntity> findSuperMatrices(IGrid grid) {
        var result = new ArrayList<SuperAssemblerMatrixBlockEntity>();
        if (grid == null) {
            return result;
        }

        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!SuperAssemblerMatrixBlockEntity.class.isAssignableFrom(machineClass)) {
                continue;
            }
            for (Object machine : grid.getMachines(machineClass)) {
                if (machine instanceof SuperAssemblerMatrixBlockEntity superMatrix) {
                    result.add(superMatrix);
                }
            }
        }
        return result;
    }

    /**
     * 在给定 AE Grid 中收集所有已成型的装配矩阵的聚合图样仓 IItemHandler（若可用）。
     */
    private static List<?> findAllMatrixPatternHandlers(IGrid grid) {
        // NeoForge 1.21 能力系统与 API 变更，此处先返回空列表，避免编译期依赖旧能力系统
        return Collections.emptyList();
    }

    private static boolean matrixContainsPattern(IGrid grid, ItemStack pattern) {
        if (grid == null || pattern == null || pattern.isEmpty()) return false;
        ItemStack patternCopy = ProviderUploadUtil.stripEncodePlayer(pattern);
        // 先检查提供外部插入视图的内部库存
        for (MatrixInventoryTarget target : findAllMatrixPatternInventories(grid)) {
            InternalInventory inv = target == null ? null : target.patternInventory();
            if (inv == null) continue;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(ProviderUploadUtil.stripEncodePlayer(s), patternCopy)) {
                    return true;
                }
            }
        }
        // 1.21 暂不检查聚合能力视图，能力系统适配后再补充
        return false;
    }

    /**
     * 能力系统（IItemHandler）未迁移前的占位插入：直接返回原始栈，表示未能插入。
     */
    private static ItemStack insertIntoAnySlot(Object handler, ItemStack stack) {
        return stack.copy();
    }

    /**
     * 判断给定矩阵集群中是否存在"装配矩阵上传核心"。
     * 要求：至少存在 1 个即可，不限制数量。
     * 传入任意属于该集群的 Tile（如 Pattern/Crafter/Frame 等）。
     */
    private static boolean clusterHasSingleUploadCore(ClusterAssemblerMatrix any) {
        try {
            if (any == null) return false;
            int cores = 0;
            var it = any.getBlockEntities();
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
     * 发送消息给玩家
     *
     * <p>与原实现一致，刻意保留为空实现：矩阵上传过程中的失败文案不应激活，
     * 已确认成功的提示由 {@link ProviderUploadUtil#uploadFromEncodingMenuByPriority} 负责。</p>
     */
    private static void sendMessage(ServerPlayer player, String key, Object... args) {
    }
}