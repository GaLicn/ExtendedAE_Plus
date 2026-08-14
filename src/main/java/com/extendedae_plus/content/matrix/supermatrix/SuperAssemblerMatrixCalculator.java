package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.content.matrix.HybridCoreBlockEntity;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class SuperAssemblerMatrixCalculator {

    private static final int MAX_PARTS_TO_SCAN = 4096;
    private static final int MIN_DELTA = 2;
    private static final int MAX_AXIS_LENGTH = 9;

    private SuperAssemblerMatrixCalculator() {
    }

    public static void recalculate(ServerLevel level, BlockPos start) {
        var positions = collectConnectedPositions(level, start);
        if (positions.isEmpty()) {
            return;
        }

        var parts = collectSuperParts(level, positions);
        var min = min(positions);
        var max = max(positions);
        var cluster = verifyAndCreate(level, min, max);
        if (cluster == null) {
            clear(parts);
            return;
        }

        destroyExisting(parts);
        for (var pos : BlockPos.betweenClosed(min, max)) {
            var part = asPart(level.getBlockEntity(pos));
            if (part != null) {
                cluster.addPart(part);
            }
        }
        cluster.done();
    }

    private static Set<BlockPos> collectConnectedPositions(ServerLevel level, BlockPos start) {
        var result = new HashSet<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(start.immutable());

        while (!queue.isEmpty() && result.size() < MAX_PARTS_TO_SCAN) {
            var pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }
            if (!isStructureComponent(level.getBlockEntity(pos))) {
                continue;
            }
            result.add(pos);
            for (var direction : net.minecraft.core.Direction.values()) {
                queue.add(pos.relative(direction));
            }
        }
        return result;
    }

    private static Set<SuperAssemblerMatrixPart> collectSuperParts(ServerLevel level, Set<BlockPos> positions) {
        var parts = new HashSet<SuperAssemblerMatrixPart>();
        for (var pos : positions) {
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

    private static BlockPos min(Set<BlockPos> positions) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int z = Integer.MAX_VALUE;
        for (var pos : positions) {
            x = Math.min(x, pos.getX());
            y = Math.min(y, pos.getY());
            z = Math.min(z, pos.getZ());
        }
        return new BlockPos(x, y, z);
    }

    private static BlockPos max(Set<BlockPos> positions) {
        int x = Integer.MIN_VALUE;
        int y = Integer.MIN_VALUE;
        int z = Integer.MIN_VALUE;
        for (var pos : positions) {
            x = Math.max(x, pos.getX());
            y = Math.max(y, pos.getY());
            z = Math.max(z, pos.getZ());
        }
        return new BlockPos(x, y, z);
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
