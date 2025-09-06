package com.extendedae_plus.content.crafting;

import appeng.block.crafting.ICraftingUnitType;
import net.minecraft.world.item.Item;

import com.extendedae_plus.init.ModItems;

public enum EPlusCraftingUnitType implements ICraftingUnitType {
    ACCELERATOR_4x(0, 4),
    ACCELERATOR_16x(0, 16),
    ACCELERATOR_64x(0, 64),
    ACCELERATOR_256x(0, 256),
    ACCELERATOR_1024x(0, 1024);

    private final long storage;
    private final int threads;

    EPlusCraftingUnitType(long storage, int threads) {
        this.storage = storage;
        this.threads = threads;
    }

    @Override
    public long getStorageBytes() {
        return this.storage;
    }

    @Override
    public int getAcceleratorThreads() {
        // AE2 在 CraftingCPUCluster.addBlockEntity 中对单块线程数做了上限 16 的硬校验。
        // 这里先进行夹取，避免形成结构时抛出 IllegalArgumentException 导致崩溃。
        // 后续如需突破上限，应通过 Mixin/扩展在集群层面增加“额外并行度”的实现。
        return Math.min(this.threads, 16);
    }

    @Override
    public Item getItemFromType() {
        return switch (this) {
            case ACCELERATOR_4x -> ModItems.ACCELERATOR_4x.get();
            case ACCELERATOR_16x -> ModItems.ACCELERATOR_16x.get();
            case ACCELERATOR_64x -> ModItems.ACCELERATOR_64x.get();
            case ACCELERATOR_256x -> ModItems.ACCELERATOR_256x.get();
            case ACCELERATOR_1024x -> ModItems.ACCELERATOR_1024x.get();
        };
    }
}
