package com.extendedae_plus.integration.jade;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.extendedae_plus.ae.wireless.IWirelessEndpoint;
import com.extendedae_plus.ae.wireless.WirelessMasterRegistry;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum WirelessTransceiverProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("extendedae_plus", "wireless_transceiver_info");
    // 此类仅用于同步服务端数据，不再包含客户端选项键

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof WirelessTransceiverBlockEntity blockEntity) {
            data.putLong("frequency", blockEntity.getFrequency());
            data.putBoolean("masterMode", blockEntity.isMasterMode());
            data.putBoolean("locked", blockEntity.isLocked());
            // 判断 AE 网络是否可用：节点存在、加入网路且网络通电
            IGridNode node = blockEntity.getGridNode();
            IGrid grid = node == null ? null : node.getGrid();
            boolean networkUsable = false;
            if (grid != null) {
                try {
                    networkUsable = grid.getEnergyService().isNetworkPowered();
                } catch (Throwable ignored) {
                    networkUsable = false;
                }
            }
            data.putBoolean("networkUsable", networkUsable);
            
            // 添加频道使用信息（参考AE2的 IUsedChannelProvider 实现）
            int usedChannels = 0;
            int maxChannels = 0;
            if (node != null && node.isActive()) {
                // 遍历该节点的所有连接，取使用频道数的最大值
                for (var connection : node.getConnections()) {
                    usedChannels = Math.max(connection.getUsedChannels(), usedChannels);
                }
                // 获取节点的最大频道容量（致密线缆为32）
                if (node instanceof appeng.me.GridNode gridNode) {
                    var channelMode = gridNode.getGrid().getPathingService().getChannelMode();
                    if (channelMode == appeng.api.networking.pathing.ChannelMode.INFINITE) {
                        maxChannels = -1; // 无限频道
                    } else {
                        maxChannels = gridNode.getMaxChannels();
                    }
                }
            }
            data.putInt("usedChannels", usedChannels);
            data.putInt("maxChannels", maxChannels);
            
            // 添加所有者信息（有FTBTeams时显示团队，否则显示玩家）
            var placerId = blockEntity.getPlacerId();
            if (placerId != null) {
                data.putUUID("placerId", placerId);
                var level = blockEntity.getServerLevel();
                if (level != null) {
                    // 使用WirelessTeamUtil自动判断显示团队或玩家名称
                    Component ownerName = WirelessTeamUtil.getNetworkOwnerName(level, placerId);
                    data.putString("ownerName", ownerName.getString());
                }
            }
            
            // 如果是从模式，查询主节点位置与维度
            if (!blockEntity.isMasterMode()) {
                var level = blockEntity.getServerLevel();
                long freq = blockEntity.getFrequency();
                // 复用上面的placerId变量
                IWirelessEndpoint master = WirelessMasterRegistry.get(level, freq, placerId);
                if (master != null && !master.isEndpointRemoved()) {
                    if (master instanceof WirelessTransceiverBlockEntity masterBlockEntity && masterBlockEntity.getCustomName() != null) {
                        data.putString("customName", masterBlockEntity.getCustomName().getString());
                    }
                    BlockPos pos = master.getBlockPos();
                    if (pos != null) {
                        data.putLong("masterPos", pos.asLong());
                    }
                    if (master.getServerLevel() != null) {
                        data.putString("masterDim", master.getServerLevel().dimension().location().toString());
                    }
                }
            }
        }
    }
}