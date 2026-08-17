package com.extendedae_plus.api.bridge;

import com.extendedae_plus.util.wireless.ChannelCardConnectionController;

/**
 * 非 mixin 包下的桥接接口，供 mixin 进行 instanceof 检测和回调。
 */
public interface IInterfaceWirelessLinkBridge {
    /** 统一频道卡控制器；旧部件未接入时返回 null。 */
    default ChannelCardConnectionController eap$getChannelCardController() {
        return null;
    }

    default void eap$updateWirelessLink() {
        var controller = eap$getChannelCardController();
        if (controller != null) controller.updateWirelessLink();
    }
    
    /**
     * 获取无线连接状态（服务端返回真实状态，客户端返回同步状态）
     */
    default boolean eap$isWirelessConnected() {
        var controller = eap$getChannelCardController();
        return controller != null && controller.isConnected();
    }
    
    /**
     * 设置客户端的无线连接状态（仅在客户端使用）
     */
    default void eap$setClientWirelessState(boolean connected) {
        var controller = eap$getChannelCardController();
        if (controller != null) controller.setClientConnected(connected);
    }
    
    /**
     * 检查是否已经进行过tick初始化
     */
    default boolean eap$hasTickInitialized() {
        var controller = eap$getChannelCardController();
        return controller == null || controller.isInitialized();
    }
    
    /**
     * 设置tick初始化状态
     */
    default void eap$setTickInitialized(boolean initialized) {
        var controller = eap$getChannelCardController();
        if (controller != null) controller.setInitialized(initialized);
    }
    
    /**
     * 执行频道链接初始化
     */
    default void eap$initializeChannelLink() {
        var controller = eap$getChannelCardController();
        if (controller != null) controller.initialize();
    }
    
    /**
     * 检查并处理延迟初始化
     */
    default void eap$handleDelayedInit() {
        var controller = eap$getChannelCardController();
        if (controller != null) controller.tick();
    }

    /**
     * 指示宿主是否需要保持慢速 tick 以维持无线连接。
     */
    default boolean eap$shouldKeepTicking() {
        var controller = eap$getChannelCardController();
        return controller != null && controller.shouldKeepTicking();
    }
}
