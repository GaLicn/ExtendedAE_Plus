package com.extendedae_plus.content.matrix;

import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCalculator;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * ExtendedAE_Plus: 装配矩阵上传核心方块实体。
 * 作为矩阵内部功能块，仅用于标记该矩阵允许被自动上传（工具类会在集群中查找此实体）。
 */
public class UploadCoreBlockEntity extends TileAssemblerMatrixFunction implements SuperAssemblerMatrixPart {

    private @Nullable SuperAssemblerMatrixCluster superMatrixCluster;

    public UploadCoreBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType<?>) com.extendedae_plus.init.ModBlockEntities.UPLOAD_CORE_BE.get(), pos, state);
    }

    @Override
    public void add(ClusterAssemblerMatrix c) {
        // 无需修改集群，仅作为存在性标记。
        // 若后续需要限制为“最多一个”，可在 ExtendedAE_Plus 工具类或事件中做校验与提示。
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.level instanceof ServerLevel serverLevel) {
            // 上传核心用于定位终极结构，必须在载入后触发一次完整校验。
            SuperAssemblerMatrixCalculator.scheduleUltimateLoadRecalculate(serverLevel, this.worldPosition);
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
        var wasSuperFormed = this.superMatrixCluster != null;
        this.superMatrixCluster = cluster;
        if (wasSuperFormed != (cluster != null)) {
            // 超级集群切换时重新建立节点连接面，避免上传核心保留未成型状态。
            this.onGridConnectableSidesChanged();
        }
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return this.superMatrixCluster == null
                ? super.getGridConnectableSides(orientation)
                : EnumSet.allOf(Direction.class);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (this.superMatrixCluster != null) {
            // 超级矩阵使用独立集群，不能回落到只识别原版集群的状态更新。
            this.eap$updateSuperMatrixStatus();
        } else {
            super.onMainNodeStateChanged(reason);
        }
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
