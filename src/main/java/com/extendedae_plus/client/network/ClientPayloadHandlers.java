package com.extendedae_plus.client.network;

import com.extendedae_plus.client.screen.LabeledWirelessTransceiverScreen;
import com.extendedae_plus.client.screen.ProviderSelectScreen;
import com.extendedae_plus.client.screen.SuperAssemblerMatrixScreen;
import com.extendedae_plus.content.ClientPatternHighlightStore;
import com.extendedae_plus.mixin.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.network.LabelNetworkListS2CPacket;
import com.extendedae_plus.network.MappingProvidersS2CPacket;
import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.extendedae_plus.network.SetPatternHighlightS2CPacket;
import com.extendedae_plus.network.SetBlockHighlightS2CPacket;
import com.extendedae_plus.network.SetProviderPageS2CPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixStatsS2CPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixUpdateS2CPacket;
import com.extendedae_plus.network.crafting.ManualCraftingStatusS2CPacket;
import com.extendedae_plus.network.jei.SyncNetworkInventoryS2CPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.glodblock.github.extendedae.client.render.EAEHighlightHandler;
import com.glodblock.github.extendedae.util.FCClientUtil;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleSetPatternHighlight(SetPatternHighlightS2CPacket packet) {
        ClientPatternHighlightStore.setHighlight(packet.key(), packet.highlight());
    }

    public static void handleSetBlockHighlight(SetBlockHighlightS2CPacket packet) {
        var dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension());
        var endTime = System.currentTimeMillis() + packet.durationMillis();
        if (packet.face() == null) {
            EAEHighlightHandler.highlight(packet.pos(), dimension, endTime);
            return;
        }

        // 只在客户端生成朝向对应的高亮包围盒。
        var box = new AABB(2 / 16D, 2 / 16D, 0, 14 / 16D, 14 / 16D, 2 / 16D).move(packet.pos());
        var center = new AABB(packet.pos()).getCenter();
        switch (packet.face()) {
            case WEST -> box = FCClientUtil.rotor(box, center, Direction.Axis.Y, (float) (Math.PI / 2));
            case SOUTH -> box = FCClientUtil.rotor(box, center, Direction.Axis.Y, (float) Math.PI);
            case EAST -> box = FCClientUtil.rotor(box, center, Direction.Axis.Y, (float) (-Math.PI / 2));
            case UP -> box = FCClientUtil.rotor(box, center, Direction.Axis.X, (float) (-Math.PI / 2));
            case DOWN -> box = FCClientUtil.rotor(box, center, Direction.Axis.X, (float) (Math.PI / 2));
            case NORTH -> {
            }
        }
        EAEHighlightHandler.highlight(packet.pos(), packet.face(), dimension, endTime, box);
    }

    public static void handleProvidersList(ProvidersListS2CPacket packet) {
        var minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ProviderSelectScreen(minecraft.screen, packet.ids(), packet.names(), packet.emptySlots()));
    }

    public static void handleMappingProviders(MappingProvidersS2CPacket packet) {
        var minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ProviderSelectScreen(
                minecraft.screen, packet.mappingKey(), packet.ids(), packet.names(), packet.emptySlots()));
    }

    public static void handleSetProviderPage(SetProviderPageS2CPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof GuiExPatternProvider provider
                && provider instanceof com.extendedae_plus.api.IExPatternPage pageAccessor) {
            pageAccessor.eap$setCurrentPage(packet.page());
            ((AbstractContainerScreenAccessor<?>) (Object) provider).eap$setHoveredSlot(null);
        }
    }

    public static void handleLabelNetworkList(LabelNetworkListS2CPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof LabeledWirelessTransceiverScreen screen
                && screen.isFor(packet.pos())) {
            screen.updateList(packet.list(), packet.currentLabel(), packet.ownerName(), packet.usedChannels(),
                    packet.maxChannels(), packet.onlineCount());
        }
    }

    public static void handleManualCraftingStatus(ManualCraftingStatusS2CPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu == null) {
            com.extendedae_plus.content.ClientManualCraftingStatusStore.clear();
            return;
        }
        if (minecraft.player.containerMenu.containerId == packet.containerId()) {
            com.extendedae_plus.content.ClientManualCraftingStatusStore.setStatus(
                    packet.containerId(), packet.manualWaiting());
        }
    }

    public static void handleSyncNetworkInventory(SyncNetworkInventoryS2CPacket packet) {
        com.extendedae_plus.client.jei.NetworkItemCache.INSTANCE.handleUpdate(packet.fullUpdate(), packet.entries());
    }

    public static void handleSuperAssemblerMatrixUpdate(SuperAssemblerMatrixUpdateS2CPacket packet) {
        if (Minecraft.getInstance().screen instanceof SuperAssemblerMatrixScreen screen) {
            screen.receiveUpdate(packet.patternId(), packet.inventorySize(), packet.updateMap());
        }
    }

    public static void handleSuperAssemblerMatrixStats(SuperAssemblerMatrixStatsS2CPacket packet) {
        if (Minecraft.getInstance().screen instanceof SuperAssemblerMatrixScreen screen) {
            screen.setConcurrentExecutions(packet.concurrentExecutions());
        }
    }
}
