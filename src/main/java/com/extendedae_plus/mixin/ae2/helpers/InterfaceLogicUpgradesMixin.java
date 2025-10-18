package com.extendedae_plus.mixin.ae2.helpers;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import com.extendedae_plus.compat.UpgradeSlotCompat;
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
    protected void onUpgradesChanged() {}

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
        try {
            // 安全检查
            if (this.upgrades == null || gridNode == null || host == null || is == null) {
                return;
            }
            
            int currentSlots = this.upgrades.size();
            
            // 检查Applied Flux是否已经修改了升级槽
            if (UpgradeSlotCompat.isAppfluxPresent()) {
                if (currentSlots >= 3) {
                    // Applied Flux已经增加了足够的升级槽，跳过修改
                    return;
                } else if (currentSlots == 2) {
                    // Applied Flux增加到2个，我们再增加1个到3个
                    this.upgrades = UpgradeInventories.forMachine(is, 3, this::onUpgradesChanged);
                } else if (currentSlots == 1) {
                    // Applied Flux存在但未生效，直接增加到3个
                    this.upgrades = UpgradeInventories.forMachine(is, 3, this::onUpgradesChanged);
                }
            } else {
                if (currentSlots == 1) {
                    // Applied Flux不存在，将升级槽从1个增加到2个
                    this.upgrades = UpgradeInventories.forMachine(is, 2, this::onUpgradesChanged);
                }
            }
        } catch (Exception e) {
            // 发生异常时不修改升级槽，确保不会崩溃
        }
    }
}
