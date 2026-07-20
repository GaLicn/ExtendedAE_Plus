package com.extendedae_plus.content.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.util.prioritylist.IPartitionList;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.menu.TagInventoryMEInterfaceMenu;
import com.glodblock.github.extendedae.common.me.taglist.TagPriorityList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TagInventoryMEInterfaceBlockEntity extends AEBaseBlockEntity
        implements IInWorldGridNodeHost, MenuProvider, IActionHost {

    public static final int MAX_FILTER_LENGTH = 1024;
    private static final String TAG_WHITE = "tagWhite";
    private static final String TAG_BLACK = "tagBlack";

    private final IManagedGridNode managedNode;
    private final IItemHandler itemHandler = new TagFilteredItemHandler();
    private final LazyOptional<IItemHandler> itemHandlerCapability = LazyOptional.of(() -> this.itemHandler);

    private String whiteListExpression = "";
    private String blackListExpression = "";
    @Nullable
    private IPartitionList filter;

    public TagInventoryMEInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TAG_INVENTORY_ME_INTERFACE_BE.get(), pos, state);
        this.managedNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE);
        this.managedNode.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.managedNode.setIdlePowerUsage(1.0);
        this.managedNode.setInWorldNode(true);
        this.managedNode.setExposedOnSides(EnumSet.allOf(Direction.class));
        this.managedNode.setTagName("tag_inventory_me_interface");
        this.managedNode.setVisualRepresentation(ModItems.TAG_INVENTORY_ME_INTERFACE.get().getDefaultInstance());
    }

    @Override
    public @Nullable IGridNode getGridNode(@Nullable Direction dir) {
        return this.managedNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.GLASS;
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return this.managedNode.getNode();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            GridHelper.onFirstTick(this, be -> be.managedNode.create(be.getLevel(), be.getBlockPos()));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(TAG_WHITE, this.whiteListExpression);
        tag.putString(TAG_BLACK, this.blackListExpression);
        this.managedNode.saveToNBT(tag);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        this.whiteListExpression = tag.getString(TAG_WHITE);
        this.blackListExpression = tag.getString(TAG_BLACK);
        this.filter = null;
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
        this.itemHandlerCapability.invalidate();
    }

    public String getWhiteListExpression() {
        return this.whiteListExpression;
    }

    public String getBlackListExpression() {
        return this.blackListExpression;
    }

    public void setTagFilters(String whiteListExpression, String blackListExpression) {
        String nextWhite = trimFilter(whiteListExpression);
        String nextBlack = trimFilter(blackListExpression);
        if (this.whiteListExpression.equals(nextWhite) && this.blackListExpression.equals(nextBlack)) {
            return;
        }

        this.whiteListExpression = nextWhite;
        this.blackListExpression = nextBlack;
        this.filter = null;
        this.setChanged();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return this.itemHandlerCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.extendedae_plus.tag_inventory_me_interface");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TagInventoryMEInterfaceMenu(id, inventory, this.worldPosition,
                this.whiteListExpression, this.blackListExpression);
    }

    private IPartitionList getFilter() {
        if (this.filter == null) {
            this.filter = new TagPriorityList(this.whiteListExpression, this.blackListExpression);
        }
        return this.filter;
    }

    private List<NetworkItem> collectMatchingItems() {
        IGridNode node = this.managedNode.getNode();
        if (node == null || !node.isActive()) {
            return List.of();
        }

        IPartitionList currentFilter = this.getFilter();
        if (currentFilter.isEmpty()) {
            return List.of();
        }

        var storage = node.getGrid().getStorageService().getCachedInventory();
        var result = new ArrayList<NetworkItem>();
        for (Object2LongMap.Entry<AEKey> entry : storage) {
            AEKey key = entry.getKey();
            if (key instanceof AEItemKey itemKey && entry.getLongValue() > 0 && currentFilter.isListed(key)) {
                result.add(new NetworkItem(itemKey, entry.getLongValue()));
            }
        }
        return result;
    }

    private static String trimFilter(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_FILTER_LENGTH ? value.substring(0, MAX_FILTER_LENGTH) : value;
    }

    private record NetworkItem(AEItemKey key, long amount) {
    }

    enum NodeListener implements IGridNodeListener<TagInventoryMEInterfaceBlockEntity> {
        INSTANCE;

        @Override
        public void onSaveChanges(TagInventoryMEInterfaceBlockEntity host, IGridNode node) {
            host.setChanged();
        }
    }

    private final class TagFilteredItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return collectMatchingItems().size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            NetworkItem item = this.getNetworkItem(slot);
            if (item == null) {
                return ItemStack.EMPTY;
            }

            int displayAmount = (int) Math.min(item.amount(), item.key().getMaxStackSize());
            return item.key().toStack(displayAmount);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }

            NetworkItem item = this.getNetworkItem(slot);
            IGridNode node = managedNode.getNode();
            if (item == null || node == null || !node.isActive()) {
                return ItemStack.EMPTY;
            }

            // 通过机器行动源从 ME 网络提取，确保权限与能耗正常生效。
            long requested = Math.min(amount, item.key().getMaxStackSize());
            long extracted = node.getGrid().getStorageService().getInventory().extract(
                    item.key(),
                    requested,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                    IActionSource.ofMachine(TagInventoryMEInterfaceBlockEntity.this));
            return extracted > 0 ? item.key().toStack((int) extracted) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            NetworkItem item = this.getNetworkItem(slot);
            return item == null ? 64 : item.key().getMaxStackSize();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }

        @Nullable
        private NetworkItem getNetworkItem(int slot) {
            if (slot < 0) {
                return null;
            }
            List<NetworkItem> items = collectMatchingItems();
            return slot < items.size() ? items.get(slot) : null;
        }
    }
}
