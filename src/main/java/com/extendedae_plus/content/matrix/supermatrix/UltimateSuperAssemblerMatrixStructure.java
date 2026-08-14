package com.extendedae_plus.content.matrix.supermatrix;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/** 终极超级装配矩阵的固定结构定义。 */
public final class UltimateSuperAssemblerMatrixStructure {

    // 312 个混合核心，每个保留 72 个样板槽位。
    public static final int PATTERN_CAPACITY = 22_464;
    public static final int PARALLEL_BUDGET = Integer.MAX_VALUE;

    private static final int SIZE_X = 14;
    private static final int SIZE_Y = 16;
    private static final int SIZE_Z = 14;
    private static final ResourceLocation DEFINITION = new ResourceLocation(
            ExtendedAEPlus.MODID, "structures/ultimate_super_assembler_matrix.json");
    private static final List<BlockPos> UPLOAD_CORE_ANCHORS = List.of(
            new BlockPos(6, 4, 6), new BlockPos(7, 4, 6),
            new BlockPos(6, 4, 7), new BlockPos(7, 4, 7));

    private static @Nullable List<ExpectedBlock> expectedBlocks;
    private static @Nullable List<StructureBlock> previewBlocks;

    private UltimateSuperAssemblerMatrixStructure() {
    }

    public static @Nullable Match findMatch(ServerLevel level, LongOpenHashSet connectedPositions) {
        var definition = getDefinition(level);
        if (definition == null) {
            return null;
        }

        var checkedOrigins = new LongOpenHashSet();
        var position = new BlockPos.MutableBlockPos();
        for (var iterator = connectedPositions.iterator(); iterator.hasNext();) {
            long packedPosition = iterator.nextLong();
            position.set(packedPosition);
            if (!(level.getBlockEntity(position) instanceof UploadCoreBlockEntity)) {
                continue;
            }
            for (var anchor : UPLOAD_CORE_ANCHORS) {
                long origin = BlockPos.asLong(position.getX() - anchor.getX(), position.getY() - anchor.getY(),
                        position.getZ() - anchor.getZ());
                if (checkedOrigins.add(origin) && matches(level, origin, definition)) {
                    return new Match(BlockPos.of(origin));
                }
            }
        }
        return null;
    }

    public static boolean placeIfClear(ServerLevel level, BlockPos origin) {
        var definition = getDefinition(level.getServer().getResourceManager());
        if (definition == null || !isAreaClear(level, origin, definition)) {
            return false;
        }

        // 所有目标方块位确认安全后才开始放置，避免搭建一半时覆盖现有方块。
        for (var expected : definition) {
            if (!expected.air) {
                level.setBlock(origin.offset(expected.x, expected.y, expected.z), expected.block.defaultBlockState(), 3);
            }
        }
        // 一键搭建已知完整结构的原点，直接成型，避免稀疏结构被连通扫描遗漏。
        return SuperAssemblerMatrixCalculator.formUltimate(level, origin);
    }

    public static List<StructureBlock> getPreviewBlocks(ResourceManager resourceManager) {
        var definition = getDefinition(resourceManager);
        return definition == null ? List.of() : previewBlocks;
    }

    public static boolean matchesAt(ServerLevel level, BlockPos origin) {
        var definition = getDefinition(level);
        return definition != null && matches(level, origin.asLong(), definition);
    }

