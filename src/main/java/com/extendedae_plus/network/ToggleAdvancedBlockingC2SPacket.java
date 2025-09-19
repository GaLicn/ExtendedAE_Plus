package com.extendedae_plus.network;

import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.AdvancedBlockingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.ExtendedAEPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;

/**
 * C2S：切换高级阻挡模式。
 * 不含额外负载，直接基于玩家当前打开的 PatternProviderMenu 进行切换。
 */
public class ToggleAdvancedBlockingC2SPacket implements CustomPacketPayload {
    public static final Type<ToggleAdvancedBlockingC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "toggle_adv_blocking"));

    public static final ToggleAdvancedBlockingC2SPacket INSTANCE = new ToggleAdvancedBlockingC2SPacket();

    public static final StreamCodec<FriendlyByteBuf, ToggleAdvancedBlockingC2SPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    private ToggleAdvancedBlockingC2SPacket() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ToggleAdvancedBlockingC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof PatternProviderMenu menu)) return;

            var accessor = (PatternProviderMenuAdvancedAccessor) menu;
            var logic = accessor.eap$logic();
            if (logic instanceof AdvancedBlockingHolder holder) {
                boolean next = !holder.eap$getAdvancedBlocking();
                holder.eap$setAdvancedBlocking(next);
                // 自动开启原版阻挡
                logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
                logic.saveChanges();
            }
        });
    }
}
