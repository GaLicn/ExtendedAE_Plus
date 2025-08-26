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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            Logger logger = LogManager.getLogger("ExtendedAE_Plus");

            // 校验维度与方块
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, msg.dimId);
            ServerLevel level = player.server.getLevel(levelKey);
            if (level == null) {
                logger.warn("[EPlus] OpenProviderUiC2SPacket: invalid dimension {}", msg.dimId);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("❌ 维度无效:" + msg.dimId), true);
                return; // 无效维度
            }

            BlockPos pos = BlockPos.of(msg.posLong);
            if (!level.isLoaded(pos)) {
                logger.warn("[EPlus] OpenProviderUiC2SPacket: chunk not loaded at {} in {}", pos, msg.dimId);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("❌ 区块未加载:" + pos.toShortString()), true);
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
                    logger.debug("[EPlus] OpenProviderUiC2SPacket: opened BE MenuProvider at {} (neighbor via {})", targetPos, dir);
                    return;
                }
                var tstate = level.getBlockState(targetPos);
                MenuProvider provider2 = tstate.getMenuProvider(level, targetPos);
                if (provider2 != null) {
                    NetworkHooks.openScreen(player, provider2, targetPos);
                    logger.debug("[EPlus] OpenProviderUiC2SPacket: opened State MenuProvider at {} (neighbor via {})", targetPos, dir);
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
                    logger.debug("[EPlus] OpenProviderUiC2SPacket: fallback(use) at {} hit {} (via {}), result={}", targetPos, dir.getOpposite(), dir, r);
                    if (r.consumesAction()) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("✅ 已尝试模拟右键交互: " + r), true);
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
                        logger.debug("[EPlus] OpenProviderUiC2SPacket: fallback(use) at {} hit {} (auto via {}), result={}", targetPos, chosen.getOpposite(), chosen, r);
                        if (r.consumesAction()) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("✅ 已尝试模拟右键交互: " + r), true);
                            return;
                        }
                    } else {
                        logger.debug("[EPlus] OpenProviderUiC2SPacket: no neighbor candidate for fallback (faceOrd<0)");
                    }
                }
            } else {
                logger.debug("[EPlus] OpenProviderUiC2SPacket: skip fallback(use) because both hands occupied");
            }

            // 若走到这里，说明未能打开界面
            logger.warn("[EPlus] OpenProviderUiC2SPacket: No MenuProvider around {} (BE={}, Block={})", pos,
                    be == null ? "null" : be.getClass().getName(), stateAtPos.getBlock().getClass().getName());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("❌ 未找到可打开的相邻界面"), true);
        });
        context.setPacketHandled(true);
    }
}
