package cn.dancingsnow.neoecoae.storage;

/**
 * Compile-only stub for NeoECOAE's optional StorageListingVersion interface.
 * Not packaged into the output jar.
 */
public interface StorageListingVersion {
    long UNKNOWN = Long.MIN_VALUE;

    long neoecoae$listingVersion();

    default Object neoecoae$listingIdentity() {
        return this;
    }
}
