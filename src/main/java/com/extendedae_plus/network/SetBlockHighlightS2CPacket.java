package com.extendedae_plus.network;

import com.glodblock.github.extendedae.client.render.EAEHighlightHandler;
import com.glodblock.github.extendedae.util.FCClientUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 指示客户端对某个方块位置进行高亮（仅作用于接收该包的客户端）
 */
public class SetBlockHighlightS2CPacket {
    private final BlockPos pos;
    private final Direction face; // 可为 null，表示方块形
    private final ResourceLocation dim; // 维度的 ResourceLocation
    private final long durationMillis; // 持续时间（毫秒）

    public SetBlockHighlightS2CPacket(BlockPos pos, Direction face, ResourceLocation dim, long durationMillis) {
        this.pos = pos;
        this.face = face;
        this.dim = dim;
        this.durationMillis = durationMillis;
    }

    public static void encode(SetBlockHighlightS2CPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeBoolean(pkt.face != null);
        if (pkt.face != null) buf.writeEnum(pkt.face);
        buf.writeResourceLocation(pkt.dim);
        buf.writeLong(pkt.durationMillis);
    }

    public static SetBlockHighlightS2CPacket decode(FriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        Direction face = null;
        if (buf.readBoolean()) face = buf.readEnum(Direction.class);
        var dim = buf.readResourceLocation();
        long dur = buf.readLong();
        return new SetBlockHighlightS2CPacket(pos, face, dim, dur);
    }

    public static void handle(SetBlockHighlightS2CPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                // 在客户端执行高亮
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, msg.dim);
                long endTime = System.currentTimeMillis() + msg.durationMillis;
                if (msg.face == null) {
                    EAEHighlightHandler.highlight(msg.pos, dimKey, endTime);
                } else {
                    var origin = new AABB(msg.pos);
                    switch (msg.face) {
                        case WEST -> origin = FCClientUtil.rotor(origin, origin.getCenter(), Direction.Axis.Y, (float) (Math.PI / 2));
                        case SOUTH -> origin = FCClientUtil.rotor(origin, origin.getCenter(), Direction.Axis.Y, (float) Math.PI);
                        case EAST -> origin = FCClientUtil.rotor(origin, origin.getCenter(), Direction.Axis.Y, (float) (-Math.PI / 2));
                        case UP -> origin = FCClientUtil.rotor(origin, origin.getCenter(), Direction.Axis.X, (float) (-Math.PI / 2));
                        case DOWN -> origin = FCClientUtil.rotor(origin, origin.getCenter(), Direction.Axis.X, (float) (Math.PI / 2));
                    }
                    EAEHighlightHandler.highlight(msg.pos, msg.face, dimKey, endTime, origin);
                }
            } catch (Throwable ignored) {
            }
        });
        ctx.setPacketHandled(true);
    }
}


