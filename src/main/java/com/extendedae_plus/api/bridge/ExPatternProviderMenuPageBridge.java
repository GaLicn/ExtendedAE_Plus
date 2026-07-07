package com.extendedae_plus.api.bridge;

/**
 * 为扩展样板供应器界面提供页码同步访问。
 */
public interface ExPatternProviderMenuPageBridge {
    int eap$getPage();

    int eap$getUnlockedMaxPage();

    void eap$setPage(int page);
}
