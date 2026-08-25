package com.extendedae_plus.network;

import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.uploadPattern.BatchPatternUploadUtil;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S: 为某个配方类型挑选机器建立映射时，请求当前网络中的样板供应器列表。
 * <p>
 * 与 {@link RequestProvidersListC2SPacket} 的区别：那个用于「把手上的样板传到哪台机器」，
 * 依赖编码终端或 pending 样板、且只列有空位的机器；本封包用于「这个配方类型由哪台机器处理」，
 * 入口是合成树上的感叹号，玩家并没有打开编码终端，机器当下满不满也与映射无关。
 */
public class RequestMappingProvidersC2SPacket implements CustomPacketPayload {
	public static final Type<RequestMappingProvidersC2SPacket> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "request_mapping_providers"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RequestMappingProvidersC2SPacket> STREAM_CODEC =
		StreamCodec.of(
			(buf, pkt) -> buf.writeUtf(pkt.mappingKey),
			buf -> new RequestMappingProvidersC2SPacket(buf.readUtf())
		);

	private final String mappingKey;

	public RequestMappingProvidersC2SPacket(String mappingKey) {
		this.mappingKey = mappingKey == null ? "" : mappingKey;
	}

	public static void handle(final RequestMappingProvidersC2SPacket msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (!(ctx.player() instanceof ServerPlayer player)) {
				return;
			}

			IGrid grid = CtrlQPendingUploadUtil.findPlayerGrid(player);
			if (grid == null) {
				player.displayClientMessage(Component.translatable("message.extendedae_plus.no_network"), false);
				return;
			}

			// 与 RequestProvidersListC2SPacket 的回退分支同一约定：负数 ID 表示按索引。
			// 映射流程不会用到这些 ID，保留只是为了复用供应器选择界面的分组逻辑。
			List<PatternContainer> containers = BatchPatternUploadUtil.listAllProvidersFromGrid(grid);
			List<Long> ids = new ArrayList<>(containers.size());
			List<Component> names = new ArrayList<>(containers.size());
			List<Integer> slots = new ArrayList<>(containers.size());
			for (int i = 0; i < containers.size(); i++) {
				PatternContainer container = containers.get(i);
				if (container == null) continue;
				ids.add(-1L - i);
				names.add(ExtendedAEPatternUploadUtil.getProviderDisplayNameComponent(container));
				slots.add(Math.max(0, ExtendedAEPatternUploadUtil.getAvailableSlots(container)));
			}

			player.connection.send(new MappingProvidersS2CPacket(msg.mappingKey, ids, names, slots));
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
