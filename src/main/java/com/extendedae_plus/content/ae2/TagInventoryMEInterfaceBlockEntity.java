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
import appeng.util.prioritylist.IPartitionList;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.menu.TagInventoryMEInterfaceMenu;
import com.glodblock.github.extendedae.common.me.taglist.TagPriorityList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TagInventoryMEInterfaceBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, MenuProvider, IActionHost {

    public static final int MAX_FILTER_LENGTH = 1024;
    private static final String TAG_WHITE = "tagWhite";
    private static final String TAG_BLACK = "tagBlack";

    private final IManagedGridNode managedNode;
    private final ResourceHandler<ItemResource> itemHandler = new TagFilteredItemHandler();

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
        if (this.level != null && !this.level.isClientSide()) {
            GridHelper.onFirstTick(this, be -> be.managedNode.create(be.getLevel(), be.getBlockPos()));
        }
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        data.putString(TAG_WHITE, this.whiteListExpression);
        data.putString(TAG_BLACK, this.blackListExpression);
        this.managedNode.serialize(data);
    }

    @Override
    public void loadAdditional(ValueInput data) {
        super.loadAdditional(data);
        this.whiteListExpression = data.getString(TAG_WHITE).orElse("");
        this.blackListExpression = data.getString(TAG_BLACK).orElse("");
        this.filter = null;
        this.managedNode.deserialize(data);
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

    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return this.itemHandler;
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

    private final class TagFilteredItemHandler implements ResourceHandler<ItemResource> {

        @Override
        public int size() {
            return collectMatchingItems().size();
        }

        @Override
        public ItemResource getResource(int slot) {
            NetworkItem item = this.getNetworkItem(slot);
            if (item == null) {
                return ItemResource.EMPTY;
            }
            return ItemResource.of(item.key().toStack());
        }

        @Override
        public long getAmountAsLong(int slot) {
            NetworkItem item = this.getNetworkItem(slot);
            return item == null ? 0 : item.amount();
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            NetworkItem item = this.getNetworkItem(slot);
            if (item == null) {
                return 0;
            }
            return resource.isEmpty() || resource.matches(item.key().toStack(1))
                    ? item.key().getMaxStackSize()
                    : 0;
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            this.checkSlot(slot);
            return false;
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext transaction) {
            if (amount <= 0 || resource.isEmpty()) {
                return 0;
            }

            NetworkItem item = this.getNetworkItem(slot);
            IGridNode node = managedNode.getNode();
            if (item == null || node == null || !node.isActive()) {
                return 0;
            }
            if (!resource.matches(item.key().toStack(1))) {
                return 0;
            }

            long requested = Math.min(amount, item.key().getMaxStackSize());
            long extracted = node.getGrid().getStorageService().getInventory().extract(
                    item.key(),
                    requested,
                    Actionable.MODULATE,
                    IActionSource.ofMachine(TagInventoryMEInterfaceBlockEntity.this));
            return (int) Math.min(extracted, Integer.MAX_VALUE);
        }

        @Nullable
        private NetworkItem getNetworkItem(int slot) {
            this.checkSlot(slot);
            List<NetworkItem> items = collectMatchingItems();
            return slot < items.size() ? items.get(slot) : null;
        }

        private void checkSlot(int slot) {
            if (slot < 0 || slot >= this.size()) {
                throw new IndexOutOfBoundsException("Slot " + slot + " not in valid range");
            }
        }
    }
}
