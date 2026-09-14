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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        var blockEntity = (BlockEntity) (Object) this;
        var glass = (TileAssemblerMatrixGlass) (Object) this;
        if (changed && !blockEntity.isRemoved() && glass.getMainNode().isReady()) {
            // 仅对存活节点刷新连接面，避免区块卸载后修改已销毁节点。
            ((AENetworkBlockEntityInvoker) (Object) this).eap$refreshGridConnectableSides();
        }
    }

    @Inject(method = "onChunkUnloaded", at = @At("HEAD"), remap = false)
    private void eap$detachSuperMatrixBeforeChunkUnload(CallbackInfo ci) {
        // AE2 销毁网络节点前先解除集群，避免集群保留失效玻璃。
        this.eap$destroySuperMatrixClusterQuietly();
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), remap = false)
    private void eap$detachSuperMatrixBeforeRemoval(CallbackInfo ci) {
        this.eap$destroySuperMatrixClusterQuietly();
    }

    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        var glass = (TileAssemblerMatrixGlass) (Object) this;
        // 未加入超级矩阵时，保留 ExtendedAE 原版矩阵的联网面判断。
        return this.eap$superMatrixCluster != null || glass.isFormed()
                ? EnumSet.allOf(Direction.class)
                : EnumSet.noneOf(Direction.class);
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
            // 超级矩阵未接管玻璃时，回退到原版矩阵的 cluster 状态。
            var formed = this.eap$superMatrixCluster != null || glass.isFormed();
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
        if (this.eap$superMatrixCluster != null) {
            this.eap$updateSuperMatrixStatus();
        } else if (reason != IGridNodeListener.State.GRID_BOOT) {
            // 普通装配矩阵沿用 ExtendedAE 原版节点状态更新流程。
            ((TileAssemblerMatrixGlass) (Object) this).updateSubType(false);
        }
    }

}
