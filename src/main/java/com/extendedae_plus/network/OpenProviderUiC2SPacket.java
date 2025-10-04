package com.extendedae_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenProviderUiC2SPacket implements CustomPacketPayload {
    public static final Type<OpenProviderUiC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "open_provider_ui"));

    public static final StreamCodec<FriendlyByteBuf, OpenProviderUiC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeLong(pkt.posLong);
                buf.writeResourceLocation(pkt.dimId);
                buf.writeVarInt(pkt.faceOrd);
            },
            buf -> new OpenProviderUiC2SPacket(buf.readLong(), buf.readResourceLocation(), buf.readVarInt())
    );
    private final long posLong;
    private final ResourceLocation dimId;
    private final int faceOrd; // 目前保留，若目标需要可用

    public OpenProviderUiC2SPacket(long posLong, ResourceLocation dimId, int faceOrd) {
        this.posLong = posLong;
        this.dimId = dimId;
        this.faceOrd = faceOrd;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final OpenProviderUiC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // 校验维度与方块
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, msg.dimId);
            ServerLevel level = player.server.getLevel(levelKey);
            if (level == null) {
                return; // 无效维度
            }

            BlockPos pos = BlockPos.of(msg.posLong);
            if (!level.isLoaded(pos)) {
                return; // 区块未加载
            }

            var be = level.getBlockEntity(pos);
            var stateAtPos = level.getBlockState(pos);

            // 目标通常是供应器所面对/连接的“相邻方块”，优先尝试邻居
            Direction[] tries = (msg.faceOrd >= 0 && msg.faceOrd < Direction.values().length)
                    ? new Direction[]{Direction.values()[msg.faceOrd]}
                    : Direction.values();

            for (Direction dir : tries) {
                BlockPos targetPos = pos.relative(dir);
                BlockEntity tbe = level.getBlockEntity(targetPos);
                if (tbe instanceof MenuProvider provider) {
                    player.openMenu(provider, targetPos);
                    return;
                }
                var tstate = level.getBlockState(targetPos);
                MenuProvider provider2 = tstate.getMenuProvider(level, targetPos);
                if (provider2 != null) {
                    player.openMenu(provider2, targetPos);
                    return;
                }
            }

            // 若邻居未提供 MenuProvider，则跳过兜底交互（1.21 API 变更，避免不兼容的 use 调用）

        });
    }
}
