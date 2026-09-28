package com.extendedae_plus.content.matrix;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.extendedae_plus.mixin.extendedae.accessor.TileAssemblerMatrixCrafterAccessor;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.me.CraftingMatrixThread;
import com.glodblock.github.extendedae.common.me.CraftingThread;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CrafterCorePlusBlockEntity extends TileAssemblerMatrixCrafter implements SuperAssemblerMatrixPart {

    public static final int MAX_THREAD = 32;
    public static final int SUPER_PARALLEL = 512;

    private int activeThreadMask = 0;
    private @Nullable SuperAssemblerMatrixCluster superMatrixCluster;

    public CrafterCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get(), pos, blockState);

        var threads = new CraftingThread[MAX_THREAD];
        var inventories = new InternalInventory[MAX_THREAD];
        for (int x = 0; x < MAX_THREAD; x++) {
            final int index = x;
            threads[index] = new CraftingMatrixThread(this, signal -> this.changeState(index, signal));
            inventories[index] = threads[index].getInternalInventory();
        }

        var accessor = (TileAssemblerMatrixCrafterAccessor) (Object) this;
        accessor.extendedae_plus$setThreads(threads);
        accessor.extendedae_plus$setInternalInv(new CombinedInternalInventory(inventories));
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);

        var threads = ((TileAssemblerMatrixCrafterAccessor) (Object) this).extendedae_plus$getThreads();
        for (int x = TileAssemblerMatrixCrafter.MAX_THREAD; x < MAX_THREAD; x++) {
            threads[x].writeNBT(data.child("#ct" + x));
        }
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);

        var threads = ((TileAssemblerMatrixCrafterAccessor) (Object) this).extendedae_plus$getThreads();
        for (int x = TileAssemblerMatrixCrafter.MAX_THREAD; x < MAX_THREAD; x++) {
            data.child("#ct" + x).ifPresent(threads[x]::readNBT);
        }
    }

    @Override
    public @NotNull BlockEntityType<?> getType() {
        return ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get();
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

    private void changeState(int index, boolean state) {
        int oldMask = this.activeThreadMask;
        if (state) {
            this.activeThreadMask |= (1 << index);
        } else {
            this.activeThreadMask &= ~(1 << index);
        }

        if (oldMask == 0 && this.activeThreadMask != 0) {
            this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        } else if (oldMask != 0 && this.activeThreadMask == 0) {
            this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().sleepDevice(node));
        }
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
        if (ExtendedAEPlus.isServerStopping()) {
            return;
        }
        if (this.level == null || this.isRemoved()) {
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