    private static boolean matches(ServerLevel level, long origin, List<ExpectedBlock> definition) {
        var position = new BlockPos.MutableBlockPos();
        int originX = BlockPos.getX(origin);
        int originY = BlockPos.getY(origin);
        int originZ = BlockPos.getZ(origin);
        for (var expected : definition) {
            // 空气位不是结构部件，允许玩家在这些坐标保留其他方块。
            if (!expected.air && !level.getBlockState(position.set(
                    originX + expected.x, originY + expected.y, originZ + expected.z)).is(expected.block)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAreaClear(ServerLevel level, BlockPos origin, List<ExpectedBlock> definition) {
        for (var expected : definition) {
            // 结构中的空气仅代表无需搭建的位置，不限制玩家在其中放置其他方块。
            if (!expected.air && !level.getBlockState(origin.offset(expected.x, expected.y, expected.z)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable List<ExpectedBlock> getDefinition(ServerLevel level) {
        return getDefinition(level.getServer().getResourceManager());
    }

    private static @Nullable List<ExpectedBlock> getDefinition(ResourceManager resourceManager) {
        if (expectedBlocks != null) {
            return expectedBlocks;
        }
        synchronized (UltimateSuperAssemblerMatrixStructure.class) {
            if (expectedBlocks == null) {
                expectedBlocks = loadDefinition(resourceManager);
            }
        }
        return expectedBlocks;
    }

    private static @Nullable List<ExpectedBlock> loadDefinition(ResourceManager resourceManager) {
        var resource = resourceManager.getResource(DEFINITION);
        if (resource.isEmpty()) {
            EAP$LOGGER.error("找不到终极超级装配矩阵结构文件: {}", DEFINITION);
            return null;
        }

        try (var reader = resource.get().openAsReader()) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var size = root.getAsJsonArray("size");
            if (size.get(0).getAsInt() != SIZE_X || size.get(1).getAsInt() != SIZE_Y
                    || size.get(2).getAsInt() != SIZE_Z) {
                EAP$LOGGER.error("终极超级装配矩阵结构尺寸不正确");
                return null;
            }

            var definition = new ArrayList<ExpectedBlock>(SIZE_X * SIZE_Y * SIZE_Z);
            var occupied = new boolean[SIZE_X * SIZE_Y * SIZE_Z];
            for (var element : root.getAsJsonArray("blocks")) {
                var entry = element.getAsJsonObject();
                var position = entry.getAsJsonArray("pos");
                int x = position.get(0).getAsInt();
                int y = position.get(1).getAsInt();
                int z = position.get(2).getAsInt();
                if (x < 0 || x >= SIZE_X || y < 0 || y >= SIZE_Y || z < 0 || z >= SIZE_Z) {
                    EAP$LOGGER.error("终极超级装配矩阵结构包含越界坐标");
                    return null;
                }
                int index = x + z * SIZE_X + y * SIZE_X * SIZE_Z;
                if (occupied[index]) {
                    EAP$LOGGER.error("终极超级装配矩阵结构包含重复坐标");
                    return null;
                }
                occupied[index] = true;

                String state = entry.get("state").getAsString();
                int propertyStart = state.indexOf('[');
                String blockId = propertyStart < 0 ? state : state.substring(0, propertyStart);
                var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));
                if (block == Blocks.AIR && !"minecraft:air".equals(blockId)) {
                    EAP$LOGGER.error("终极超级装配矩阵结构包含未知方块: {}", blockId);
                    return null;
                }
                definition.add(new ExpectedBlock(x, y, z, block == Blocks.AIR, block));
            }
            if (definition.size() != occupied.length) {
                EAP$LOGGER.error("终极超级装配矩阵结构没有定义全部坐标");
                return null;
            }
            var loaded = List.copyOf(definition);
            previewBlocks = loaded.stream()
                    .filter(expected -> !expected.air)
                    .map(expected -> new StructureBlock(new BlockPos(expected.x, expected.y, expected.z), expected.block))
                    .toList();
            return loaded;
        } catch (IOException | RuntimeException exception) {
            EAP$LOGGER.error("读取终极超级装配矩阵结构失败", exception);
            return null;
        }
    }

    public record Match(BlockPos origin) {
        public BlockPos max() {
            return this.origin.offset(SIZE_X - 1, SIZE_Y - 1, SIZE_Z - 1);
        }
    }

    public record StructureBlock(BlockPos offset, Block block) {
    }

    private record ExpectedBlock(int x, int y, int z, boolean air, Block block) {
    }
}
