package com.extendedae_plus.content.controller;

import appeng.api.networking.*;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.menu.NetworkPatternControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class NetworkPatternControllerBlockEntity extends BlockEntity implements IInWorldGridNodeHost, MenuProvider {

    private final IManagedGridNode managedNode;

    public NetworkPatternControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_PATTERN_CONTROLLER_BE.get(), pos, state);
        this.managedNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE);
        this.managedNode.setIdlePowerUsage(1.0);
        this.managedNode.setInWorldNode(true);
        this.managedNode.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.managedNode.setTagName("network_pattern_controller");
    }

    @Override
    public @Nullable IGridNode getGridNode(@Nullable Direction dir) {
        return managedNode == null ? null : managedNode.getNode();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            GridHelper.onFirstTick(this, be -> be.managedNode.create(be.getLevel(), be.getBlockPos()));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.managedNode.saveToNBT(tag);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.managedNode.loadFromNBT(tag);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.managedNode.destroy();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.managedNode.destroy();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.extendedae_plus.network_pattern_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new NetworkPatternControllerMenu(id, inv, this.worldPosition);
    }

    enum NodeListener implements IGridNodeListener<NetworkPatternControllerBlockEntity> {
        INSTANCE;
        @Override
        public void onSaveChanges(NetworkPatternControllerBlockEntity host, IGridNode node) {
            host.setChanged();
        }
    }
}
