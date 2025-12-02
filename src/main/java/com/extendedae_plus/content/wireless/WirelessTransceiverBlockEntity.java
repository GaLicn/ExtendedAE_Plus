package com.extendedae_plus.content.wireless;

import appeng.api.networking.*;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import com.extendedae_plus.ae.wireless.IWirelessEndpoint;
import com.extendedae_plus.ae.wireless.WirelessMasterLink;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
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
 * 无线收发器方块实体（骨架）：
 * - 主/从模式切换；
 * - 频率设置；
 * - 集成 AE2 节点；
 * - 集成无线主/从逻辑。
 * - 支持FTBTeams队伍隔离（软依赖）
 */
public class WirelessTransceiverBlockEntity extends AEBaseBlockEntity implements IWirelessEndpoint, IInWorldGridNodeHost {

    private IManagedGridNode managedNode;

    private long frequency = 1L;
    private boolean masterMode = false;
    private boolean locked = false;
    
    @Nullable
    private UUID placerId; // 放置者UUID，用于队伍隔离
    @Nullable
    private String placerName; // 放置者名称，用于显示

    private WirelessMasterLink masterLink;
    private WirelessSlaveLink slaveLink;

    public WirelessTransceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_TRANSCEIVER_BE.get(), pos, state);
        // 创建 AE2 管理节点
        this.managedNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
                .setFlags(GridFlags.DENSE_CAPACITY);
        this.managedNode.setIdlePowerUsage(1.0); // 可按需调整基础待机功耗
        this.managedNode.setTagName("wireless_node");
        this.managedNode.setInWorldNode(true);
        this.managedNode.setExposedOnSides(EnumSet.allOf(Direction.class));
        // 可见表示，方便在 AE2 界面中识别（可选）
        this.managedNode.setVisualRepresentation(ModItems.WIRELESS_TRANSCEIVER.get().getDefaultInstance());
        // 初始化无线逻辑
        this.masterLink = new WirelessMasterLink(this);
        this.slaveLink = new WirelessSlaveLink(this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        // 根据相邻方块的实际连接类型渲染（优先采用相邻主机返回的类型），回退为 GLASS。
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

    /* ===================== 公共方法（交互调用） ===================== */
    
    /**
     * 设置放置者UUID和名称（在方块放置时调用）
     */
    public void setPlacerId(@Nullable UUID placerId, @Nullable String placerName) {
        if (this.placerId != null && !this.placerId.equals(placerId)) {
            // 如果所有者改变，需要重新注册
            if (this.masterMode) {
                masterLink.onUnloadOrRemove();
            } else {
                slaveLink.onUnloadOrRemove();
            }
        }
        this.placerId = placerId;
        this.placerName = placerName;
        this.masterLink.setPlacerId(placerId);
        this.slaveLink.setPlacerId(placerId);
        setChanged();
    }
    
    /**
     * 仅设置UUID（兼容旧代码）
     */
    public void setPlacerId(@Nullable UUID placerId) {
        setPlacerId(placerId, null);
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

    public void setFrequency(long frequency) {
        if (this.locked) return;
        if (this.frequency == frequency) return;
        this.frequency = frequency;
        if (isMasterMode()) {
            masterLink.setFrequency(frequency);
        } else {
            slaveLink.setFrequency(frequency);
        }
        setChanged();
    }

    public boolean isMasterMode() {
        return masterMode;
    }

    public void setMasterMode(boolean masterMode) {
        if (this.locked) return;
        if (this.masterMode == masterMode) return;
        // 切换前清理原模式状态
        if (this.masterMode) {
            masterLink.onUnloadOrRemove();
        } else {
            slaveLink.onUnloadOrRemove();
        }
        this.masterMode = masterMode;
        // 切换后应用频率
        if (this.masterMode) {
            masterLink.setFrequency(frequency);
        } else {
            slaveLink.setFrequency(frequency);
        }
        setChanged();
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (this.locked == locked) return;
        this.locked = locked;
        setChanged();
    }

    public void onRemoved() {
        if (this.masterMode) {
            masterLink.onUnloadOrRemove();
        } else {
            slaveLink.onUnloadOrRemove();
        }
        if (managedNode != null) {
            managedNode.destroy();
        }
    }

    /* ===================== Tick ===================== */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WirelessTransceiverBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        if (!be.masterMode) {
            // 从端需要周期检查与维护连接
            be.slaveLink.updateStatus();
        }
        // 更新状态
        be.updateState();
    }

    /**
     * 根据连接状态和频道数更新方块状态
     */
    private void updateState() {
        if (this.level == null || this.level.isClientSide) return;
        
        IGridNode node = this.getGridNode();
        int newState = 5; // 默认状态：无连接
        
        if (node != null && node.isActive()) {
            // 获取该节点使用的频道数（与 jade 中获取频道使用量的方式一致）
            int usedChannels = 0;
            for (var connection : node.getConnections()) {
                usedChannels = Math.max(connection.getUsedChannels(), usedChannels);
            }
            
            // 根据频道数计算状态：
            // 有连接但频道数 < 8：状态0（创建连接时）
            // 频道数 >= 8 且 < 16：状态1
            // 频道数 >= 16 且 < 24：状态2
            // 频道数 >= 24 且 < 32：状态3
            // 频道数 >= 32：状态4
            if (usedChannels >= 32) {
                newState = 4;
            } else if (usedChannels >= 24) {
                newState = 3;
            } else if (usedChannels >= 16) {
                newState = 2;
            } else if (usedChannels >= 8) {
                newState = 1;
            } else if (usedChannels >= 0) {
                newState = 0; // 有连接但频道数 < 8
            }
            // 如果 usedChannels == 0，保持 newState = 5（无连接状态）
        }
        
        // 更新方块状态
        BlockState currentState = this.getBlockState();
        if (currentState.getValue(WirelessTransceiverBlock.STATE) != newState) {
            this.level.setBlock(this.worldPosition, currentState.setValue(WirelessTransceiverBlock.STATE, newState), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 仅服务端创建节点
        ServerLevel sl = getServerLevel();
        if (sl == null) return;
        // 在首个 tick 创建，以保证区块已就绪
        GridHelper.onFirstTick(this, be -> {
            be.managedNode.create(be.getLevel(), be.getBlockPos());
            // 节点创建后，重新应用当前模式与频率，确保：
            // - 主端在重载后完成注册；
            // - 从端在重载后开始维护连接。
            if (be.masterMode) {
                be.masterLink.setFrequency(be.frequency);
            } else {
                be.slaveLink.setFrequency(be.frequency);
            }
        });
    }

    /* ===================== NBT ===================== */
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("frequency", frequency);
        tag.putBoolean("master", masterMode);
        tag.putBoolean("locked", locked);
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
        this.masterMode = tag.getBoolean("master");
        this.locked = tag.getBoolean("locked");
        
        if (tag.hasUUID("placerId")) {
            this.placerId = tag.getUUID("placerId");
            this.masterLink.setPlacerId(this.placerId);
            this.slaveLink.setPlacerId(this.placerId);
        }
        
        if (tag.contains("placerName")) {
            this.placerName = tag.getString("placerName");
        }

        if (managedNode != null) {
            managedNode.loadFromNBT(tag);
        }
        if (masterMode) {
            masterLink.setFrequency(frequency);
        } else {
            slaveLink.setFrequency(frequency);
        }
    }

    /* ===================== AE2 节点监听 ===================== */
    enum NodeListener implements IGridNodeListener<WirelessTransceiverBlockEntity> {
        INSTANCE;
        @Override
        public void onSaveChanges(WirelessTransceiverBlockEntity host, IGridNode node) {
            host.setChanged();
        }
        @Override
        public void onStateChanged(WirelessTransceiverBlockEntity host, IGridNode node, State state) {
            // 可在此响应 POWER/CHANNEL 等变化，刷新显示等
            host.updateState();
        }
        @Override
        public void onInWorldConnectionChanged(WirelessTransceiverBlockEntity host, IGridNode node) {
            // 连接变化时更新状态
            host.updateState();
        }
        @Override
        public void onGridChanged(WirelessTransceiverBlockEntity host, IGridNode node) {
            // 网格变化时更新状态
            host.updateState();
        }
        @Override
        public void onOwnerChanged(WirelessTransceiverBlockEntity host, IGridNode node) {}
    }
}
