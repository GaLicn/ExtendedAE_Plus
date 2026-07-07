package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.screen.SuperAssemblerMatrixScreen;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SuperAssemblerMatrixUpdateS2CPacket implements CustomPacketPayload {

    public static final Type<SuperAssemblerMatrixUpdateS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "super_assembler_matrix_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SuperAssemblerMatrixUpdateS2CPacket> STREAM_CODEC =
            StreamCodec.of(SuperAssemblerMatrixUpdateS2CPacket::write, SuperAssemblerMatrixUpdateS2CPacket::decode);

    private final long patternId;
    private final int inventorySize;
    private final Int2ObjectMap<ItemStack> updateMap;

    public SuperAssemblerMatrixUpdateS2CPacket(long patternId, int inventorySize,
            Int2ObjectMap<ItemStack> updateMap) {
        this.patternId = patternId;
        this.inventorySize = inventorySize;
        this.updateMap = new Int2ObjectOpenHashMap<>(updateMap);
    }

    private static void write(RegistryFriendlyByteBuf buf, SuperAssemblerMatrixUpdateS2CPacket packet) {
        buf.writeLong(packet.patternId);
        buf.writeVarInt(packet.inventorySize);
        buf.writeVarInt(packet.updateMap.size());
        for (var entry : packet.updateMap.int2ObjectEntrySet()) {
            buf.writeVarInt(entry.getIntKey());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.getValue());
        }
    }

    private static SuperAssemblerMatrixUpdateS2CPacket decode(RegistryFriendlyByteBuf buf) {
        long patternId = buf.readLong();
        int inventorySize = buf.readVarInt();
        int size = buf.readVarInt();
        var updateMap = new Int2ObjectOpenHashMap<ItemStack>();
        for (int i = 0; i < size; i++) {
            updateMap.put(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new SuperAssemblerMatrixUpdateS2CPacket(patternId, inventorySize, updateMap);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SuperAssemblerMatrixUpdateS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(SuperAssemblerMatrixUpdateS2CPacket packet) {
        if (Minecraft.getInstance().screen instanceof SuperAssemblerMatrixScreen screen) {
            screen.receiveUpdate(packet.patternId, packet.inventorySize, packet.updateMap);
        }
    }
}
