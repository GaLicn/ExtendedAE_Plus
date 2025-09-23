package com.extendedae_plus.mixin.ae2.helpers.patternprovider;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.ae.items.ChannelCardItem;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.wireless.WirelessSlaveLink;
import com.extendedae_plus.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.bridge.InterfaceWirelessLinkBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 为 PatternProviderLogic 注入升级槽，实现 IUpgradeableObject。
 * 仅负责升级槽的持久化/掉落/清空与初始化，不改变原有逻辑。
 */
@Mixin(value = PatternProviderLogic.class, priority = 2000, remap = false)
public abstract class PatternProviderLogicUpgradesMixin implements IUpgradeableObject, InterfaceWirelessLinkBridge {
    @Unique
    private IUpgradeInventory eap$upgrades = UpgradeInventories.empty();

    @Unique
    private WirelessSlaveLink eap$link;

    @Unique
    private long eap$lastChannel = -1;

    @Unique
    private boolean eap$clientConnected = false;

    @Unique
    private boolean eap$hasInitialized = false;

    @Unique
    private int eap$delayedInitTicks = 0;

    @Final
    @Shadow
    private PatternProviderLogicHost host;

    @Final
    @Shadow
    private IManagedGridNode mainNode;

    @Final
    @Shadow
    private IActionSource actionSource;

    @Unique
    private void eap$onUpgradesChanged() {
        this.host.saveChanges();
        // 升级变更，重置并尝试初始化
        eap$lastChannel = -1;
        eap$hasInitialized = false;
        eap$initializeChannelLink();
    }

    

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        // 只有在应该启用升级卡槽时才初始化
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }
        
        this.eap$upgrades = UpgradeInventories.forMachine(host.getTerminalIcon().getItem(), 1, this::eap$onUpgradesChanged);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$saveUpgrades(CompoundTag tag, CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }
        this.eap$upgrades.writeToNBT(tag, "upgrades");
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$loadUpgrades(CompoundTag tag, CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }
        this.eap$upgrades.readFromNBT(tag, "upgrades");
        // 从 NBT 加载后，重置并尝试初始化（可能刚进入世界）
        eap$lastChannel = -1;
        eap$hasInitialized = false;
        eap$initializeChannelLink();
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$dropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }
        for (var stack : this.eap$upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$clearUpgrades(CallbackInfo ci) {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return;
        }
        this.eap$upgrades.clear();
    }

    @Override
    public void eap$updateWirelessLink() {
        if (eap$link != null) {
            eap$link.updateStatus();
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        if (!UpgradeSlotCompat.shouldEnableUpgradeSlots()) {
            return UpgradeInventories.empty();
        }
        return this.eap$upgrades;
    }

    // ===== 频道卡初始化与延迟重试（服务端） =====
    @Unique
    public void eap$initializeChannelLink() {
        // 客户端早退
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            return;
        }

        // 避免重复初始化
        if (eap$hasInitialized) {
            return;
        }

        // 等待网格完成引导
        if (!mainNode.hasGridBooted()) {
            // 安排短延迟，等待后续 tick 再试
            eap$delayedInitTicks = Math.max(eap$delayedInitTicks, 5);
            try {
                mainNode.ifPresent((grid, node) -> {
                    try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
            return;
        }

        try {
            long channel = 0L;
            boolean found = false;
            for (ItemStack stack : this.eap$upgrades) {
                if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                    channel = ChannelCardItem.getChannel(stack);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // 无频道卡：断开并视为初始化完成
                if (eap$link != null) {
                    eap$link.setFrequency(0L);
                    eap$link.updateStatus();
                }
                eap$hasInitialized = true;
                return;
            }

            if (eap$link == null) {
                var endpoint = new GenericNodeEndpointImpl(() -> host.getBlockEntity(), () -> this.mainNode.getNode());
                eap$link = new WirelessSlaveLink(endpoint);
            }

            eap$link.setFrequency(channel);
            eap$link.updateStatus();

            if (eap$link.isConnected()) {
                eap$hasInitialized = true;
            } else {
                eap$hasInitialized = false;
                eap$delayedInitTicks = Math.max(eap$delayedInitTicks, 5);
                try {
                    mainNode.ifPresent((grid, node) -> {
                        try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void eap$setClientWirelessState(boolean connected) {
        eap$clientConnected = connected;
    }

    @Override
    public boolean eap$isWirelessConnected() {
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            return eap$clientConnected;
        } else {
            return eap$link != null && eap$link.isConnected();
        }
    }

    @Override
    public boolean eap$hasTickInitialized() {
        return eap$hasInitialized;
    }

    @Override
    public void eap$setTickInitialized(boolean initialized) {
        eap$hasInitialized = initialized;
    }

    @Override
    public void eap$handleDelayedInit() {
        // 仅服务端
        if (host.getBlockEntity() != null && host.getBlockEntity().getLevel() != null && host.getBlockEntity().getLevel().isClientSide) {
            return;
        }
        if (!eap$hasInitialized) {
            if (!mainNode.hasGridBooted()) {
                if (eap$delayedInitTicks > 0) {
                    eap$delayedInitTicks--;
                }
                if (eap$delayedInitTicks == 0) {
                    eap$delayedInitTicks = 5;
                    try {
                        mainNode.ifPresent((grid, node) -> {
                            try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }
            } else {
                eap$initializeChannelLink();
            }
        }
    }

    // 当主节点状态变化（例如 GRID_BOOT 完成）时，安排一次延迟初始化并唤醒设备
    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void eap$onMainNodeStateChangedTail(CallbackInfo ci) {
        eap$lastChannel = -1;
        eap$hasInitialized = false;
        eap$delayedInitTicks = 10;
        try {
            mainNode.ifPresent((grid, node) -> {
                try { grid.getTickManager().wakeDevice(node); } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }
}
