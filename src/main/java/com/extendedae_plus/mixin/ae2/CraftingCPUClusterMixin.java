package com.extendedae_plus.mixin.ae2;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.CraftingMonitorBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 2000)
public abstract class CraftingCPUClusterMixin {
    
    @Shadow private List<CraftingBlockEntity> blockEntities;
    @Shadow private List<CraftingMonitorBlockEntity> status;
    @Shadow private MachineSource machineSrc;
    @Shadow private long storage;
    @Shadow private int accelerator;
    
    /**
     * 完全重写addBlockEntity方法，移除16线程的硬限制
     * @author ExtendedAE_Plus
     * @reason 移除单方块16线程的硬限制，允许更高的线程数
     */
    @Overwrite
    void addBlockEntity(CraftingBlockEntity te) {
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
            // 移除原来的16线程限制，直接添加线程数
            this.accelerator += te.getAcceleratorThreads();
        }
    }
}
