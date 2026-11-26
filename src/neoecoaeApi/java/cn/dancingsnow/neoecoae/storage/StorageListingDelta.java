package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;

import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

/**
 * Compile-only stub for NeoECOAE's optional StorageListingDelta interface.
 * Not packaged into the output jar.
 */
public interface StorageListingDelta extends StorageListingVersion {
    boolean neoecoae$visitListingChanges(long since, long current, Consumer<AEKey> changed);

    default boolean neoecoae$visitListingChanges(long since, long current, ObjLongConsumer<AEKey> visitor) {
        throw new AbstractMethodError();
    }

    long neoecoae$listedAmount(AEKey key);
}
