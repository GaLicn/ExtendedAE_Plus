package com.extendedae_plus.network;

import com.extendedae_plus.client.network.SuperAssemblerMatrixClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SuperAssemblerMatrixStatsS2CPacket {

    private final long concurrentExecutions;

    public SuperAssemblerMatrixStatsS2CPacket(long concurrentExecutions) {
        this.concurrentExecutions = concurrentExecutions;
    }

    public static void encode(SuperAssemblerMatrixStatsS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarLong(packet.concurrentExecutions);
    }

    public static SuperAssemblerMatrixStatsS2CPacket decode(FriendlyByteBuf buf) {
        return new SuperAssemblerMatrixStatsS2CPacket(buf.readVarLong());
    }

    public static void handle(SuperAssemblerMatrixStatsS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SuperAssemblerMatrixClientPacketHandler.handleStats(packet.concurrentExecutions)));
        ctx.setPacketHandled(true);
    }
}
