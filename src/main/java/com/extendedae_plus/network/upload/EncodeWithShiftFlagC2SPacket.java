package com.extendedae_plus.network.upload;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync.EncodeRequest;
import com.extendedae_plus.api.upload.UploadPreferences;
import com.extendedae_plus.config.PatternUploadPriority;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * C2S：图样编码终端点击「编码」按钮时上报本次编码请求，服务端在随后的 {@code encode()} 中消费。
 *
 * <p>Shift 状态、唯一匹配判定与自动上传偏好均依赖客户端环境，故由客户端上报（见 {@link EncodeRequest}）。</p>
 */
public class EncodeWithShiftFlagC2SPacket implements CustomPacketPayload {

    public static final Type<EncodeWithShiftFlagC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "encode_with_shift"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> PROVIDER_CODEC =
            ByteBufCodecs.optional(ComponentSerialization.TRUSTED_STREAM_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, EncodeWithShiftFlagC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                EncodeRequest req = pkt.request;
                buf.writeBoolean(req.shiftDown());
                PROVIDER_CODEC.encode(buf, Optional.ofNullable(req.uniqueProvider()));
                buf.writeBoolean(req.prefs().ecoAutoUploadEnabled());
                buf.writeEnum(req.prefs().uploadPriority());
            },
            buf -> {
                boolean shiftDown = buf.readBoolean();
                Component provider = PROVIDER_CODEC.decode(buf).orElse(null);
                boolean eco = buf.readBoolean();
                PatternUploadPriority priority = buf.readEnum(PatternUploadPriority.class);
                return new EncodeWithShiftFlagC2SPacket(
                        new EncodeRequest(shiftDown, provider, new UploadPreferences(eco, priority)));
            }
    );

    private final EncodeRequest request;

    public EncodeWithShiftFlagC2SPacket(EncodeRequest request) {
        this.request = request;
    }

    public static void handle(final EncodeWithShiftFlagC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu instanceof PatternEncodingTermMenu menu
                    && menu instanceof IPatternEncodeRequestSync sync) {
                sync.eap$clientSetEncodeRequest(msg.request);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
