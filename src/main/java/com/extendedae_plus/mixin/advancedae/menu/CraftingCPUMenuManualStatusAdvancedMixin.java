package com.extendedae_plus.mixin.advancedae.menu;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.extendedae_plus.api.crafting.IManualCraftingState;
import com.extendedae_plus.network.crafting.ManualCraftingStatusS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = CraftingCPUMenu.class, remap = false)
public abstract class CraftingCPUMenuManualStatusAdvancedMixin {
    @Unique
    private AdvCraftingCPU eap$advancedaeSelectedCpu;

    @Unique
    private Map<AEKey, Long> eap$advancedaeLastManualWaitingSnapshot = Collections.emptyMap();

    @Inject(method = "setCPU", at = @At("HEAD"))
    private void eap$trackAdvancedAeCpu(ICraftingCPU cpu, CallbackInfo ci) {
        this.eap$advancedaeSelectedCpu = cpu instanceof AdvCraftingCPU advCpu ? advCpu : null;
        if (this.eap$advancedaeSelectedCpu == null) {
            this.eap$advancedaeLastManualWaitingSnapshot = Collections.emptyMap();
        }
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void eap$syncAdvancedAeManualWaitingStatus(CallbackInfo ci) {
        CraftingCPUMenu self = (CraftingCPUMenu) (Object) this;
        if (self.isClientSide() || !(self.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (this.eap$advancedaeSelectedCpu == null) {
            return;
        }

        Map<AEKey, Long> snapshot = Collections.emptyMap();
        if (this.eap$advancedaeSelectedCpu.craftingLogic instanceof IManualCraftingState manualState) {
            snapshot = manualState.eap$getManualWaitingSnapshot();
        }

        if (snapshot.equals(this.eap$advancedaeLastManualWaitingSnapshot)) {
            return;
        }

        this.eap$advancedaeLastManualWaitingSnapshot = new LinkedHashMap<>(snapshot);
        serverPlayer.connection.send(new ManualCraftingStatusS2CPacket(self.containerId, snapshot));
    }
}
