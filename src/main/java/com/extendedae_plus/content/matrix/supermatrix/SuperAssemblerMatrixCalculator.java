package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.content.matrix.HybridCoreBlockEntity;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class SuperAssemblerMatrixCalculator {

    private static final int MAX_PARTS_TO_SCAN = 4096;
    private static final int MIN_DELTA = 2;
    private static final int[] ULTIMATE_LOAD_RECALCULATION_DELAYS = { 1, 20, 100, 300 };
    private static final Map<ServerLevel, LongOpenHashSet> PENDING_RECALCULATIONS = new IdentityHashMap<>();
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<LongOpenHashSet>> DELAYED_RECALCULATIONS = new IdentityHashMap<>();

    private SuperAssemblerMatrixCalculator() {
    }

    public static void clearScheduledRecalculations() {
        PENDING_RECALCULATIONS.clear();
        DELAYED_RECALCULATIONS.clear();
    }

    /** 将同一刻内的方块事件合并，避免巨构在一次搭建中重复完整校验。 */
    public static void scheduleRecalculate(ServerLevel level, BlockPos start) {
        PENDING_RECALCULATIONS.computeIfAbsent(level, ignored -> new LongOpenHashSet()).add(start.asLong());
    }

    /** 仅结构部件变化时触发重算，忽略相邻机器和红石的普通方块更新。 */
    public static void scheduleAfterNeighborChange(ServerLevel level, BlockPos start, BlockPos changedPos) {
        if (isStructureComponent(level.getBlockEntity(changedPos))) {
            scheduleRecalculate(level, start);
        }
    }

    /** 上传核心随区块载入后分阶段复核，覆盖跨区块方块实体的异步初始化。 */
    public static void scheduleUltimateLoadRecalculate(ServerLevel level, BlockPos uploadCorePos) {
        for (int delay : ULTIMATE_LOAD_RECALCULATION_DELAYS) {
            scheduleDelayedRecalculate(level, uploadCorePos, delay);
        }
    }

    private static void scheduleDelayedRecalculate(ServerLevel level, BlockPos start, int delay) {
        long dueTick = level.getGameTime() + delay;
        DELAYED_RECALCULATIONS.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(dueTick, ignored -> new LongOpenHashSet())
                .add(start.asLong());
    }

    /** 在世界刻末尾只检查每个连通结构一次。 */
    public static void processScheduledRecalculations(ServerLevel level) {
        var delayed = DELAYED_RECALCULATIONS.get(level);
        if (delayed != null) {
            long gameTime = level.getGameTime();
            var dueTicks = new LongOpenHashSet();
            for (var iterator = delayed.keySet().iterator(); iterator.hasNext();) {
                long dueTick = iterator.nextLong();
                if (dueTick <= gameTime) {
                    dueTicks.add(dueTick);
                }
            }
            for (var iterator = dueTicks.iterator(); iterator.hasNext();) {
                var positions = delayed.remove(iterator.nextLong());
                if (positions != null) {
                    PENDING_RECALCULATIONS.computeIfAbsent(level, ignored -> new LongOpenHashSet()).addAll(positions);
                }
            }
            if (delayed.isEmpty()) {
                DELAYED_RECALCULATIONS.remove(level);
            }
        }

        var pending = PENDING_RECALCULATIONS.remove(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        while (!pending.isEmpty()) {
            long start = pending.iterator().nextLong();
            pending.remove(start);
            // 上传核心是终极结构的固定锚点，直接校验以绕过稀疏内部的连通扫描。
            if (tryFormUltimate(level, UltimateSuperAssemblerMatrixStructure.findMatchAtUploadCore(level,
                    BlockPos.of(start)))) {
                continue;
            }
            var positions = collectConnectedPositions(level, start);
            pending.removeAll(positions);
            recalculate(level, positions);
        }
    }

    private static void recalculate(ServerLevel level, LongOpenHashSet positions) {
        if (positions.isEmpty()) {
            return;
        }

        var parts = collectSuperParts(level, positions);
        var ultimateMatch = UltimateSuperAssemblerMatrixStructure.findMatch(level, positions);
        if (ultimateMatch != null) {
            tryFormUltimate(level, ultimateMatch);
            return;
        }

        var min = min(positions);
        var max = max(positions);
        var cluster = verifyAndCreate(level, min, max);
        if (cluster == null) {
            clear(parts);
            return;
        }
        if (isAlreadyFormed(parts)) {
            return;
        }

        form(level, parts, min, max, cluster);
    }

    /** 供固定结构的一键搭建器在全部方块落位后立即完成成型。 */
    public static boolean formUltimate(ServerLevel level, BlockPos origin) {
        if (!UltimateSuperAssemblerMatrixStructure.matchesAt(level, origin)) {
            return false;
        }
        return tryFormUltimate(level, new UltimateSuperAssemblerMatrixStructure.Match(origin));
    }

    private static boolean tryFormUltimate(ServerLevel level, @org.jetbrains.annotations.Nullable UltimateSuperAssemblerMatrixStructure.Match match) {
        if (match == null) {
            return false;
        }

        var parts = collectUltimateParts(level, match.origin(), match.max());
        if (isAlreadyFormed(parts)) {
            return true;
        }
        // 终极结构已通过固定定义校验，清除原版矩阵加载期间生成的临时集群。
        for (var part : parts) {
            if (part instanceof TileAssemblerMatrixBase matrixPart) {
                matrixPart.disconnect(false);
            }
        }
        form(level, parts, match.origin(), match.max(),
                SuperAssemblerMatrixCluster.ultimate(match.origin(), match.max()));
        return true;
    }

    private static void form(ServerLevel level, Set<SuperAssemblerMatrixPart> parts, BlockPos min, BlockPos max,
            SuperAssemblerMatrixCluster cluster) {
        destroyExisting(parts);
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var part = asPart(level.getBlockEntity(pos));
            if (part != null) {
                cluster.addPart(part);
            }
        }
        cluster.done();
    }

    private static LongOpenHashSet collectConnectedPositions(ServerLevel level, long start) {
        var result = new LongOpenHashSet();
        var visited = new LongOpenHashSet();
        var queue = new LongArrayFIFOQueue();
        var position = new BlockPos.MutableBlockPos();
        queue.enqueue(start);

        while (!queue.isEmpty() && result.size() < MAX_PARTS_TO_SCAN) {
            long packedPosition = queue.dequeueLong();
            if (!visited.add(packedPosition)) {
                continue;
            }
            position.set(packedPosition);
            if (!isStructureComponent(level.getBlockEntity(position))) {
                continue;
            }
            result.add(packedPosition);
            for (var direction : Direction.values()) {
                queue.enqueue(BlockPos.asLong(position.getX() + direction.getStepX(),
                        position.getY() + direction.getStepY(), position.getZ() + direction.getStepZ()));
            }
        }
        return result;
    }

    private static Set<SuperAssemblerMatrixPart> collectSuperParts(ServerLevel level, LongOpenHashSet positions) {
        var parts = new HashSet<SuperAssemblerMatrixPart>();
        var position = new BlockPos.MutableBlockPos();
        for (var iterator = positions.iterator(); iterator.hasNext();) {
            position.set(iterator.nextLong());
            var part = asPart(level.getBlockEntity(position));
            if (part != null) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static Set<SuperAssemblerMatrixPart> collectSuperParts(ServerLevel level, BlockPos min, BlockPos max) {
        var parts = new HashSet<SuperAssemblerMatrixPart>();
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var part = asPart(level.getBlockEntity(pos));
            if (part != null) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static Set<SuperAssemblerMatrixPart> collectUltimateParts(ServerLevel level, BlockPos min, BlockPos max) {
        var parts = new HashSet<SuperAssemblerMatrixPart>();
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SuperAssemblerMatrixPart part) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static SuperAssemblerMatrixCluster verifyAndCreate(ServerLevel level, BlockPos min, BlockPos max) {
        if (max.getX() - min.getX() < MIN_DELTA
                || max.getY() - min.getY() < MIN_DELTA
                || max.getZ() - min.getZ() < MIN_DELTA) {
            return null;
        }

        boolean anyHybridCore = false;
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var blockEntity = level.getBlockEntity(pos);
            if (!isStructureComponent(blockEntity)) {
                return null;
            }
            if (isInternal(pos, min, max)) {
                if (!isSuperFunction(blockEntity)) {
                    return null;
                }
                anyHybridCore |= blockEntity instanceof HybridCoreBlockEntity;
            } else if (isEdge(pos, min, max)) {
                if (!(blockEntity instanceof SuperAssemblerMatrixFrameBlockEntity)) {
                    return null;
                }
            } else {
                if (!(blockEntity instanceof SuperAssemblerMatrixWallBlockEntity)
                        && !(blockEntity instanceof TileAssemblerMatrixGlass)) {
                    return null;
                }
            }
        }
        return anyHybridCore ? new SuperAssemblerMatrixCluster(min, max) : null;
    }

    private static void clear(Set<SuperAssemblerMatrixPart> parts) {
        for (var part : parts) {
            var cluster = part.eap$getSuperMatrixCluster();
            if (cluster != null) {
                cluster.destroy();
            } else {
                part.eap$setSuperMatrixCluster(null);
                part.eap$updateSuperMatrixStatus();
            }
        }
    }

    private static void destroyExisting(Set<SuperAssemblerMatrixPart> parts) {
        var destroyed = new HashSet<SuperAssemblerMatrixCluster>();
        for (var part : parts) {
            var cluster = part.eap$getSuperMatrixCluster();
            if (cluster != null && destroyed.add(cluster)) {
                cluster.destroy();
            }
        }
    }

    private static BlockPos min(LongOpenHashSet positions) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int z = Integer.MAX_VALUE;
        for (var iterator = positions.iterator(); iterator.hasNext();) {
            long pos = iterator.nextLong();
            x = Math.min(x, BlockPos.getX(pos));
            y = Math.min(y, BlockPos.getY(pos));
            z = Math.min(z, BlockPos.getZ(pos));
        }
        return new BlockPos(x, y, z);
    }

    private static BlockPos max(LongOpenHashSet positions) {
        int x = Integer.MIN_VALUE;
        int y = Integer.MIN_VALUE;
        int z = Integer.MIN_VALUE;
        for (var iterator = positions.iterator(); iterator.hasNext();) {
            long pos = iterator.nextLong();
            x = Math.max(x, BlockPos.getX(pos));
            y = Math.max(y, BlockPos.getY(pos));
            z = Math.max(z, BlockPos.getZ(pos));
        }
        return new BlockPos(x, y, z);
    }

    private static boolean isAlreadyFormed(Set<SuperAssemblerMatrixPart> parts) {
        SuperAssemblerMatrixCluster cluster = null;
        for (var part : parts) {
            var partCluster = part.eap$getSuperMatrixCluster();
            if (partCluster == null || partCluster.isDestroyed()) {
                return false;
            }
            if (cluster == null) {
                cluster = partCluster;
            } else if (cluster != partCluster) {
                return false;
            }
        }
        return cluster != null;
    }

    private static boolean isInternal(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() < max.getX() && pos.getX() > min.getX()
                && pos.getY() < max.getY() && pos.getY() > min.getY()
                && pos.getZ() < max.getZ() && pos.getZ() > min.getZ();
    }

    private static boolean isEdge(BlockPos pos, BlockPos min, BlockPos max) {
        int boundary = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) {
            boundary++;
        }
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) {
            boundary++;
        }
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
            boundary++;
        }
        return boundary >= 2;
    }

    private static boolean isSuperFunction(BlockEntity blockEntity) {
        // 超级装配矩阵内部只允许混合核心，旧核心不再参与成型。
        return blockEntity instanceof HybridCoreBlockEntity;
    }

    private static boolean isStructureComponent(BlockEntity blockEntity) {
        if (blockEntity instanceof TileAssemblerMatrixBase oldMatrixPart && oldMatrixPart.isFormed()) {
            return false;
        }
        return blockEntity instanceof SuperAssemblerMatrixPart
                || blockEntity instanceof TileAssemblerMatrixGlass;
    }

    private static SuperAssemblerMatrixPart asPart(BlockEntity blockEntity) {
        if (blockEntity instanceof TileAssemblerMatrixBase oldMatrixPart && oldMatrixPart.isFormed()) {
            return null;
        }
        return blockEntity instanceof SuperAssemblerMatrixPart part ? part : null;
    }
}
