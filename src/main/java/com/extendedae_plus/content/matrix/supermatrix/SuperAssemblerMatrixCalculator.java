package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.content.matrix.HybridCoreBlockEntity;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
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
    private static final int MAX_AXIS_LENGTH = 9;
    private static final Map<ServerLevel, LongOpenHashSet> PENDING_RECALCULATIONS = new IdentityHashMap<>();

    private SuperAssemblerMatrixCalculator() {
    }

    /** 将同一刻内的方块事件合并，避免巨构在一次搭建中重复完整校验。 */
    public static void scheduleRecalculate(ServerLevel level, BlockPos start) {
        PENDING_RECALCULATIONS.computeIfAbsent(level, ignored -> new LongOpenHashSet()).add(start.asLong());
    }

    /** 在世界刻末尾只检查每个连通结构一次。 */
    public static void processScheduledRecalculations(ServerLevel level) {
        var pending = PENDING_RECALCULATIONS.remove(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        while (!pending.isEmpty()) {
            long start = pending.iterator().nextLong();
            pending.remove(start);
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
            if (isAlreadyFormed(parts)) {
                return;
            }
            form(level, parts, ultimateMatch.origin(), ultimateMatch.max(),
                    SuperAssemblerMatrixCluster.ultimate(ultimateMatch.origin(), ultimateMatch.max()));
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

        var match = new UltimateSuperAssemblerMatrixStructure.Match(origin);
        var parts = collectSuperParts(level, match.origin(), match.max());
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

    private static SuperAssemblerMatrixCluster verifyAndCreate(ServerLevel level, BlockPos min, BlockPos max) {
        // 外框任一方向超过 9 格时，不允许形成超级装配矩阵。
        if (max.getX() - min.getX() + 1 > MAX_AXIS_LENGTH
                || max.getY() - min.getY() + 1 > MAX_AXIS_LENGTH
                || max.getZ() - min.getZ() + 1 > MAX_AXIS_LENGTH) {
            return null;
        }
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
