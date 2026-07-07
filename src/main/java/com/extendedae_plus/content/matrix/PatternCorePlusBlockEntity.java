package com.extendedae_plus.content.matrix;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import com.extendedae_plus.ExtendedAEPlus;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixPart;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.mixin.extendedae.accessor.TileAssemblerMatrixPatternAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.BlockEntityAccessor;
import com.glodblock.github.extendedae.common.blocks.matrix.BlockAssemblerMatrixBase;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

import appeng.api.crafting.PatternDetailsHelper;

public class PatternCorePlusBlockEntity extends TileAssemblerMatrixPattern implements SuperAssemblerMatrixPart {

    public static final int INV_SIZE = 72;
    public static final int SUPER_PATTERN_CAPACITY = INV_SIZE;

    private @Nullable SuperAssemblerMatrixCluster superMatrixCluster;

    public PatternCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(pos, blockState);

        ((BlockEntityAccessor) (Object) this)
                .extendedae_plus$setType(ModBlockEntities.ASSEMBLER_MATRIX_PATTERN_PLUS_BE.get());

        var inventory = new AppEngInternalInventory(this, INV_SIZE, 1);
        inventory.setFilter(new Filter(this::getLevel));
        ((TileAssemblerMatrixPatternAccessor) (Object) this).extendedae_plus$setPatternInventory(inventory);
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var icon = AEItemKey.of(ModItems.ASSEMBLER_MATRIX_PATTERN_PLUS.get());
        var name = this.hasCustomName() ? this.getCustomName() : icon.getDisplayName();
        return new PatternContainerGroup(
                icon,
                name,
                List.of(Component.translatable("gui.extendedae_plus.assembler_matrix.pattern"))
        );
    }

    @Override
    public @NotNull BlockEntityType<?> getType() {
        return ModBlockEntities.ASSEMBLER_MATRIX_PATTERN_PLUS_BE.get();
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
    public List<IPatternDetails> getAvailablePatterns() {
        if (this.superMatrixCluster != null) {
            return List.of();
        }
        return super.getAvailablePatterns();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (this.superMatrixCluster != null) {
            return this.superMatrixCluster.pushPattern(patternDetails, inputHolder);
        }
        return super.pushPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        if (this.superMatrixCluster != null) {
            return this.superMatrixCluster.isBusy();
        }
        return super.isBusy();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        super.saveChangedInventory(inv);
        if (this.superMatrixCluster != null) {
            this.superMatrixCluster.refreshCraftingProvider();
        }
    }

    public record Filter(Supplier<Level> world) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (stack.getItem() instanceof EncodedPatternItem<?>) {
                return PatternDetailsHelper.decodePattern(stack, world.get())
                        instanceof IMolecularAssemblerSupportedPattern;
            }
            return false;
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
