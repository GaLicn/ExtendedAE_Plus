package com.extendedae_plus.content.decor;

import com.extendedae_plus.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DollBlockEntity extends BlockEntity {
    public static final int BOUNCE_DURATION_TICKS = 10;

    private long serverBounceStartedAt = Long.MIN_VALUE;
    private long clientBounceStartedAt = Long.MIN_VALUE;
    private int lastClientBounceId = -1;

    public DollBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOLL_BOUNCE_BE.get(), pos, state);
    }

    public void startBounce(Level level) {
        if (level.isClientSide) return;

        BlockState state = getBlockState();
        if (state.getValue(DollBlock.BOUNCING)) return;

        serverBounceStartedAt = level.getGameTime();
        int nextBounceId = (state.getValue(DollBlock.BOUNCE_ID) + 1) % 16;
        level.setBlock(worldPosition, state
                .setValue(DollBlock.BOUNCING, true)
                .setValue(DollBlock.BOUNCE_ID, nextBounceId), Block.UPDATE_CLIENTS);
    }

    public float getSquash(float partialTick) {
        Level level = getLevel();
        if (level == null || clientBounceStartedAt == Long.MIN_VALUE) return 0;

        double elapsed = level.getGameTime() - clientBounceStartedAt + partialTick;
        if (elapsed < 0 || elapsed >= BOUNCE_DURATION_TICKS) return 0;

        double beat = elapsed / BOUNCE_DURATION_TICKS;
        return (float) ((1 - Math.cos(beat * Math.PI * 2)) * 0.5);
    }

    public static void clientTick(Level level, BlockState state, DollBlockEntity entity) {
        int bounceId = state.getValue(DollBlock.BOUNCE_ID);
        if (bounceId != entity.lastClientBounceId) {
            entity.lastClientBounceId = bounceId;
            entity.clientBounceStartedAt = level.getGameTime();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DollBlockEntity entity) {
        if (!state.getValue(DollBlock.BOUNCING)) {
            entity.serverBounceStartedAt = Long.MIN_VALUE;
            return;
        }

        if (entity.serverBounceStartedAt == Long.MIN_VALUE
                || level.getGameTime() - entity.serverBounceStartedAt >= BOUNCE_DURATION_TICKS) {
            level.setBlock(pos, state.setValue(DollBlock.BOUNCING, false), Block.UPDATE_CLIENTS);
            entity.serverBounceStartedAt = Long.MIN_VALUE;
        }
    }
}
