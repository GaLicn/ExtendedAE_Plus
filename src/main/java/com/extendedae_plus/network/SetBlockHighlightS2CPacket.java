package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.glodblock.github.extendedae.client.render.EAEHighlightHandler;
import com.glodblock.github.extendedae.util.FCClientUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-side block highlight used after opening a provider from the crafting monitor. */
public final class SetBlockHighlightS2CPacket implements CustomPacketPayload {
    public static final Type<SetBlockHighlightS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_block_highlight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetBlockHighlightS2CPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.pos);
                        buf.writeBoolean(packet.face != null);
                        if (packet.face != null) {
                            buf.writeEnum(packet.face);
                        }
                        buf.writeResourceLocation(packet.dimension);
                        buf.writeLong(packet.durationMillis);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        Direction face = buf.readBoolean() ? buf.readEnum(Direction.class) : null;
                        return new SetBlockHighlightS2CPacket(
                                pos,
                                face,
                                buf.readResourceLocation(),
                                buf.readLong());
                    });

    private final BlockPos pos;
    private final Direction face;
    private final ResourceLocation dimension;
    private final long durationMillis;

    public SetBlockHighlightS2CPacket(BlockPos pos, Direction face, ResourceLocation dimension, long durationMillis) {
        this.pos = pos;
        this.face = face;
        this.dimension = dimension;
        this.durationMillis = durationMillis;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetBlockHighlightS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension);
            long endTime = System.currentTimeMillis() + packet.durationMillis;
            if (packet.face == null) {
                EAEHighlightHandler.highlight(packet.pos, dimension, endTime);
                return;
            }

            AABB box = new AABB(2 / 16D, 2 / 16D, 0, 14 / 16D, 14 / 16D, 2 / 16D)
                    .move(packet.pos);
            var center = new AABB(packet.pos).getCenter();
            switch (packet.face) {
                case WEST -> box = FCClientUtil.rotor(box, center, Direction.Axis.Y, (float) (Math.PI / 2));
                case SOUTH -> box = FCClientUtil.rotor(box, center, Direction.Axis.Y, (float) Math.PI);
                case EAST -> box = FCClientUtil.rotor(box, center, Direction.Axis.Y, (float) (-Math.PI / 2));
                case UP -> box = FCClientUtil.rotor(box, center, Direction.Axis.X, (float) (-Math.PI / 2));
                case DOWN -> box = FCClientUtil.rotor(box, center, Direction.Axis.X, (float) (Math.PI / 2));
                case NORTH -> {
                }
            }
            EAEHighlightHandler.highlight(packet.pos, packet.face, dimension, endTime, box);
        });
    }
}
