package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.screen.ProviderSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: 返回网络中的样板供应器列表，客户端以「挑映射机器」模式打开供应器选择界面
 * （即 EAEP 原本添加映射的那个界面：可搜索、可直接点选机器），选中即写入配方类型 → 机器名映射。
 */
public class MappingProvidersS2CPacket implements CustomPacketPayload {
	public static final Type<MappingProvidersS2CPacket> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "mapping_providers"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MappingProvidersS2CPacket> STREAM_CODEC = StreamCodec.of(
		(buf, pkt) -> {
			buf.writeUtf(pkt.mappingKey);
			buf.writeVarInt(pkt.ids.size());
			for (int i = 0; i < pkt.ids.size(); i++) {
				buf.writeLong(pkt.ids.get(i));
				ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, pkt.names.get(i));
				buf.writeVarInt(pkt.emptySlots.get(i));
			}
		},
		buf -> {
			String mappingKey = buf.readUtf();
			int size = buf.readVarInt();
			List<Long> ids = new ArrayList<>(size);
			List<Component> names = new ArrayList<>(size);
			List<Integer> slots = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				ids.add(buf.readLong());
				names.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
				slots.add(buf.readVarInt());
			}
			return new MappingProvidersS2CPacket(mappingKey, ids, names, slots);
		}
	);

	private final String mappingKey;
	private final List<Long> ids;
	private final List<Component> names;
	private final List<Integer> emptySlots;

	MappingProvidersS2CPacket(String mappingKey, List<Long> ids, List<Component> names, List<Integer> emptySlots) {
		this.mappingKey = mappingKey;
		this.ids = ids;
		this.names = names;
		this.emptySlots = emptySlots;
	}

	public static void handle(final MappingProvidersS2CPacket msg, final IPayloadContext ctx) {
		ctx.enqueueWork(() -> handleClient(msg));
	}

	@OnlyIn(Dist.CLIENT)
	private static void handleClient(MappingProvidersS2CPacket msg) {
		var mc = Minecraft.getInstance();
		if (mc == null) return;
		mc.setScreen(new ProviderSelectScreen(mc.screen, msg.mappingKey, msg.ids, msg.names, msg.emptySlots));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
