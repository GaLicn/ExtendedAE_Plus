package com.extendedae_plus.client.network;

import com.extendedae_plus.network.LabelNetworkListS2CPacket;
import com.extendedae_plus.network.MappingProvidersS2CPacket;
import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.extendedae_plus.network.SetBlockHighlightS2CPacket;
import com.extendedae_plus.network.SetPatternHighlightS2CPacket;
import com.extendedae_plus.network.SetProviderPageS2CPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixStatsS2CPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixUpdateS2CPacket;
import com.extendedae_plus.network.crafting.ManualCraftingStatusS2CPacket;
import com.extendedae_plus.network.jei.SyncNetworkInventoryS2CPacket;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

public final class ClientPayloadRegistration {
    private ClientPayloadRegistration() {
    }

    public static void register(RegisterClientPayloadHandlersEvent event) {
        // 客户端 handler 只在客户端事件中注册，公共网络类无需加载 Screen。
        event.register(SetPatternHighlightS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleSetPatternHighlight(packet));
        event.register(SetBlockHighlightS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleSetBlockHighlight(packet));
        event.register(ProvidersListS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleProvidersList(packet));
        event.register(MappingProvidersS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleMappingProviders(packet));
        event.register(SetProviderPageS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleSetProviderPage(packet));
        event.register(LabelNetworkListS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleLabelNetworkList(packet));
        event.register(ManualCraftingStatusS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleManualCraftingStatus(packet));
        event.register(SyncNetworkInventoryS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleSyncNetworkInventory(packet));
        event.register(SuperAssemblerMatrixUpdateS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleSuperAssemblerMatrixUpdate(packet));
        event.register(SuperAssemblerMatrixStatsS2CPacket.TYPE,
                (packet, context) -> ClientPayloadHandlers.handleSuperAssemblerMatrixStats(packet));
    }
}
