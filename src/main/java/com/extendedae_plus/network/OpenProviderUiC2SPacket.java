package com.extendedae_plus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenProviderUiC2SPacket {
    private final long posLong;
    private final ResourceLocation dimId;
    private final int faceOrd; // 目前保留，若目标需要可用

    public OpenProviderUiC2SPacket(long posLong, ResourceLocation dimId, int faceOrd) {
        this.posLong = posLong;
        this.dimId = dimId;
        this.faceOrd = faceOrd;
    }

    public static void encode(OpenProviderUiC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.posLong);
        buf.writeResourceLocation(msg.dimId);
        buf.writeVarInt(msg.faceOrd);
    }

    public static OpenProviderUiC2SPacket decode(FriendlyByteBuf buf) {
        long posLong = buf.readLong();
        ResourceLocation dimId = buf.readResourceLocation();
        int faceOrd = buf.readVarInt();
        return new OpenProviderUiC2SPacket(posLong, dimId, faceOrd);
        
    }

    public static void handle(OpenProviderUiC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            

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
                    NetworkHooks.openScreen(player, provider, targetPos);
                    return;
                }
                var tstate = level.getBlockState(targetPos);
                MenuProvider provider2 = tstate.getMenuProvider(level, targetPos);
                if (provider2 != null) {
                    NetworkHooks.openScreen(player, provider2, targetPos);
                    return;
                }
            }

            // 如果邻居也未提供 MenuProvider，则兜底：尽量模拟一次徒手右键相邻方块
            boolean anyHandEmpty = player.getMainHandItem().isEmpty() || player.getOffhandItem().isEmpty();
            if (anyHandEmpty) {
                InteractionHand hand = player.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                if (msg.faceOrd >= 0 && msg.faceOrd < Direction.values().length) {
                    Direction dir = Direction.values()[msg.faceOrd];
                    BlockPos targetPos = pos.relative(dir);
                    var state2 = level.getBlockState(targetPos);
                    var hit = new BlockHitResult(Vec3.atCenterOf(targetPos), dir.getOpposite(), targetPos, false);
                    InteractionResult r = state2.use(level, player, hand, hit);
                    if (r.consumesAction()) {
                        return;
                    }
                } else {
                    // 无明确朝向：优先挑选有方块实体的邻居，否则挑选非空气方块
                    Direction chosen = null;
                    for (Direction d : Direction.values()) {
                        if (level.getBlockEntity(pos.relative(d)) != null) { chosen = d; break; }
                    }
                    if (chosen == null) {
                        for (Direction d : Direction.values()) {
                            if (!level.getBlockState(pos.relative(d)).isAir()) { chosen = d; break; }
                        }
                    }
                    if (chosen != null) {
                        BlockPos targetPos = pos.relative(chosen);
                        var state2 = level.getBlockState(targetPos);
                        var hit = new BlockHitResult(Vec3.atCenterOf(targetPos), chosen.getOpposite(), targetPos, false);
                        InteractionResult r = state2.use(level, player, hand, hit);
                        if (r.consumesAction()) {
                            return;
                        }
                    } else {
                        // 无可选邻居
                    }
                }
            } else {
                // 双手占用则跳过兜底交互
            }

            context.setPacketHandled(true);
        });
    }
}
