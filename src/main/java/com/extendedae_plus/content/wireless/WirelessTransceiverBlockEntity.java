package com.extendedae_plus.content.wireless;

import appeng.api.networking.*;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import com.extendedae_plus.init.ModBlockEntities;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.wireless.IWirelessEndpoint;
import com.extendedae_plus.wireless.WirelessMasterLink;
import com.extendedae_plus.wireless.WirelessSlaveLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;

/**
 * 无线收发器方块实体（骨架）：
 * - 主/从模式切换；
 * - 频率设置；
 * - 集成 AE2 节点；
 * - 集成无线主/从逻辑。
 */
public class WirelessTransceiverBlockEntity extends AEBaseBlockEntity implements IWirelessEndpoint, IInWorldGridNodeHost {

    private IManagedGridNode managedNode;

    private long frequency = 1L;
    private boolean masterMode = false;
    private boolean locked = false;

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
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("frequency", frequency);
        tag.putBoolean("master", masterMode);
        tag.putBoolean("locked", locked);
        if (managedNode != null) {
            managedNode.saveToNBT(tag);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.frequency = tag.getLong("frequency");
        this.masterMode = tag.getBoolean("master");
        this.locked = tag.getBoolean("locked");
        if (managedNode != null) {
            managedNode.loadFromNBT(tag);
        }
        // 应用到链接器
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
        }
        @Override
        public void onInWorldConnectionChanged(WirelessTransceiverBlockEntity host, IGridNode node) {}
        @Override
        public void onGridChanged(WirelessTransceiverBlockEntity host, IGridNode node) {}
        @Override
        public void onOwnerChanged(WirelessTransceiverBlockEntity host, IGridNode node) {}
    }
}
