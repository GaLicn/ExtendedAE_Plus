package com.extendedae_plus.mixin.ae2.network;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.core.sync.packets.PatternAccessTerminalPacket;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.menu.implementations.PatternAccessTermMenu$ContainerTracker", remap = false)
public abstract class PatternAccessTermContainerTrackerMixin {
    @Shadow @Final private long serverId;
    @Shadow @Final private long sortBy;
    @Shadow @Final private PatternContainerGroup group;
    @Mutable @Shadow @Final private InternalInventory client;
    @Shadow @Final private InternalInventory server;

    @Inject(method = "createUpdatePacket", at = @At("HEAD"), cancellable = true, remap = false)
    private void eap$resizeClientSnapshot(CallbackInfoReturnable<PatternAccessTerminalPacket> cir) {
        if (this.client.size() == this.server.size()) {
            return;
        }

        // 外部库存尺寸变化时重建快照，并发送完整包让客户端同步增减槽位。
        this.client = new AppEngInternalInventory(this.server.size());
        Int2ObjectArrayMap<ItemStack> slots = new Int2ObjectArrayMap<>(this.server.size());
        for (int slot = 0; slot < this.server.size(); slot++) {
            ItemStack stack = this.server.getStackInSlot(slot);
            this.client.setItemDirect(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            if (!stack.isEmpty()) {
                slots.put(slot, stack);
            }
        }
        cir.setReturnValue(PatternAccessTerminalPacket.fullUpdate(
                this.serverId,
                this.server.size(),
                this.sortBy,
                this.group,
                slots));
    }
}
