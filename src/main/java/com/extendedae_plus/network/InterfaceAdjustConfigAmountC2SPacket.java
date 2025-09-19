package com.extendedae_plus.network;

import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.SlotSemantics;
import appeng.api.stacks.GenericStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：调整 ME 接口配置槽位(标记物品)的数量。
 * 支持按因子倍增或整除，且保持最小值为 1。
 */
public class InterfaceAdjustConfigAmountC2SPacket {
    private final int slotIndex; // 配置槽位索引（ConfigInventory 索引）
    private final boolean divide; // true 表示做除法，否则做乘法
    private final int factor; // 因子：2/5/10

    public InterfaceAdjustConfigAmountC2SPacket(int slotIndex, boolean divide, int factor) {
        this.slotIndex = slotIndex;
        this.divide = divide;
        this.factor = factor;
    }

    public static void encode(InterfaceAdjustConfigAmountC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slotIndex);
        buf.writeBoolean(msg.divide);
        buf.writeVarInt(msg.factor);
    }

    public static InterfaceAdjustConfigAmountC2SPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        boolean div = buf.readBoolean();
        int factor = buf.readVarInt();
        return new InterfaceAdjustConfigAmountC2SPacket(slot, div, factor);
        
    }

    public static void handle(InterfaceAdjustConfigAmountC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // 支持 AE2 原版接口和 ExtendedAE 扩展接口
            InterfaceMenu menu = null;
            com.glodblock.github.extendedae.container.ContainerExInterface exMenu = null;
            if (player.containerMenu instanceof InterfaceMenu im) {
                menu = im;
            } else if (player.containerMenu instanceof com.glodblock.github.extendedae.container.ContainerExInterface cem) {
                exMenu = cem;
            } else {
                return;
            }

            try {
                var logic = (menu != null ? menu.getHost() : exMenu.getHost()).getInterfaceLogic();
                var config = logic.getConfig();
                if (msg.slotIndex == -1) {
                    // 对所有配置槽统一生效（不依赖槽位语义，直接按 config.size() 遍历）
                    int size = config.size();
                    for (int idx = 0; idx < size; idx++) {
                        var st = config.getStack(idx);
                        if (st == null) continue;

                        long current = st.amount();
                        int factor = Math.max(1, msg.factor);
                        long next;
                        if (msg.divide) {
                            if (factor <= 1) continue;
                            if (current % factor != 0) continue;
                            next = current / factor;
                            if (next < 1) next = 1;
                        } else {
                            if (factor <= 1) continue;
                            next = current * factor;
                            if (next < 1) next = 1;
                        }

                        GenericStack newStack = new GenericStack(st.what(), next);
                        config.setStack(idx, newStack);
                    }
                } else {
                    var stack = config.getStack(msg.slotIndex);
                    if (stack == null) return; // 槽位无标记

                    long current = stack.amount();
                    int factor = Math.max(1, msg.factor);
                    long next;
                    if (msg.divide) {
                        // 只能整除，且至少为 1
                        if (factor <= 1) return;
                        if (current % factor != 0) return; // 不能整除则跳过
                        next = current / factor;
                        if (next < 1) next = 1;
                    } else {
                        // 倍增，至少为 1
                        if (factor <= 1) return;
                        next = current * factor;
                        if (next < 1) next = 1;
                    }

                    // 应用
                    GenericStack newStack = new GenericStack(stack.what(), next);
                    config.setStack(msg.slotIndex, newStack);
                }
                // 不需要显式保存：InterfaceLogic.config 的变更监听器会触发 host.saveChanges() 与计划更新
            } catch (Throwable ignored) {}
        });
        ctx.setPacketHandled(true);
    }
}
