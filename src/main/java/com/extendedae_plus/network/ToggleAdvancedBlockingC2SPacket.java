package com.extendedae_plus.network;

import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.AdvancedBlockingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAdvancedAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：切换高级阻挡模式。
 * 不含额外负载，直接基于玩家当前打开的 PatternProviderMenu 进行切换。
 */
public class ToggleAdvancedBlockingC2SPacket {
    public ToggleAdvancedBlockingC2SPacket() {}

    public static void encode(ToggleAdvancedBlockingC2SPacket msg, FriendlyByteBuf buf) {}

    public static ToggleAdvancedBlockingC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleAdvancedBlockingC2SPacket();
    }

    public static void handle(ToggleAdvancedBlockingC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof PatternProviderMenu menu)) return;

            // 通过 accessor 获取逻辑与当前状态
            var accessor = (PatternProviderMenuAdvancedAccessor) menu;
            var logic = accessor.eap$logic();
            if (logic instanceof AdvancedBlockingHolder holder) {
                boolean current = holder.eap$getAdvancedBlocking();
                boolean next = !current;
                holder.eap$setAdvancedBlocking(next);
                // 保存并触发 AE2 的菜单 @GuiSync 广播到所有观看该菜单的玩家
                logic.saveChanges();
            }
        });
        ctx.setPacketHandled(true);
    }
}
