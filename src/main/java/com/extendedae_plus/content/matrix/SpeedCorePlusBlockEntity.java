package com.extendedae_plus.content.matrix;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.extendedae_plus.init.ModBlockEntities;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SpeedCorePlusBlockEntity extends TileAssemblerMatrixFunction implements SuperAssemblerMatrixPart {

    private @Nullable SuperAssemblerMatrixCluster superMatrixCluster;

    public SpeedCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ASSEMBLER_MATRIX_SPEED_PLUS_BE.get(), pos, blockState);
    }

    @Override
    public void add(ClusterAssemblerMatrix cluster) {
        if (cluster == null) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            cluster.addSpeedCore();
        }
    }

    @Override
    public void onChunkUnloaded() {
        this.eap$destroySuperMatrixClusterQuietly();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        this.eap$destroySuperMatrixClusterQuietly();
        super.setRemoved();
    }

    @Override
    public BlockPos eap$getSuperMatrixPos() {
        return this.worldPosition;
    }

    @Override
    public @Nullable Level eap$getSuperMatrixLevel() {
        return this.level;
    }

    @Override
    public @Nullable SuperAssemblerMatrixCluster eap$getSuperMatrixCluster() {
        return this.superMatrixCluster;
    }

    @Override
    public void eap$setSuperMatrixCluster(@Nullable SuperAssemblerMatrixCluster cluster) {
        this.superMatrixCluster = cluster;
    }

    @Override
    public void eap$updateSuperMatrixStatus() {
        if (ExtendedAEPlus.isServerStopping() || this.level == null || this.isRemoved()) {
            return;
        }
        var state = this.level.getBlockState(this.worldPosition);
        if (state.hasProperty(BlockAssemblerMatrixBase.FORMED)
                && state.hasProperty(BlockAssemblerMatrixBase.POWERED)) {
            var formed = this.superMatrixCluster != null || this.isFormed();
            var powered = formed && this.getMainNode().isActive();
            var newState = state
                    .setValue(BlockAssemblerMatrixBase.FORMED, formed)
                    .setValue(BlockAssemblerMatrixBase.POWERED, powered);
            if (newState != state) {
                this.level.setBlock(this.worldPosition, newState, Block.UPDATE_CLIENTS);
            }
        }
    }
}
