package com.extendedae_plus.mixin.ae2;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.CraftingMonitorBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 2000)
public abstract class CraftingCPUClusterMixin {
    
    @Shadow(remap = false)
    private MachineSource machineSrc;
    
    @Shadow(remap = false)
    @Final
    private List<CraftingBlockEntity> blockEntities;
    
    @Shadow(remap = false)
    @Final
    private List<CraftingMonitorBlockEntity> status;
    
    @Shadow(remap = false)
    private long storage;
    
    @Shadow(remap = false)
    private int accelerator;
    
    /**
     * 完全替换 addBlockEntity 方法，移除 16 线程限制
     * 在方法开始时注入，检查线程数，如果超过 16 就手动处理并取消原方法
     */
    @Inject(
            method = "addBlockEntity(Lappeng/blockentity/crafting/CraftingBlockEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0  // 设置为 0，即使注入失败也不会崩溃
    )
    private void extendedae_plus$removeThreadLimit(CraftingBlockEntity te, CallbackInfo ci) {
        // 只有当线程数超过 16 时才接管处理
        if (te.getAcceleratorThreads() > 16) {
            // 手动实现 addBlockEntity 的逻辑，但不检查 16 的限制
            if (this.machineSrc == null || te.isCoreBlock()) {
                this.machineSrc = new MachineSource(te);
            }
            
            te.setCoreBlock(false);
            te.saveChanges();
            this.blockEntities.add(0, te);
            
            if (te instanceof CraftingMonitorBlockEntity) {
                this.status.add((CraftingMonitorBlockEntity) te);
            }
            if (te.getStorageBytes() > 0) {
                this.storage += te.getStorageBytes();
            }
            if (te.getAcceleratorThreads() > 0) {
                // 这里不检查 <= 16 的限制，直接添加
                this.accelerator += te.getAcceleratorThreads();
            }
            
            // 取消原方法执行
            ci.cancel();
        }
        // 如果 <= 16，让原方法正常执行
    }
}
