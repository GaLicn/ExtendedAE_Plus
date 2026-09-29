package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C：通知客户端高亮已打开的样板供应器位置。 */
public final class SetBlockHighlightS2CPacket implements CustomPacketPayload {
    public static final Type<SetBlockHighlightS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_block_highlight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetBlockHighlightS2CPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.pos);
                        buf.writeBoolean(packet.face != null);
                        if (packet.face != null) {
                            buf.writeEnum(packet.face);
                        }
                        buf.writeIdentifier(packet.dimension);
                        buf.writeLong(packet.durationMillis);
                    },
                    buf -> new SetBlockHighlightS2CPacket(
                            buf.readBlockPos(),
                            buf.readBoolean() ? buf.readEnum(Direction.class) : null,
                            buf.readIdentifier(),
                            buf.readLong()));

    private final BlockPos pos;
    private final Direction face;
    private final Identifier dimension;
    private final long durationMillis;

    public SetBlockHighlightS2CPacket(BlockPos pos, Direction face, Identifier dimension, long durationMillis) {
        this.pos = pos;
        this.face = face;
        this.dimension = dimension;
        this.durationMillis = durationMillis;
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction face() {
        return face;
    }

    public Identifier dimension() {
        return dimension;
    }

    public long durationMillis() {
        return durationMillis;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
