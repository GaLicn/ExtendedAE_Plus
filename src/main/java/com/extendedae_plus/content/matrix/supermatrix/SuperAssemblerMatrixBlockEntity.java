package com.extendedae_plus.content.matrix.supermatrix;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.CombinedInternalInventory;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public abstract class SuperAssemblerMatrixBlockEntity extends AENetworkedBlockEntity implements
        ICraftingProvider, IGridTickable, PatternContainer, SuperAssemblerMatrixPart {

    private static final InternalInventory EMPTY_PATTERN_INVENTORY = InternalInventory.empty();

    private boolean core;
    private boolean unloading;
    private @Nullable SuperAssemblerMatrixCluster superCluster;

    protected SuperAssemblerMatrixBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.getMainNode().setIdlePowerUsage(0.5);
        this.getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL);
        this.getMainNode().setVisualRepresentation(ModItems.SUPER_ASSEMBLER_MATRIX_FRAME.get());
        this.getMainNode().addService(ICraftingProvider.class, this);
        this.getMainNode().addService(IGridTickable.class, this);
        this.getMainNode().addService(IGridMultiblock.class, this::getMultiblockNodes);
    }

    public boolean isCore() {
        return this.core;
    }

    public void setCore(boolean core) {
        this.core = core;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModItems.SUPER_ASSEMBLER_MATRIX_FRAME.get();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("superMatrixCore", this.core);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.core = tag.getBoolean("superMatrixCore");
    }

    @Override
    public void onReady() {
        super.onReady();
        this.unloading = false;
        if (this.level instanceof ServerLevel serverLevel) {
            SuperAssemblerMatrixCalculator.recalculate(serverLevel, this.worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        this.unloading = true;
        this.eap$destroySuperMatrixClusterQuietly();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        this.unloading = true;
        this.eap$destroySuperMatrixClusterQuietly();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.unloading = false;
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
        return this.superCluster;
    }

    @Override
    public void eap$setSuperMatrixCluster(@Nullable SuperAssemblerMatrixCluster cluster) {
        var wasFormed = this.superCluster != null;
        this.superCluster = cluster;
        if (wasFormed != (cluster != null)) {
            this.onGridConnectableSidesChanged();
        }
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return this.superCluster == null ? EnumSet.noneOf(Direction.class) : EnumSet.allOf(Direction.class);
    }

    @Override
    public void eap$updateSuperMatrixStatus() {
        if (ExtendedAEPlus.isServerStopping()) {
            return;
        }
        if (this.unloading || this.level == null || this.isRemoved()) {
            return;
        }
        var state = this.level.getBlockState(this.worldPosition);
        if (state.hasProperty(BlockAssemblerMatrixBase.FORMED)
                && state.hasProperty(BlockAssemblerMatrixBase.POWERED)) {
            var formed = this.superCluster != null;
            var powered = formed && this.getMainNode().isActive();
            var newState = state
                    .setValue(BlockAssemblerMatrixBase.FORMED, formed)
                    .setValue(BlockAssemblerMatrixBase.POWERED, powered);
            if (newState != state) {
                this.level.setBlock(this.worldPosition, newState, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return this.core && this.superCluster != null ? this.superCluster.getAvailablePatterns() : List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return this.core && this.superCluster != null && this.superCluster.pushPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return !this.core || this.superCluster == null || this.superCluster.isBusy();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.core || this.superCluster == null) {
            return TickRateModulation.SLEEP;
        }
        return this.superCluster.tick(ticksSinceLastCall) ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    public void wakeCoreNode() {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    public long insertToNetwork(appeng.api.stacks.AEKey key, long amount) {
        var grid = this.getMainNode().getGrid();
        if (grid == null || amount <= 0) {
            return 0;
        }
        var storage = grid.getService(IStorageService.class);
        return storage.getInventory().insert(key, amount, Actionable.MODULATE, appeng.api.networking.security.IActionSource.ofMachine(this));
    }

    @Override
    public @Nullable IGrid getGrid() {
        return this.getMainNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        if (!this.core || this.superCluster == null) {
            return EMPTY_PATTERN_INVENTORY;
        }
        var inventories = this.superCluster.getPatternInventories();
        return inventories.length == 0 ? EMPTY_PATTERN_INVENTORY : new CombinedInternalInventory(inventories);
    }

    @Override
    public boolean isVisibleInTerminal() {
        return this.core && this.superCluster != null;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var icon = AEItemKey.of(ModItems.SUPER_ASSEMBLER_MATRIX_FRAME.get());
        return new PatternContainerGroup(
                icon,
                Component.translatable("block.extendedae_plus.super_assembler_matrix_frame"),
                List.of(Component.translatable("gui.extendedae_plus.super_assembler_matrix.pattern"))
        );
    }

    public void refreshCraftingProvider() {
        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    private java.util.Iterator<IGridNode> getMultiblockNodes() {
        return this.unloading || this.superCluster == null
                ? java.util.Collections.emptyIterator()
                : this.superCluster.getGridNodes();
    }

    @Override
    public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State reason) {
        this.eap$updateSuperMatrixStatus();
    }
}
