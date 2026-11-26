package com.extendedae_plus.mixin.neoecoae.storage;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

/**
 * 为吞盘（{@link InfinityBigIntegerCellInventory}）桥接 NeoECOAE 的版本与差量缓存接口。
 * 仅在运行时检测到 {@code cn.dancingsnow.neoecoae.storage.StorageListingDelta} 存在时应用。
 */
@Mixin(value = InfinityBigIntegerCellInventory.class, remap = false)
public abstract class InfinityBigIntegerCellListingMixin implements StorageListingDelta {

    @Override
    public Object neoecoae$listingIdentity() {
        return ((InfinityBigIntegerCellInventory) (Object) this).getListingIdentity();
    }

    @Override
    public long neoecoae$listingVersion() {
        return ((InfinityBigIntegerCellInventory) (Object) this).getListingVersion();
    }

    @Override
    public long neoecoae$listedAmount(AEKey key) {
        return ((InfinityBigIntegerCellInventory) (Object) this).getListedAmount(key);
    }

    @Override
    public boolean neoecoae$visitListingChanges(long since, long current, Consumer<AEKey> changed) {
        return ((InfinityBigIntegerCellInventory) (Object) this).visitListingChanges(since, current, changed);
    }

    @Override
    public boolean neoecoae$visitListingChanges(long since, long current, ObjLongConsumer<AEKey> visitor) {
        return ((InfinityBigIntegerCellInventory) (Object) this).visitListingChanges(since, current, visitor);
    }
}
