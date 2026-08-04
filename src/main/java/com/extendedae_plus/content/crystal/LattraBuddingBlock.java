package com.extendedae_plus.content.crystal;

import com.extendedae_plus.init.ModBlocks;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class LattraBuddingBlock extends Block {
    private static final int GROWTH_CHANCE = 3;
    private static final int DECAY_CHANCE = 10;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final Block degradedBlock;

    public LattraBuddingBlock(Properties properties, Block degradedBlock) {
        super(properties);
        this.degradedBlock = degradedBlock;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) != 0) {
            return;
        }

        Direction direction = Util.getRandom(DIRECTIONS, random);
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        Block nextGrowthStage = getNextGrowthStage(targetState, direction);
        if (nextGrowthStage == null) {
            return;
        }

        // 生长阶段保留朝向与含水状态，确保晶体可附着在任意表面。
        level.setBlockAndUpdate(targetPos, nextGrowthStage.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, targetState.getFluidState().getType() == Fluids.WATER));

        // 无暇母岩生长晶簇后保持品质，其余阶段仍可能退化。
        if (state.getBlock() != ModBlocks.LATTRA_BUDDING_FULLY.get()
                && random.nextInt(DECAY_CHANCE) == 0) {
            level.setBlockAndUpdate(pos, degradedBlock.defaultBlockState());
        }
    }

    private static Block getNextGrowthStage(BlockState targetState, Direction direction) {
        if (canGrowInto(targetState)) {
            return ModBlocks.LATTRA_CRYSTAL_BUD_SMALL.get();
        }

        Block targetBlock = targetState.getBlock();
        if (!isLattraCrystalGrowthStage(targetBlock)
                || targetState.getValue(AmethystClusterBlock.FACING) != direction) {
            return null;
        }
        if (targetBlock == ModBlocks.LATTRA_CRYSTAL_BUD_SMALL.get()) {
            return ModBlocks.LATTRA_CRYSTAL_BUD_MEDIUM.get();
        }
        if (targetBlock == ModBlocks.LATTRA_CRYSTAL_BUD_MEDIUM.get()) {
            return ModBlocks.LATTRA_CRYSTAL_BUD_LARGE.get();
        }
        if (targetBlock == ModBlocks.LATTRA_CRYSTAL_BUD_LARGE.get()) {
            return ModBlocks.LATTRA_CRYSTAL_CLUSTER.get();
        }
        return null;
    }

    private static boolean isLattraCrystalGrowthStage(Block block) {
        return block == ModBlocks.LATTRA_CRYSTAL_BUD_SMALL.get()
                || block == ModBlocks.LATTRA_CRYSTAL_BUD_MEDIUM.get()
                || block == ModBlocks.LATTRA_CRYSTAL_BUD_LARGE.get();
    }

    private static boolean canGrowInto(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
