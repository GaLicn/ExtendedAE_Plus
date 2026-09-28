package com.extendedae_plus.mixin.extendedae.container;

import appeng.api.storage.IPatternAccessTermMenuHost;
import appeng.menu.guisync.GuiSync;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ContainerExPatternTerminal.class, remap = false)
public abstract class ContainerExPatternTerminalMixin {

    @GuiSync(25564)
    @Unique
    public boolean eap$hidePatternSlots = false;

    @Unique
    public boolean isHidePatternSlots() {
        return this.eap$hidePatternSlots;
    }

    @Unique
    public void setHidePatternSlots(boolean hide) {
        this.eap$hidePatternSlots = hide;
    }

    @Unique
    public void toggleHidePatternSlots() {
        this.eap$hidePatternSlots = !this.eap$hidePatternSlots;
    }

    @Unique
    private Player epp$player;

    @Unique
    private static final Logger EAP_LOGGER = LogManager.getLogger("ExtendedAE_Plus");

    @Inject(method = "<init>*", at = @At("TAIL"), remap = false)
    private void init(int id, net.minecraft.world.entity.player.Inventory playerInventory, IPatternAccessTermMenuHost host, CallbackInfo ci) {
        this.epp$player = playerInventory.player;
        // glodium IActionHolder 已移除，此处逻辑改为使用专用网络包的途径，详见 network 包。
    }
}