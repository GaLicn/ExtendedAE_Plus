package com.extendedae_plus.integration.jade;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import com.extendedae_plus.wireless.IWirelessEndpoint;
import com.extendedae_plus.wireless.WirelessMasterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum WirelessTransceiverProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("extendedae_plus", "wireless_transceiver_info");
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
            // 如果是从模式，查询主节点位置与维度
            if (!blockEntity.isMasterMode()) {
                var level = blockEntity.getServerLevel();
                long freq = blockEntity.getFrequency();
                IWirelessEndpoint master = WirelessMasterRegistry.get(level, freq);
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