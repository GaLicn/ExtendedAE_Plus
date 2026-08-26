package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.uploadPattern.BatchPendingUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 批量编码待选队列里，玩家对当前项做出「跳过」或「放弃整条队列」的选择。
 * <p>
 * 选中机器走 {@link UploadEncodedPatternToProviderC2SPacket}（复用同一套负数索引），
 * 这里只处理不上传的两种出口：样板都退回背包，区别是继续问下一项还是一次问完。
 */
public class BatchPendingActionC2SPacket implements CustomPacketPayload {
	public static final Type<BatchPendingActionC2SPacket> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "batch_pending_action"));

	public enum Action {
		/** 当前项退回背包，继续问下一项。 */
		SKIP,
		/** 剩余项全部退回背包，结束队列。 */
		ABORT
	}

	public static final StreamCodec<FriendlyByteBuf, BatchPendingActionC2SPacket> STREAM_CODEC = StreamCodec.of(
			(buf, pkt) -> buf.writeEnum(pkt.action),
			buf -> new BatchPendingActionC2SPacket(buf.readEnum(Action.class))
	);

	private final Action action;

	public BatchPendingActionC2SPacket(Action action) {
		this.action = action == null ? Action.ABORT : action;
	}

	public static void handle(final BatchPendingActionC2SPacket msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (!(ctx.player() instanceof ServerPlayer player)) {
				return;
			}
			if (!BatchPendingUploadUtil.hasPending(player)) {
				return;
			}
			if (msg.action == Action.SKIP) {
				BatchPendingUploadUtil.skipHead(player);
			} else {
				BatchPendingUploadUtil.abortAll(player);
			}
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
