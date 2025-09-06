package com.extendedae_plus.network;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.util.ExtendedAELogger;
import com.extendedae_plus.util.PatternProviderDataUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：请求对当前打开的样板供应器执行样板数量缩放（倍增或除法）。
 */
public class ScalePatternsC2SPacket implements CustomPacketPayload {
    public enum Operation {
        MUL2, DIV2, MUL5, DIV5, MUL10, DIV10
    }

    public static final Type<ScalePatternsC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "scale_patterns"));

    public static final StreamCodec<FriendlyByteBuf, ScalePatternsC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeEnum(pkt.op),
            buf -> new ScalePatternsC2SPacket(buf.readEnum(Operation.class))
    );

    private final Operation op;

    public ScalePatternsC2SPacket(Operation op) {
        this.op = op;
    }

    public Operation op() {
        return op;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ScalePatternsC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof PatternProviderMenu menu)) return;

            try {
                var accessor = (PatternProviderMenuAdvancedAccessor) menu;
                PatternProviderLogic logic = accessor.eap$logic();
                if (logic == null) return;

                double factor;
                boolean multiply;
                switch (msg.op) {
                    case MUL2 -> { factor = 2.0; multiply = true; }
                    case DIV2 -> { factor = 2.0; multiply = false; }
                    case MUL5 -> { factor = 5.0; multiply = true; }
                    case DIV5 -> { factor = 5.0; multiply = false; }
                    case MUL10 -> { factor = 10.0; multiply = true; }
                    case DIV10 -> { factor = 10.0; multiply = false; }
                    default -> { return; }
                }

                PatternProviderDataUtil.PatternScalingResult result;
                if (multiply) {
                    result = PatternProviderDataUtil.multiplyPatternAmounts(logic, factor);
                } else {
                    result = PatternProviderDataUtil.dividePatternAmounts(logic, factor);
                }

                logic.saveChanges();

                // 回显结果到玩家
                String summary = String.format("样板缩放(%s x%.0f): 共%d, 成功%d, 失败%d", multiply ? "倍增" : "除法",
                        factor, result.getTotalPatterns(), result.getScaledPatterns(), result.getFailedPatterns());
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("[EAP] " + summary), true);

                for (String err : result.getErrors()) {
                    ExtendedAELogger.LOGGER.debug("[EAP] scale error: {}", err);
                }
            } catch (Throwable t) {
                ExtendedAELogger.LOGGER.error("[EAP] Handle ScalePatternsC2SPacket failed", t);
            }
        });
    }
}
