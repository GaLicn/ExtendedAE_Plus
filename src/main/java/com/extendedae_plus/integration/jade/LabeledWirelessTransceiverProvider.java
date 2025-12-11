package com.extendedae_plus.integration.jade;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 标签无线收发器：服务端数据同步。
 * 仅包含标签名、频道、网络在线状态。
 */
public enum LabeledWirelessTransceiverProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("extendedae_plus", "labeled_wireless_info");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof LabeledWirelessTransceiverBlockEntity be)) return;
        String label = be.getLabelForDisplay();
        if (label != null) {
            data.putString("label", label);
        }
        data.putLong("channel", be.getFrequency());

        IGridNode node = be.getGridNode();
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
    }
}
