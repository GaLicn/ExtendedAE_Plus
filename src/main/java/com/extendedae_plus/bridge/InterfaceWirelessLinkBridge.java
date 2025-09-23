package com.extendedae_plus.bridge;

/**
 * 非 mixin 包下的桥接接口，供 mixin 进行 instanceof 检测和回调。
 */
public interface InterfaceWirelessLinkBridge {
    void eap$updateWirelessLink();
    
    /**
     * 获取无线连接状态（服务端返回真实状态，客户端返回同步状态）
     */
    default boolean eap$isWirelessConnected() {
        return false;
    }
    
    /**
     * 设置客户端的无线连接状态（仅在客户端使用）
     */
    default void eap$setClientWirelessState(boolean connected) {
        // 默认实现为空
    }
    
    /**
     * 检查是否已经进行过tick初始化
     */
    default boolean eap$hasTickInitialized() {
        return true; // 默认认为已初始化
    }
    
    /**
     * 设置tick初始化状态
     */
    default void eap$setTickInitialized(boolean initialized) {
        // 默认实现为空
    }
    
    /**
     * 执行频道链接初始化
     */
    default void eap$initializeChannelLink() {
        // 默认实现为空
    }
    
    /**
     * 检查并处理延迟初始化
     */
    default void eap$handleDelayedInit() {
        // 默认实现为空
    }
}
