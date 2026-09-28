package com.extendedae_plus.client.model;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixFrameBlock;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MatrixFrameBakedModel implements DynamicBlockStateModel {
    private final BlockStateModel blockOff;
    private final BlockStateModel blockOn;
    private final BlockStateModel columnOff;
    private final BlockStateModel columnOn;

    public MatrixFrameBakedModel(BlockStateModel blockOff, BlockStateModel blockOn,
            BlockStateModel columnOff, BlockStateModel columnOn) {
        this.blockOff = blockOff;
        this.blockOn = blockOn;
        this.columnOff = columnOff;
        this.columnOn = columnOn;
    }

    @Override
    public void collectParts(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
            @NotNull RandomSource random, @NotNull List<BlockStateModelPart> parts) {
        boolean column = state.getValue(SuperAssemblerMatrixFrameBlock.SHAPE)
                != SuperAssemblerMatrixFrameBlock.Shape.block;
        boolean powered = state.getValue(BlockAssemblerMatrixBase.POWERED);
        (column ? (powered ? columnOn : columnOff) : (powered ? blockOn : blockOff))
                .collectParts(random, parts);
    }

    @Override
    public @NotNull Material.Baked particleMaterial() {
        return blockOff.particleMaterial();
    }

    @Override
    public @BakedQuad.MaterialFlags int materialFlags() {
        return blockOff.materialFlags();
    }
}
