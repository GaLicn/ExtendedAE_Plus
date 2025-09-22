package com.extendedae_plus.mixin.ae2.helpers.patternprovider;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.extendedae_plus.ae.items.ChannelCardItem;
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
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicUpgradesMixin implements IUpgradeableObject, InterfaceWirelessLinkBridge {
    @Unique
    private IUpgradeInventory extendedae_plus$upgrades = UpgradeInventories.empty();

    @Unique
    private WirelessSlaveLink extendedae_plus$link;

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
    private void extendedae_plus$onUpgradesChanged() {
        this.host.saveChanges();
        // 读取频道卡，更新无线链接频率
        long channel = 0L;
        for (var stack : this.extendedae_plus$upgrades) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                channel = ChannelCardItem.getChannel(stack);
                break;
            }
        }
        if (extendedae_plus$link == null) {
            var endpoint = new GenericNodeEndpointImpl(() -> host.getBlockEntity(), () -> this.mainNode.getNode());
            extendedae_plus$link = new WirelessSlaveLink(endpoint);
        }
        extendedae_plus$link.setFrequency(channel);
        extendedae_plus$link.updateStatus();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.extendedae_plus$upgrades;
    }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void extendedae_plus$initUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        this.extendedae_plus$upgrades = UpgradeInventories.forMachine(host.getTerminalIcon().getItem(), 1, this::extendedae_plus$onUpgradesChanged);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void extendedae_plus$saveUpgrades(CompoundTag tag, CallbackInfo ci) {
        this.extendedae_plus$upgrades.writeToNBT(tag, "upgrades");
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void extendedae_plus$loadUpgrades(CompoundTag tag, CallbackInfo ci) {
        this.extendedae_plus$upgrades.readFromNBT(tag, "upgrades");
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void extendedae_plus$dropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        for (var stack : this.extendedae_plus$upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void extendedae_plus$clearUpgrades(CallbackInfo ci) {
        this.extendedae_plus$upgrades.clear();
    }

    @Override
    public void extendedae_plus$updateWirelessLink() {
        if (extendedae_plus$link != null) {
            extendedae_plus$link.updateStatus();
        }
    }
}
