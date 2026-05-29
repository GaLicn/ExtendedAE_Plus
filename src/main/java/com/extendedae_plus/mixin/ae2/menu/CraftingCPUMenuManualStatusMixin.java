package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.extendedae_plus.api.crafting.IManualCraftingState;
import com.extendedae_plus.network.crafting.ManualCraftingStatusS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = CraftingCPUMenu.class, remap = false)
public abstract class CraftingCPUMenuManualStatusMixin {
    @Shadow
    private CraftingCPUCluster cpu;

    @Unique
    private Map<AEKey, Long> eap$lastManualWaitingSnapshot = Collections.emptyMap();

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void eap$syncManualWaitingStatus(CallbackInfo ci) {
        CraftingCPUMenu self = (CraftingCPUMenu) (Object) this;
        if (self.isClientSide() || !(self.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Map<AEKey, Long> snapshot = Collections.emptyMap();
        if (this.cpu != null && this.cpu.craftingLogic instanceof IManualCraftingState manualState) {
            snapshot = manualState.eap$getManualWaitingSnapshot();
        }

        if (snapshot.equals(this.eap$lastManualWaitingSnapshot)) {
            return;
        }

        this.eap$lastManualWaitingSnapshot = new LinkedHashMap<>(snapshot);
        serverPlayer.connection.send(new ManualCraftingStatusS2CPacket(self.containerId, snapshot));
    }
}
