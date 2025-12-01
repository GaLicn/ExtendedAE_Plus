package com.extendedae_plus.content.matrix;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.util.inv.CombinedInternalInventory;
import com.extendedae_plus.init.ModBlockEntities;
import com.glodblock.github.extendedae.common.me.CraftingThread;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CrafterCorePlusBlockEntity extends TileAssemblerMatrixCrafter {

    public static final int MAX_THREAD = 32;

    private final CraftingThread[] threads = new CraftingThread[MAX_THREAD];
    private final InternalInventory internalInv;
    private final BlockEntityType<?> overriddenType;

    public CrafterCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(pos, blockState);
        this.overriddenType = ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get();

        InternalInventory[] inventories = new InternalInventory[MAX_THREAD];
        for (int i = 0; i < MAX_THREAD; i++) {
            this.threads[i] = new CraftingThread(this);
            this.threads[i].setPusher(this::pushResult);
            inventories[i] = this.threads[i].getInternalInventory();
        }
        this.internalInv = new CombinedInternalInventory(inventories);
    }

    public int usedThread() {
        int count = 0;
        for (CraftingThread thread : this.threads) {
            if (!thread.getInternalInventory().isEmpty()) {
                count++;
            }
        }
        double scale = (double) TileAssemblerMatrixCrafter.MAX_THREAD / MAX_THREAD;
        int reported = (int) Math.ceil(count * scale);
        return Math.min(TileAssemblerMatrixCrafter.MAX_THREAD, reported);
    }

    public boolean pushJob(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        for (CraftingThread thread : this.threads) {
            if (thread.acceptJob(patternDetails, inputHolder, Direction.DOWN)) {
                if (this.cluster != null) {
                    this.cluster.updateCrafter(this);
                }
                return true;
            }
        }
        return false;
    }

    public void stop() {
        for (CraftingThread thread : this.threads) {
            thread.stop();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (int i = 0; i < MAX_THREAD; i++) {
            tag.put("#ct" + i, this.threads[i].writeNBT());
        }
        CompoundTag invTag = new CompoundTag();
        for (int i = 0; i < this.internalInv.size(); i++) {
            invTag.put("item" + i, this.internalInv.getStackInSlot(i).save(new CompoundTag()));
        }
        tag.put("inv", invTag);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        for (int i = 0; i < MAX_THREAD; i++) {
            if (tag.contains("#ct" + i)) {
                this.threads[i].readNBT(tag.getCompound("#ct" + i));
            }
        }
        CompoundTag invTag = tag.getCompound("inv");
        for (int i = 0; i < this.internalInv.size(); i++) {
            this.internalInv.setItemDirect(i, ItemStack.of(invTag.getCompound("item" + i)));
        }
    }

    public ItemStack pushResult(ItemStack stack, Direction direction) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        var grid = this.getMainNode().getGrid();
        if (grid != null) {
            IStorageService storage = grid.getService(IStorageService.class);
            if (storage != null) {
                long inserted = storage.getInventory().insert(
                        AEItemKey.of(stack),
                        stack.getCount(),
                        Actionable.MODULATE,
                        this.cluster == null ? null : this.cluster.getSrc()
                );
                if (inserted == 0) {
                    return stack;
                }
                this.saveChanges();
                if (inserted != stack.getCount()) {
                    return stack.copyWithCount((int) (stack.getCount() - inserted));
                }
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    @Override
    public void add(ClusterAssemblerMatrix cluster) {
        cluster.addCrafter(this);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        boolean isAwake = false;
        for (CraftingThread thread : this.threads) {
            thread.recalculatePlan();
            thread.updateSleepiness();
            isAwake |= thread.isAwake();
        }
        if (isAwake) {
            for (CraftingThread thread : this.threads) {
                thread.forceAwake();
            }
        }
        return new TickingRequest(1, 1, !isAwake, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.cluster == null) {
            return TickRateModulation.SLEEP;
        }

        TickRateModulation rate = TickRateModulation.SLEEP;
        for (CraftingThread thread : this.threads) {
            if (thread.isAwake()) {
                TickRateModulation threadRate = thread.tick(this.cluster.getSpeedCore(), ticksSinceLastCall);
                if (threadRate.ordinal() > rate.ordinal()) {
                    rate = threadRate;
                }
            }
        }
        this.cluster.updateCrafter(this);
        return rate;
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        for (CraftingThread thread : this.threads) {
            if (inv == thread.getInternalInventory()) {
                thread.recalculatePlan();
                break;
            }
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.internalInv) {
            GenericStack genericStack = GenericStack.unwrapItemStack(stack);
            if (genericStack != null) {
                genericStack.what().addDrops(genericStack.amount(), drops, level, pos);
            } else {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.internalInv.clear();
    }

    @Override
    public BlockEntityType<?> getType() {
        return this.overriddenType;
    }
}
