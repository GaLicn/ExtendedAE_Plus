package com.extendedae_plus.network;

import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.api.SmartDoublingHolder;
import com.extendedae_plus.mixin.advancedae.accessor.AdvPatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAdvancedAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;

/**
 * C2S：切换智能翻倍启用状态。
 * 不含额外负载，基于玩家当前打开的 PatternProviderMenu 进行切换。
 */
public class ToggleSmartDoublingC2SPacket implements CustomPacketPayload {
    public static final Type<ToggleSmartDoublingC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "toggle_smart_doubling"));

    public static final ToggleSmartDoublingC2SPacket INSTANCE = new ToggleSmartDoublingC2SPacket();

    public static final StreamCodec<FriendlyByteBuf, ToggleSmartDoublingC2SPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    private ToggleSmartDoublingC2SPacket() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ToggleSmartDoublingC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var containerMenu = player.containerMenu;
            if (containerMenu instanceof PatternProviderMenu menu) {
                var accessor = (PatternProviderMenuAdvancedAccessor) menu;
                var logic = accessor.eap$logic();
                if (logic instanceof SmartDoublingHolder holder) {
                    boolean current = holder.eap$getSmartDoubling();
                    boolean next = !current;
                    holder.eap$setSmartDoubling(next);
                    logic.saveChanges();
                }
            }else if (containerMenu instanceof AdvPatternProviderMenu menu){
                var accessor = (AdvPatternProviderMenuAdvancedAccessor) menu;
                var logic = accessor.eap$logic();
                if (logic instanceof SmartDoublingHolder holder) {
                    boolean current = holder.eap$getSmartDoubling();
                    boolean next = !current;
                    holder.eap$setSmartDoubling(next);
                    logic.saveChanges();
                }
            }
        });
    }
}
