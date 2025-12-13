package com.extendedae_plus.content.matrix;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
    import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.init.ModBlockEntities;
import com.glodblock.github.extendedae.common.me.CraftingMatrixThread;
import com.glodblock.github.extendedae.common.me.CraftingThread;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CrafterCorePlusBlockEntity extends TileAssemblerMatrixCrafter {

    public static final int MAX_THREAD = 32;

    // 自己的 32 线程与合并库存，不影响父类原来的 8 线程逻辑
    private final CraftingThread[] plusThreads = new CraftingThread[MAX_THREAD];
    private final InternalInventory plusInternalInv;
    private int plusStates = 0;

    public CrafterCorePlusBlockEntity(BlockPos pos, BlockState blockState) {
        super(pos, blockState);
        // 替换为自己的 32 线程实现
        var inventories = new InternalInventory[MAX_THREAD];
        for (int x = 0; x < MAX_THREAD; x++) {
            final int index = x;
            this.plusThreads[index] = new CraftingMatrixThread(this, this::getSrc, signal -> this.changeState(index, signal));
            inventories[index] = this.plusThreads[index].getInternalInventory();
        }
        this.plusInternalInv = new CombinedInternalInventory(inventories);
    }

    @Override
    public @NotNull BlockEntityType<?> getType() {
        return ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get();
    }

    private IActionSource getSrc() {
        return this.cluster == null ? null : this.cluster.getSrc();
    }

    private void changeState(int index, boolean state) {
        boolean oldState = this.plusStates > 0;
        if (state) {
            this.plusStates |= (1 << index);
        } else {
            this.plusStates &= ~(1 << index);
        }
        if (state) {
            if (!oldState) {
                this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
            }
        } else if (oldState && this.plusStates <= 0) {
            this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().sleepDevice(node));
        }
    }

    @Override
    public int usedThread() {
        int cnt = 0;
        for (var t : this.plusThreads) {
            if (t.getCurrentPattern() != null) {
                cnt++;
            } else if (!t.getInternalInventory().isEmpty()) {
                cnt++;
            }
        }
        return cnt;
    }

    @Override
    public boolean pushJob(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        for (int i = 0; i < this.plusThreads.length; i++) {
            var thread = this.plusThreads[i];
            
            if (thread.acceptJob(patternDetails, inputHolder, Direction.DOWN)) {
                this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
                
                if (this.cluster != null) {
                    this.cluster.updateCrafter(this);
                }
                return true;
            }
        }
        return false;
    }

    public void stop() {
        for (var thread : this.plusThreads) {
            thread.stop();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        for (int x = 0; x < MAX_THREAD; x++) {
            var tag = this.plusThreads[x].writeNBT(registries);
            data.put("#ct" + x, tag);
        }
        final CompoundTag opt = new CompoundTag();
        for (int x = 0; x < this.plusInternalInv.size(); x++) {
            var is = this.plusInternalInv.getStackInSlot(x);
            opt.put("item" + x, is.saveOptional(registries));
        }
        data.put("inv", opt);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        for (int x = 0; x < MAX_THREAD; x++) {
            if (data.contains("#ct" + x)) {
                this.plusThreads[x].readNBT(data.getCompound("#ct" + x), registries);
            }
        }
        var opt = data.getCompound("inv");
        for (int x = 0; x < this.plusInternalInv.size(); x++) {
            var item = opt.getCompound("item" + x);
            this.plusInternalInv.setItemDirect(x, ItemStack.parseOptional(registries, item));
        }
    }

    @Override
    public void add(ClusterAssemblerMatrix cluster) {
        // 保持父类期望的类型：集群内部还是按 TileAssemblerMatrixCrafter 来管理
        cluster.addCrafter(this);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        var isAwake = false;
        for (var t : this.plusThreads) {
            t.recalculatePlan();
            t.updateSleepiness();
            isAwake |= t.isAwake();
        }
        return new TickingRequest(1, 1, !isAwake);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.cluster == null) {
            return TickRateModulation.SLEEP;
        }
        var rate = TickRateModulation.SLEEP;
        for (int i = 0; i < this.plusThreads.length; i++) {
            var t = this.plusThreads[i];
            if (t.isAwake()) {
                var tr = t.tick(this.cluster.getSpeedCore(), ticksSinceLastCall);
                if (tr.ordinal() > rate.ordinal()) {
                    rate = tr;
                }
            }
        }
        this.cluster.updateCrafter(this);
        return rate;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        for (var t : this.plusThreads) {
            if (inv == t.getInternalInventory()) {
                t.recalculatePlan();
                break;
            }
        }
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.saveChangedInventory(inv);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var stack : this.plusInternalInv) {
            var genericStack = GenericStack.unwrapItemStack(stack);
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
        this.plusInternalInv.clear();
    }

}
