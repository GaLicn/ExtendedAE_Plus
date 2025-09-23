package com.extendedae_plus.mixin.ae2;

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
            // 添加更多的安全检查
            if (this.upgrades == null) {
                System.err.println("[ExtendedAE_Plus] InterfaceLogic upgrades为null，跳过升级槽扩展");
                return;
            }
            
            if (gridNode == null || host == null || is == null) {
                System.err.println("[ExtendedAE_Plus] InterfaceLogic构造参数为null，跳过升级槽扩展");
                return;
            }
            
            System.out.println("[ExtendedAE_Plus] InterfaceLogic升级槽扩展开始");
            
            int currentSlots = this.upgrades.size();
            System.out.println("[ExtendedAE_Plus] 当前升级槽数量: " + currentSlots);
            
            // 检查Applied Flux是否已经修改了升级槽
            if (UpgradeSlotCompat.isAppfluxPresent()) {
                System.out.println("[ExtendedAE_Plus] 检测到Applied Flux存在");
                
                if (currentSlots >= 3) {
                    // Applied Flux已经增加了升级槽到3个或更多，我们不需要再修改
                    System.out.println("[ExtendedAE_Plus] Applied Flux已经增加了足够的升级槽，跳过修改");
                    return;
                } else if (currentSlots == 2) {
                    // Applied Flux增加到2个，我们再增加1个到3个
                    System.out.println("[ExtendedAE_Plus] Applied Flux增加到2个槽，我们再增加到3个");
                    this.upgrades = UpgradeInventories.forMachine(is, 3, this::onUpgradesChanged);
                } else if (currentSlots == 1) {
                    // Applied Flux可能还没有生效，我们直接增加到3个
                    System.out.println("[ExtendedAE_Plus] Applied Flux存在但未生效，直接增加到3个槽");
                    this.upgrades = UpgradeInventories.forMachine(is, 3, this::onUpgradesChanged);
                } else {
                    System.out.println("[ExtendedAE_Plus] Applied Flux存在，当前槽数异常: " + currentSlots + "，跳过修改");
                    return;
                }
            } else {
                System.out.println("[ExtendedAE_Plus] Applied Flux不存在");
                
                if (currentSlots == 1) {
                    // Applied Flux不存在，我们将升级槽从1个增加到2个
                    System.out.println("[ExtendedAE_Plus] 将升级槽从1个增加到2个");
                    this.upgrades = UpgradeInventories.forMachine(is, 2, this::onUpgradesChanged);
                } else {
                    System.out.println("[ExtendedAE_Plus] Applied Flux不存在，当前槽数异常: " + currentSlots + "，跳过修改");
                    return;
                }
            }
            
            System.out.println("[ExtendedAE_Plus] InterfaceLogic升级槽扩展完成，最终槽数: " + this.upgrades.size());
        } catch (Exception e) {
            // 发生异常时不修改升级槽，确保不会崩溃
            System.err.println("[ExtendedAE_Plus] Failed to expand interface upgrades: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
