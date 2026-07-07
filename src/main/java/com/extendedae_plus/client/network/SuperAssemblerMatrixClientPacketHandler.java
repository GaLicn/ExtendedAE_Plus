package com.extendedae_plus.client.network;

import com.extendedae_plus.client.screen.SuperAssemblerMatrixScreen;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class SuperAssemblerMatrixClientPacketHandler {

    private SuperAssemblerMatrixClientPacketHandler() {
    }

    public static void handleUpdate(long patternId, int inventorySize, Int2ObjectMap<ItemStack> updateMap) {
        if (Minecraft.getInstance().screen instanceof SuperAssemblerMatrixScreen screen) {
            screen.receiveUpdate(patternId, inventorySize, updateMap);
        }
    }

    public static void handleStats(long concurrentExecutions) {
        if (Minecraft.getInstance().screen instanceof SuperAssemblerMatrixScreen screen) {
            screen.setConcurrentExecutions(concurrentExecutions);
        }
    }
}
