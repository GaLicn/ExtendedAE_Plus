package com.extendedae_plus.mixin;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import com.glodblock.github.extendedae.container.ContainerWirelessExPAT;
import com.glodblock.github.extendedae.common.me.itemhost.HostWirelessExPAT;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 为无线样板访问终端容器注册通用动作（CGenericPacket 分发）
 */
@Mixin(ContainerWirelessExPAT.class)
public abstract class ContainerWirelessExPatternTerminalMixin implements IActionHolder {

    @Unique
    private final Map<String, Consumer<Paras>> actions = createHolder();

    @Unique
    private Player epp$player;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(int id, net.minecraft.world.entity.player.Inventory playerInventory, HostWirelessExPAT host, CallbackInfo ci) {
        this.epp$player = playerInventory.player;
        // 注册上传动作：参数顺序必须与客户端 CGenericPacket 保持一致
        this.actions.put("upload", p -> {
            try {
                Object o0 = p.get(0);
                Object o1 = p.get(1);
                int playerSlotIndex = (o0 instanceof Number) ? ((Number) o0).intValue() : Integer.parseInt(String.valueOf(o0));
                long providerId = (o1 instanceof Number) ? ((Number) o1).longValue() : Long.parseLong(String.valueOf(o1));
                var sp = (ServerPlayer) this.epp$player;
                System.out.println("[EAE+][Server][Wireless] upload: slot=" + playerSlotIndex + ", provider=" + providerId);
                ExtendedAEPatternUploadUtil.uploadPatternToProvider(sp, playerSlotIndex, providerId);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        System.out.println("[EAE+][Server] WirelessExPAT actions registered: " + this.actions.keySet());
    }

    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.actions;
    }
}
