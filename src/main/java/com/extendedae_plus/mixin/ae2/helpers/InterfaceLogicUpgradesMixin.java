package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.util.ModCheckUtils;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为ME接口增加升级槽数量的Mixin
 * 兼容Applied Flux模组，避免冲突
 */
@Mixin(value = InterfaceLogic.class, remap = false, priority = 1100)
public class InterfaceLogicUpgradesMixin {

    @Final
    @Mutable
    @Shadow
    private IUpgradeInventory upgrades;

    @Shadow
    private void onUpgradesChanged() {}

    /**
     * 在InterfaceLogic构造函数末尾注入，增加升级槽数量
     * 使用优先级1100确保在Applied Flux之后执行，但不会过度干扰其他组件
     */
    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/InterfaceLogicHost;Lnet/minecraft/world/item/Item;I)V",
            at = @At("TAIL"),
            require = 0  // 设置为可选注入，避免在某些情况下导致崩溃
    )
    private void expandInterfaceUpgrades(IManagedGridNode gridNode, InterfaceLogicHost host, Item is, int slots, CallbackInfo ci) {
        int currentSlots = this.upgrades.size();

        // 默认 1 个，有 Applied Flux 时希望至少 3 个，否则至少 2 个
        int desiredSlots = ModCheckUtils.isAppfluxLoading() ? 3 : 2;

        // 仅当当前槽位小于期望值时才扩容；如果已 >= desiredSlots 则不改动（避免降级或叠加）
        if (currentSlots < desiredSlots) {
            this.upgrades = UpgradeInventories.forMachine(is, desiredSlots, this::onUpgradesChanged);
        }
    }
}
