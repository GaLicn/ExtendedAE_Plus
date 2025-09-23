package com.extendedae_plus.bridge;

/**
 * 非 mixin 包下的桥接接口，供 mixin 进行 instanceof 检测和回调。
 */
public interface InterfaceWirelessLinkBridge {
    void extendedae_plus$updateWirelessLink();
    
    /**
     * 获取无线连接状态（服务端返回真实状态，客户端返回同步状态）
     */
    default boolean extendedae_plus$isWirelessConnected() {
        return false;
    }
    
    /**
     * 设置客户端的无线连接状态（仅在客户端使用）
     */
    default void extendedae_plus$setClientWirelessState(boolean connected) {
        // 默认实现为空
    }
}
