package com.extendedae_plus.network.provider;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.PatternTerminalUtil;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;

import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * C2S: Retrieved the last uploaded pattern from the recently used provider.
 */
public class ReturnLastPatternC2SPacket {
    public ReturnLastPatternC2SPacket() {}

    public static void encode(ReturnLastPatternC2SPacket msg, FriendlyByteBuf buf) {}

    public static ReturnLastPatternC2SPacket decode(FriendlyByteBuf buf) {
        return new ReturnLastPatternC2SPacket();
    }

    public static void handle(ReturnLastPatternC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            long lastProviderId = ProviderUploadUtil.getLastUploadedProviderId(player);
            if (lastProviderId == Long.MIN_VALUE) {
                return; // Nothing to return
            }
            
            // Special ID for Assembly Matrix
            if (lastProviderId == -999999L) {
                if (player.containerMenu instanceof PatternEncodingTermMenu encMenu) {
                    IGridNode node = encMenu.getNetworkNode();
                    if (node != null && node.getGrid() != null) {
                        IGrid grid = node.getGrid();
                        // Get all matrix pattern inventories
                        try {
                            Set<TileAssemblerMatrixPattern> allTiles = grid.getMachines(TileAssemblerMatrixPattern.class);
                            for (TileAssemblerMatrixPattern tile : allTiles) {
                                if (tile != null && tile.isFormed() && tile.getMainNode().isActive()) {
                                    InternalInventory inv = tile.getTerminalPatternInventory();
                                    if (inv != null && returnPatternFromInventory(player, inv)) {
                                        return;
                                    }
                                }
                            }
                            
                            Set<PatternCorePlusBlockEntity> myAllTiles = grid.getMachines(PatternCorePlusBlockEntity.class);
                            for (PatternCorePlusBlockEntity tile : myAllTiles) {
                                if (tile != null && tile.isFormed() && tile.getMainNode().isActive()) {
                                    InternalInventory inv = tile.getTerminalPatternInventory();
                                    if (inv != null && returnPatternFromInventory(player, inv)) {
                                        return;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                return;
            }

            PatternContainer targetContainer = null;

            // Try to resolve the container using encoding menu
            if (player.containerMenu instanceof PatternEncodingTermMenu encMenu) {
                if (lastProviderId >= 0) {
                    PatternAccessTermMenu accessMenu = PatternTerminalUtil.getPatternAccessMenu(player);
                    if (accessMenu != null) {
                        targetContainer = PatternTerminalUtil.getPatternContainerById(accessMenu, lastProviderId);
                    }
                } else {
                    int index = (int) (-1L - lastProviderId);
                    List<PatternContainer> list = PatternTerminalUtil.listAvailableProvidersFromGrid(encMenu);
                    if (index >= 0 && index < list.size()) {
                        targetContainer = list.get(index);
                    }
                }
            } else {
                // Try to resolve from player network if not in encoding menu (e.g. pending ctrl+q state)
                if (lastProviderId >= 0) {
                    // It's harder without access terminal menu, but technically we could search the grid
                    return; 
                } else {
                    int index = (int) (-1L - lastProviderId);
                    List<PatternContainer> list = ProviderUploadUtil.listAvailableProvidersFromPlayerNetwork(player);
                    if (index >= 0 && index < list.size()) {
                        targetContainer = list.get(index);
                    }
                }
            }

            if (targetContainer == null || !targetContainer.isVisibleInTerminal()) {
                return;
            }

            InternalInventory inv = targetContainer.getTerminalPatternInventory();
            if (inv != null && inv.size() > 0) {
                returnPatternFromInventory(player, inv);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    private static boolean returnPatternFromInventory(ServerPlayer player, InternalInventory inv) {
        // Find the highest slot with an encoded pattern
        for (int slot = inv.size() - 1; slot >= 0; slot--) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                ItemStack extracted = inv.extractItem(slot, 1, false);
                if (!extracted.isEmpty()) {
                    if (!player.getInventory().add(extracted)) {
                        player.drop(extracted, false);
                    }
                    return true; // Found and returned
                }
            }
        }
        return false;
    }
}
