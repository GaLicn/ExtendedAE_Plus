package com.extendedae_plus.network.provider;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.advancedBlocking.IAdvancedBlocking;
import com.extendedae_plus.mixin.advancedae.accessor.AdvPatternProviderMenuAdvancedAccessor;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;

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

            var containerMenu = player.containerMenu;
            if (containerMenu instanceof PatternProviderMenu menu) {
                var accessor = (PatternProviderMenuAccessor) menu;
                var logic = accessor.eap$logic();
                if (logic instanceof IAdvancedBlocking holder) {
                    boolean current = holder.eap$getAdvancedBlocking();
                    boolean next = !current;
                    holder.eap$setAdvancedBlocking(next);
                    // 自动开启原版阻挡
                    logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
                    // 保存并触发 AE2 的菜单 @GuiSync 广播到所有观看该菜单的玩家
                    logic.saveChanges();
                }
            }else if (containerMenu instanceof AdvPatternProviderMenu menu){
                var accessor = (AdvPatternProviderMenuAdvancedAccessor) menu;
                var logic = accessor.eap$logic();
                if (logic instanceof IAdvancedBlocking holder) {
                    boolean current = holder.eap$getAdvancedBlocking();
                    boolean next = !current;
                    holder.eap$setAdvancedBlocking(next);
                    // 自动开启原版阻挡
                    logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
                    // 保存并触发 AE2 的菜单 @GuiSync 广播到所有观看该菜单的玩家
                    logic.saveChanges();
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
