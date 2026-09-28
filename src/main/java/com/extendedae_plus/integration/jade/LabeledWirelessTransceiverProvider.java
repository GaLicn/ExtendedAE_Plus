package com.extendedae_plus.integration.jade;

import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import appeng.me.GridNode;
import appeng.api.networking.IGridNode;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 标签无线收发器：服务端数据同步（无主从）。
 */
public enum LabeledWirelessTransceiverProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath("extendedae_plus", "labeled_wireless_info");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof LabeledWirelessTransceiverBlockEntity be)) return;
        String label = be.getLabelForDisplay();
        if (label != null) {
            data.putString("label", label);
        }
        // 所有者
        var placerId = be.getPlacerId();
        if (placerId != null && be.getServerLevel() != null) {
            data.putIntArray("placerId", UUIDUtil.uuidToIntArray(placerId));
            data.putString("ownerName", WirelessTeamUtil.getNetworkOwnerName(be.getServerLevel(), placerId).getString());
        }

        IGridNode node = be.getGridNode();
        IGrid grid = node == null ? null : node.getGrid();
        boolean networkUsable = false;
        int usedChannels = 0;
        int maxChannels = 0;
        if (node != null && node.isActive()) {
            for (var connection : node.getConnections()) {
                usedChannels = Math.max(connection.getUsedChannels(), usedChannels);
            }
            if (node instanceof GridNode gridNode) {
                var channelMode = gridNode.getGrid().getPathingService().getChannelMode();
                if (channelMode == ChannelMode.INFINITE) {
                    maxChannels = -1;
                } else {
                    maxChannels = gridNode.getMaxChannels();
                }
            }
        }
        networkUsable = grid != null && grid.getEnergyService().isNetworkPowered();
        data.putInt("usedChannels", usedChannels);
        data.putInt("maxChannels", maxChannels);
        data.putBoolean("networkUsable", networkUsable);
    }
}
