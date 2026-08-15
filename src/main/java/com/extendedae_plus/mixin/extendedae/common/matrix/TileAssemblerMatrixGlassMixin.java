package com.extendedae_plus.mixin.extendedae.common.matrix;

import appeng.api.orientation.BlockOrientation;
import appeng.api.networking.IGridNodeListener;
import com.extendedae_plus.mixin.ae2.accessor.AENetworkBlockEntityInvoker;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixGlass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.EnumSet;
import java.util.Set;

/** 让原版矩阵玻璃作为超级装配矩阵的完整多方块部件。 */
@Mixin(value = TileAssemblerMatrixGlass.class, remap = false)
public abstract class TileAssemblerMatrixGlassMixin implements SuperAssemblerMatrixPart {

    @Unique
    private @Nullable SuperAssemblerMatrixCluster eap$superMatrixCluster;

    @Override
    public BlockPos eap$getSuperMatrixPos() {
        return ((BlockEntity) (Object) this).getBlockPos();
    }

    @Override
    public @Nullable Level eap$getSuperMatrixLevel() {
        return ((BlockEntity) (Object) this).getLevel();
    }

    @Override
    public @Nullable SuperAssemblerMatrixCluster eap$getSuperMatrixCluster() {
        return this.eap$superMatrixCluster;
    }

    @Override
    public void eap$setSuperMatrixCluster(@Nullable SuperAssemblerMatrixCluster cluster) {
        var changed = this.eap$superMatrixCluster != cluster;
        this.eap$superMatrixCluster = cluster;
        if (changed) {
            // 成型变化后重建玻璃节点的 AE 连接面。
            ((AENetworkBlockEntityInvoker) (Object) this).eap$refreshGridConnectableSides();
        }
    }

    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return this.eap$superMatrixCluster == null
                ? EnumSet.noneOf(Direction.class)
                : EnumSet.allOf(Direction.class);
    }

    @Override
    public void eap$updateSuperMatrixStatus() {
        if (ExtendedAEPlus.isServerStopping()) {
            return;
        }
        var blockEntity = (BlockEntity) (Object) this;
        var level = blockEntity.getLevel();
        if (level == null || blockEntity.isRemoved()) {
            return;
        }
        if (level.isClientSide) {
            return;
        }
        var state = level.getBlockState(blockEntity.getBlockPos());
        if (state.hasProperty(BlockAssemblerMatrixBase.FORMED)
                && state.hasProperty(BlockAssemblerMatrixBase.POWERED)) {
            var glass = (TileAssemblerMatrixGlass) (Object) this;
            var formed = this.eap$superMatrixCluster != null;
            var newState = state
                    .setValue(BlockAssemblerMatrixBase.FORMED, formed)
                    .setValue(BlockAssemblerMatrixBase.POWERED, formed && glass.getMainNode().isActive());
            if (newState != state) {
                level.setBlock(blockEntity.getBlockPos(), newState, Block.UPDATE_CLIENTS);
            }
        }
    }

    // 覆盖原版回调，避免其按原版集群为空将超级结构玻璃重置为未成型。
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.eap$updateSuperMatrixStatus();
    }

}
