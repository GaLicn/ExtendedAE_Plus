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
    private IUpgradeInventory eap$upgrades = UpgradeInventories.empty();

    @Unique
    private WirelessSlaveLink eap$link;

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
        // 读取频道卡，更新无线链接频率
        long channel = 0L;
        for (var stack : this.eap$upgrades) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                channel = ChannelCardItem.getChannel(stack);
                break;
            }
        }
        if (eap$link == null) {
            var endpoint = new GenericNodeEndpointImpl(() -> host.getBlockEntity(), () -> this.mainNode.getNode());
            eap$link = new WirelessSlaveLink(endpoint);
        }
        eap$link.setFrequency(channel);
        eap$link.updateStatus();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.eap$upgrades;
    }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void eap$initUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize, CallbackInfo ci) {
        this.eap$upgrades = UpgradeInventories.forMachine(host.getTerminalIcon().getItem(), 1, this::eap$onUpgradesChanged);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void eap$saveUpgrades(CompoundTag tag, CallbackInfo ci) {
        this.eap$upgrades.writeToNBT(tag, "upgrades");
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void eap$loadUpgrades(CompoundTag tag, CallbackInfo ci) {
        this.eap$upgrades.readFromNBT(tag, "upgrades");
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void eap$dropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        for (var stack : this.eap$upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void eap$clearUpgrades(CallbackInfo ci) {
        this.eap$upgrades.clear();
    }

    @Override
    public void eap$updateWirelessLink() {
        if (eap$link != null) {
            eap$link.updateStatus();
        }
    }
}
