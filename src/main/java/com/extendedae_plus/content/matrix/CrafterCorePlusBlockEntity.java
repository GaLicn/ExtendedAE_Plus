package com.extendedae_plus.content.matrix;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.util.inv.CombinedInternalInventory;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.mixin.extendedae.accessor.TileAssemblerMatrixCrafterAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.BlockEntityAccessor;
import com.glodblock.github.extendedae.common.me.CraftingMatrixThread;
import com.glodblock.github.extendedae.common.me.CraftingThread;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class CrafterCorePlusBlockEntity extends TileAssemblerMatrixCrafter {

    public static final int MAX_THREAD = 32;

    private int activeThreadMask = 0;

    public CrafterCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(pos, blockState);

        ((BlockEntityAccessor) (Object) this)
                .extendedae_plus$setType(ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get());

        var threads = new CraftingThread[MAX_THREAD];
        var inventories = new InternalInventory[MAX_THREAD];
        for (int x = 0; x < MAX_THREAD; x++) {
            final int index = x;
            threads[index] = new CraftingMatrixThread(this, this::getSrc, signal -> this.changeState(index, signal));
            inventories[index] = threads[index].getInternalInventory();
        }

        var accessor = (TileAssemblerMatrixCrafterAccessor) (Object) this;
        accessor.extendedae_plus$setThreads(threads);
        accessor.extendedae_plus$setInternalInv(new CombinedInternalInventory(inventories));
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        var threads = ((TileAssemblerMatrixCrafterAccessor) (Object) this).extendedae_plus$getThreads();
        for (int x = TileAssemblerMatrixCrafter.MAX_THREAD; x < MAX_THREAD; x++) {
            tag.put("#ct" + x, threads[x].writeNBT(registries));
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);

        var threads = ((TileAssemblerMatrixCrafterAccessor) (Object) this).extendedae_plus$getThreads();
        for (int x = TileAssemblerMatrixCrafter.MAX_THREAD; x < MAX_THREAD; x++) {
            if (tag.contains("#ct" + x)) {
                threads[x].readNBT(tag.getCompound("#ct" + x), registries);
            }
        }
    }

    @Override
    public @NotNull BlockEntityType<?> getType() {
        return ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get();
    }

    private IActionSource getSrc() {
        return this.cluster == null ? null : this.cluster.getSrc();
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
}
