package com.extendedae_plus.network;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：从 JEI 中键点击请求打开 AE 的下单界面。
 * 负载为一个 GenericStack（物品或流体）。
 */
public class OpenCraftFromJeiC2SPacket implements CustomPacketPayload {
    public static final Type<OpenCraftFromJeiC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(com.extendedae_plus.ExtendedAEPlus.MODID, "open_craft_from_jei"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftFromJeiC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> GenericStack.writeBuffer(pkt.stack, buf),
            buf -> new OpenCraftFromJeiC2SPacket(GenericStack.readBuffer(buf))
    );
    private final GenericStack stack;

    public OpenCraftFromJeiC2SPacket(GenericStack stack) {
        this.stack = stack;
    }

    public static void handle(final OpenCraftFromJeiC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // 仅支持 AEKey 为可合成的种类
            AEKey what = msg.stack.what();

            // 定位无线终端
            var located = WirelessTerminalLocator.find(player);

            var grid = WirelessTerminalLocator.getConnectedGrid(player, located);
            var craftingService = grid.getCraftingService();

            var locator = located.createMenuLocator(player);
            if (locator != null) {
                CraftAmountMenu.open(player, locator, what, 1);
            } else {
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
