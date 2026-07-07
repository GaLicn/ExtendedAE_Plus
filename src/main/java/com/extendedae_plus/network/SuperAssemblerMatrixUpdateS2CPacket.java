package com.extendedae_plus.network;

import com.extendedae_plus.client.network.SuperAssemblerMatrixClientPacketHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuperAssemblerMatrixUpdateS2CPacket {

    private final long patternId;
    private final int inventorySize;
    private final Int2ObjectMap<ItemStack> updateMap;

    public SuperAssemblerMatrixUpdateS2CPacket(long patternId, int inventorySize,
            Int2ObjectMap<ItemStack> updateMap) {
        this.patternId = patternId;
        this.inventorySize = inventorySize;
        this.updateMap = new Int2ObjectOpenHashMap<>(updateMap);
    }

    public static void encode(SuperAssemblerMatrixUpdateS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.patternId);
        buf.writeVarInt(packet.inventorySize);
        buf.writeVarInt(packet.updateMap.size());
        for (var entry : packet.updateMap.int2ObjectEntrySet()) {
            buf.writeVarInt(entry.getIntKey());
            buf.writeItemStack(entry.getValue(), false);
        }
    }

    public static SuperAssemblerMatrixUpdateS2CPacket decode(FriendlyByteBuf buf) {
        long patternId = buf.readLong();
        int inventorySize = buf.readVarInt();
        int size = buf.readVarInt();
        var updateMap = new Int2ObjectOpenHashMap<ItemStack>();
        for (int i = 0; i < size; i++) {
            updateMap.put(buf.readVarInt(), buf.readItem());
        }
        return new SuperAssemblerMatrixUpdateS2CPacket(patternId, inventorySize, updateMap);
    }

    public static void handle(SuperAssemblerMatrixUpdateS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SuperAssemblerMatrixClientPacketHandler.handleUpdate(
                        packet.patternId, packet.inventorySize, packet.updateMap)));
        ctx.setPacketHandled(true);
    }
}
