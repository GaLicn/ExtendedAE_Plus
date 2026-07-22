package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.extendedae_plus.api.crafting.IManualCraftingState;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.crafting.ManualCraftingStatusS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = CraftingCPUMenu.class, priority = 1100, remap = false)
public abstract class CraftingCPUMenuManualStatusMixin {
    @Unique
    private ICraftingCPU eap$selectedCpu;

    @Unique
    private Map<AEKey, Long> eap$lastManualWaitingSnapshot = Collections.emptyMap();

    @Inject(
            method = "setCPU(Lappeng/api/networking/crafting/ICraftingCPU;)V",
            at = @At("HEAD"))
    private void eap$trackSelectedCpu(ICraftingCPU cpu, CallbackInfo ci) {
        if (this.eap$selectedCpu != cpu) {
            this.eap$selectedCpu = cpu;
            this.eap$lastManualWaitingSnapshot = null;
        }
    }

    @Inject(method = "broadcastChanges()V", at = @At("TAIL"), remap = true)
    private void eap$syncManualWaitingStatus(CallbackInfo ci) {
        CraftingCPUMenu self = (CraftingCPUMenu) (Object) this;
        if (self.isClientSide() || !(self.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Map<AEKey, Long> snapshot = Collections.emptyMap();
        if (this.eap$selectedCpu instanceof CraftingCPUCluster selectedCpu) {
            if (selectedCpu.craftingLogic instanceof IManualCraftingState manualState) {
                snapshot = manualState.eap$getManualWaitingSnapshot();
            }
        } else if (this.eap$selectedCpu != null) {
            return;
        }

        if (snapshot.equals(this.eap$lastManualWaitingSnapshot)) {
            return;
        }

        this.eap$lastManualWaitingSnapshot = new LinkedHashMap<>(snapshot);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                new ManualCraftingStatusS2CPacket(self.containerId, snapshot));
    }
}
