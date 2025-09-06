package com.extendedae_plus.network;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.extendedae_plus.menu.locator.CuriosItemLocator;
import com.extendedae_plus.util.WirelessTerminalLocator;
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final OpenCraftFromJeiC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player == null || msg.stack == null) return;

            // 仅支持 AEKey 为可合成的种类
            AEKey what = msg.stack.what();

            // 定位无线终端
            var located = WirelessTerminalLocator.find(player);
            if (located.isEmpty()) return;

            // 若为 Curios 槽位：跳过 AE2 基类的距离/电量前置校验，直接打开数量界面，
            // 让菜单与宿主（WirelessTerminalMenuHost）以及 ae2wtlib 自身处理量子卡跨维/跨距逻辑。
            String curiosSlotId = located.getCuriosSlotId();
            int curiosIndex = located.getCuriosIndex();
            if (curiosSlotId != null && curiosIndex >= 0) {
                int initial = 1;
                CraftAmountMenu.open(player, new CuriosItemLocator(curiosSlotId, curiosIndex), what, initial);
                return;
            }

            // 非 Curios（主手/副手/背包）仍按原先流程做前置校验，保持行为一致。
            if (!(located.stack.getItem() instanceof WirelessTerminalItem wt)) return;

            // 基本前置校验：联网、电量
            IGrid grid = wt.getLinkedGrid(located.stack, player.level(), null);
            if (grid == null) return;
            if (!wt.hasPower(player, 0.5, located.stack)) return;

            // 该 Key 是否可被网络自动合成
            var craftingService = grid.getCraftingService();
            if (!craftingService.isCraftable(what)) return;

            var hand = located.getHand();
            int slot = located.getSlotIndex();
            if (hand != null) {
                int initial = 1;
                CraftAmountMenu.open(player, MenuLocators.forHand(player, hand), what, initial);
            } else if (slot >= 0) {
                // 直接基于物品槽位作为菜单宿主打开数量输入界面
                int initial = 1; // 初始数量，避免依赖具体 Key 的单位定义
                CraftAmountMenu.open(player, MenuLocators.forInventorySlot(slot), what, initial);
            } else {
                // 未知宿主（回退忽略）
            }
        });
    }
}
