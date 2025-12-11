package com.extendedae_plus.content.wireless;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import com.extendedae_plus.ae.wireless.IWirelessEndpoint;
import com.extendedae_plus.ae.wireless.LabelLink;
import com.extendedae_plus.ae.wireless.LabelNetworkRegistry;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * 标签无线收发器方块实体：
 * - 无主从区分，通过标签映射频道并连接至虚拟节点；
 * - 无 UI（占位），仅提供服务端逻辑与状态更新；
 * - 保留频率字段用于状态显示。
 */
public class LabeledWirelessTransceiverBlockEntity extends AEBaseBlockEntity implements IWirelessEndpoint, IInWorldGridNodeHost {

    private IManagedGridNode managedNode;

    private long frequency = 0L;
    @Nullable
    private String labelForDisplay;
    private boolean locked = false;
    private boolean beingRemoved = false;

    @Nullable
    private UUID placerId;
    @Nullable
    private String placerName;

    private final LabelLink labelLink = new LabelLink(this);

    public LabeledWirelessTransceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LABELED_WIRELESS_TRANSCEIVER_BE.get(), pos, state);
        this.managedNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
                .setFlags(GridFlags.DENSE_CAPACITY);
        this.managedNode.setIdlePowerUsage(1.0);
        this.managedNode.setTagName("labeled_wireless_node");
        this.managedNode.setInWorldNode(true);
        this.managedNode.setExposedOnSides(EnumSet.allOf(Direction.class));
        this.managedNode.setVisualRepresentation(ModItems.LABELED_WIRELESS_TRANSCEIVER.get().getDefaultInstance());
    }

    /* ===================== IInWorldGridNodeHost ===================== */
    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return getGridNode();
    }

    /* ===================== IWirelessEndpoint ===================== */
    @Override
    public ServerLevel getServerLevel() {
        Level lvl = super.getLevel();
        return lvl instanceof ServerLevel sl ? sl : null;
    }

    @Override
    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    @Override
    public IGridNode getGridNode() {
        return managedNode == null ? null : managedNode.getNode();
    }

    @Override
    public boolean isEndpointRemoved() {
        return super.isRemoved();
    }

    /* ===================== 公共方法 ===================== */

    public void setPlacerId(@Nullable UUID placerId, @Nullable String placerName) {
        this.placerId = placerId;
        this.placerName = placerName;
        setChanged();
    }

    @Nullable
    public UUID getPlacerId() {
        return placerId;
    }

    @Nullable
    public String getPlacerName() {
        return placerName;
    }

    public long getFrequency() {
        return frequency;
    }

    @Nullable
    public String getLabelForDisplay() {
        return labelForDisplay;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (this.locked == locked) return;
        this.locked = locked;
        setChanged();
    }

    /**
     * 应用/切换标签。空或非法标签将清空并断开。
     */
    public void applyLabel(@Nullable String rawLabel) {
        ServerLevel sl = getServerLevel();
        if (sl == null) return;

        // 先注销旧网络引用
        LabelNetworkRegistry.get(sl).unregister(this);

        var network = LabelNetworkRegistry.get(sl).register(sl, rawLabel, placerId, this);
        if (network == null) {
            clearLabel();
            return;
        }

        this.labelForDisplay = rawLabel;
        this.frequency = network.channel();
        this.labelLink.setTarget(network);
        updateState();
        setChanged();
    }

    /**
     * 清空标签并断开连接。
     */
    public void clearLabel() {
        ServerLevel sl = getServerLevel();
        if (sl != null) {
            LabelNetworkRegistry.get(sl).unregister(this);
        }
        this.labelForDisplay = null;
        this.frequency = 0L;
        this.labelLink.clearTarget();
        updateState();
        setChanged();
    }

    /**
     * 在加载或跨区块迁移后重新获取网络并重连。
     */
    public void refreshLabel() {
        ServerLevel sl = getServerLevel();
        if (sl == null) return;
        if (labelForDisplay == null || labelForDisplay.isEmpty()) {
            this.frequency = 0L;
            this.labelLink.clearTarget();
            updateState();
            return;
        }
        var network = LabelNetworkRegistry.get(sl).getNetwork(sl, labelForDisplay, placerId);
        if (network == null) {
            this.frequency = 0L;
            this.labelLink.clearTarget();
        } else {
            this.frequency = network.channel();
            this.labelLink.setTarget(network);
        }
        updateState();
        setChanged();
    }

    public void onRemoved() {
        this.beingRemoved = true;
        labelLink.onUnloadOrRemove();
        ServerLevel sl = getServerLevel();
        if (sl != null) {
            LabelNetworkRegistry.get(sl).unregister(this);
        }
        if (managedNode != null) {
            managedNode.destroy();
        }
    }

    /* ===================== Tick ===================== */
    public static void serverTick(Level level, BlockPos pos, BlockState state, LabeledWirelessTransceiverBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        be.labelLink.updateStatus();
        be.updateState();
    }

    /**
     * 根据连接状态更新方块状态。
     */
    private void updateState() {
        if (this.level == null || this.level.isClientSide) return;
        if (this.beingRemoved || this.isRemoved()) return;
        BlockState currentState = this.getBlockState();
        if (!(currentState.getBlock() instanceof LabeledWirelessTransceiverBlock)) {
            return;
        }

        IGridNode node = this.getGridNode();
        int newState = 5; // 默认无连接

        if (node != null && node.isActive()) {
            int usedChannels = 0;
            for (var connection : node.getConnections()) {
                usedChannels = Math.max(connection.getUsedChannels(), usedChannels);
            }
            if (usedChannels >= 32) {
                newState = 4;
            } else if (usedChannels >= 24) {
                newState = 3;
            } else if (usedChannels >= 16) {
                newState = 2;
            } else if (usedChannels >= 8) {
                newState = 1;
            } else if (usedChannels >= 0) {
                newState = 0;
            }
        }

        if (currentState.getValue(LabeledWirelessTransceiverBlock.STATE) != newState) {
            this.level.setBlock(this.worldPosition, currentState.setValue(LabeledWirelessTransceiverBlock.STATE, newState), 3);
        }
    }

    /* ===================== AECableType 展示 ===================== */
    @Override
    public AECableType getCableConnectionType(Direction dir) {
        if (this.level == null) return AECableType.GLASS;
        var adjacentPos = this.worldPosition.relative(dir);
        if (!Objects.requireNonNull(this.getLevel()).hasChunkAt(adjacentPos)) return AECableType.GLASS;
        var adjacentHost = GridHelper.getNodeHost(this.getLevel(), adjacentPos);
        if (adjacentHost != null) {
            var t = adjacentHost.getCableConnectionType(dir.getOpposite());
            if (t != null) return t;
        }
        return AECableType.GLASS;
    }

    /* ===================== 生命周期 ===================== */
    @Override
    public void onLoad() {
        super.onLoad();
        ServerLevel sl = getServerLevel();
        if (sl == null) return;
        GridHelper.onFirstTick(this, be -> {
            be.managedNode.create(be.getLevel(), be.getBlockPos());
            be.refreshLabel();
        });
    }

    /* ===================== NBT ===================== */
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("frequency", frequency);
        tag.putBoolean("locked", locked);
        if (labelForDisplay != null) {
            tag.putString("label", labelForDisplay);
        }
        if (placerId != null) {
            tag.putUUID("placerId", placerId);
        }
        if (placerName != null) {
            tag.putString("placerName", placerName);
        }
        if (managedNode != null) {
            managedNode.saveToNBT(tag);
        }
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        this.frequency = tag.getLong("frequency");
        this.locked = tag.getBoolean("locked");
        if (tag.contains("label")) {
            this.labelForDisplay = tag.getString("label");
        } else {
            this.labelForDisplay = null;
        }
        if (tag.hasUUID("placerId")) {
            this.placerId = tag.getUUID("placerId");
        }
        if (tag.contains("placerName")) {
            this.placerName = tag.getString("placerName");
        }
        if (managedNode != null) {
            managedNode.loadFromNBT(tag);
        }
    }

    /* ===================== AE2 节点监听 ===================== */
    enum NodeListener implements IGridNodeListener<LabeledWirelessTransceiverBlockEntity> {
        INSTANCE;
        @Override
        public void onSaveChanges(LabeledWirelessTransceiverBlockEntity host, IGridNode node) {
            host.setChanged();
        }
        @Override
        public void onStateChanged(LabeledWirelessTransceiverBlockEntity host, IGridNode node, State state) {
            host.updateState();
        }
        @Override
        public void onInWorldConnectionChanged(LabeledWirelessTransceiverBlockEntity host, IGridNode node) {
            host.updateState();
        }
        @Override
        public void onGridChanged(LabeledWirelessTransceiverBlockEntity host, IGridNode node) {
            host.updateState();
        }
        @Override
        public void onOwnerChanged(LabeledWirelessTransceiverBlockEntity host, IGridNode node) {}
    }
}
