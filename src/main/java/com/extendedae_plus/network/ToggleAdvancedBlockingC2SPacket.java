package com.extendedae_plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

import java.util.function.Supplier;

import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.mixin.accessor.PatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.api.AdvancedBlockingHolder;
import com.extendedae_plus.mixin.accessor.PatternProviderLogicAccessor;

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
            var logic = accessor.ext$logic();
            if (logic instanceof AdvancedBlockingHolder holder) {
                boolean current = holder.ext$getAdvancedBlocking();
                boolean next = !current;
                System.out.println("[EPP][C2S] ToggleAdvancedBlockingC2SPacket: player=" + player.getGameProfile().getName()
                        + ", menu=" + menu.getClass().getName()
                        + ", before=" + current + ", after=" + next);
                holder.ext$setAdvancedBlocking(next);
                // 关键：保存持久化，触发 AE2 写入逻辑（writeToNBT），并由菜单 @GuiSync 同步回客户端
                logic.saveChanges();
                System.out.println("[EPP][C2S] logic.saveChanges() called for advancedBlocking=" + next);
                // 直接下发 S2C 强制同步（带供应器标识：维度+方块坐标）
                var host = ((PatternProviderLogicAccessor) logic).ext$host();
                var be = host.getBlockEntity();
                var level = be.getLevel();
                String dimId = level.dimension().location().toString();
                long posLong = be.getBlockPos().asLong();
                ModNetwork.CHANNEL.sendTo(new AdvancedBlockingSyncS2CPacket(dimId, posLong, next), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        });
        ctx.setPacketHandled(true);
    }
}
