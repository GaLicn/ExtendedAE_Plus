package com.extendedae_plus.mixin;

import appeng.menu.guisync.GuiSync;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import appeng.api.util.IConfigurableObject;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;

@Mixin(ContainerExPatternTerminal.class)
public abstract class ContainerExPatternTerminalMixin implements IActionHolder {

    @GuiSync(11452)
    @Unique
    public boolean hidePatternSlots = false;

    @Unique
    public boolean isHidePatternSlots() {
        return this.hidePatternSlots;
    }

    @Unique
    public void setHidePatternSlots(boolean hide) {
        this.hidePatternSlots = hide;
    }

    @Unique
    public void toggleHidePatternSlots() {
        this.hidePatternSlots = !this.hidePatternSlots;
    }

    @Unique
    private final Map<String, Consumer<Paras>> actions = createHolder();

    @Unique
    private Player epp$player;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(int id, net.minecraft.world.entity.player.Inventory playerInventory, IConfigurableObject host, CallbackInfo ci) {
        this.epp$player = playerInventory.player;
        // 注册上传动作：参数顺序必须与客户端 CGenericPacket 保持一致
        this.actions.put("upload", p -> {
            try {
                int playerSlotIndex = p.get(0);
                long providerId = p.get(1);
                var sp = (ServerPlayer) this.epp$player;
                System.out.println("[EAE+][Server] upload: slot=" + playerSlotIndex + ", provider=" + providerId);
                ExtendedAEPatternUploadUtil.uploadPatternToProvider(sp, playerSlotIndex, providerId);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        System.out.println("[EAE+][Server] ExPatternTerminal actions registered: " + this.actions.keySet());
    }

    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.actions;
    }
}