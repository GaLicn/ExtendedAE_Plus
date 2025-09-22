package com.extendedae_plus.bridge;

/**
 * 非 mixin 包下的桥接接口，供 mixin 进行 instanceof 检测和回调。
 */
public interface InterfaceWirelessLinkBridge {
    void extendedae_plus$updateWirelessLink();
}
