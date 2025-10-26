package com.extendedae_plus.mixin.ae2WTlib;

import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import com.glodblock.github.extendedae.xmod.wt.ContainerUWirelessExPAT;
import com.glodblock.github.extendedae.xmod.wt.HostUWirelessExPAT;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 为通用无线样板访问终端（AE2WTlib 集成）容器注册通用动作（CGenericPacket 分发）
 */
@Pseudo
@Mixin(ContainerUWirelessExPAT.class)
public abstract class ContainerUWirelessExPatternTerminalMixin implements IActionHolder {

    @Unique
    private final Map<String, Consumer<Paras>> eap$actions = createHolder();

    @Unique
    private Player eap$player;

    // 明确目标构造签名：<init>(int, Inventory, HostUWirelessExPAT)
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lcom/glodblock/github/extendedae/xmod/wt/HostUWirelessExPAT;)V", at = @At("TAIL"), remap = false)
    private void init(int id, net.minecraft.world.entity.player.Inventory playerInventory, HostUWirelessExPAT host, CallbackInfo ci) {
        this.eap$player = playerInventory.player;
        // 注册上传动作：参数顺序必须与客户端 CGenericPacket 保持一致
        this.eap$actions.put("upload", p -> {
            try {
                Object o0 = p.get(0);
                Object o1 = p.get(1);
                int playerSlotIndex = (o0 instanceof Number) ? ((Number) o0).intValue() : Integer.parseInt(String.valueOf(o0));
                long providerId = (o1 instanceof Number) ? ((Number) o1).longValue() : Long.parseLong(String.valueOf(o1));
                var sp = (ServerPlayer) this.eap$player;
                ProviderUploadUtil.uploadPatternToProvider(sp, playerSlotIndex, providerId);
            } catch (Throwable t) {
            }
        });
    }


    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.eap$actions;
    }
}
