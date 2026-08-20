package com.extendedae_plus.mixin.ae2.network;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.core.sync.packets.PatternAccessTerminalPacket;
import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.menu.implementations.PatternAccessTermMenu$ContainerTracker", remap = false)
public abstract class PatternAccessTermContainerTrackerMixin {
    @Shadow @Final private PatternContainer container;
    @Shadow @Final private long serverId;
    @Shadow @Final private long sortBy;
    @Shadow @Final private PatternContainerGroup group;
    @Shadow @Final private InternalInventory client;
    @Shadow @Final private InternalInventory server;

    @Shadow
    private IntList detectChangedSlots() {
        throw new AssertionError();
    }

    @Unique
    private int eap$lastAccessibleSlots = -1;

    @Inject(method = "createFullPacket", at = @At("HEAD"), cancellable = true, remap = false)
    private void eap$createAccessibleFullPacket(CallbackInfoReturnable<PatternAccessTerminalPacket> cir) {
        int accessibleSlots = eap$getAccessibleSlots();
        eap$lastAccessibleSlots = accessibleSlots;
        cir.setReturnValue(eap$createFullPacket(accessibleSlots));
    }

    @Inject(method = "createUpdatePacket", at = @At("HEAD"), cancellable = true, remap = false)
    private void eap$refreshWhenAccessibleSlotsChange(CallbackInfoReturnable<PatternAccessTerminalPacket> cir) {
        int accessibleSlots = eap$getAccessibleSlots();
        if (eap$lastAccessibleSlots >= 0 && eap$lastAccessibleSlots != accessibleSlots) {
            // 扩容卡变化不一定修改样板内容，因此主动发送完整包刷新终端容量。
            eap$lastAccessibleSlots = accessibleSlots;
            cir.setReturnValue(eap$createFullPacket(accessibleSlots));
            return;
        }

        IntList changedSlots = detectChangedSlots();
        if (changedSlots == null) {
            cir.setReturnValue(null);
            return;
        }

        Int2ObjectMap<ItemStack> slots = new Int2ObjectArrayMap<>(changedSlots.size());
        for (int index = 0; index < changedSlots.size(); index++) {
            int slot = changedSlots.getInt(index);
            ItemStack stack = server.getStackInSlot(slot);
            // 无论槽位是否可见，都同步 Tracker 的快照，避免锁定槽位重复触发更新。
            client.setItemDirect(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            if (slot < accessibleSlots) {
                slots.put(slot, stack);
            }
        }
        cir.setReturnValue(PatternAccessTerminalPacket.incrementalUpdate(serverId, slots));
    }

    @Unique
    private int eap$getAccessibleSlots() {
        return Math.max(0, Math.min(server.size(),
                UpgradeSlotCompat.getAccessiblePatternSlotCount(container)));
    }

    @Unique
    private PatternAccessTerminalPacket eap$createFullPacket(int accessibleSlots) {
        Int2ObjectMap<ItemStack> slots = new Int2ObjectArrayMap<>(accessibleSlots);
        for (int index = 0; index < accessibleSlots; index++) {
            ItemStack stack = server.getStackInSlot(index);
            if (!stack.isEmpty()) {
                slots.put(index, stack);
            }
        }
        return PatternAccessTerminalPacket.fullUpdate(
                serverId,
                accessibleSlots,
                sortBy,
                group,
                slots);
    }
}
