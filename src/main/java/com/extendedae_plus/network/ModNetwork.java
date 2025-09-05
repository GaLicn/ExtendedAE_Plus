package com.extendedae_plus.network;

/**
 * 临时的网络通道占位实现，仅用于让 GUI 方案A 最小子集通过编译。
 * 后续将替换为 NeoForge SimpleChannel 正式实现。
 */
public class ModNetwork {
    public static final DummyChannel CHANNEL = new DummyChannel();

    public static void register() {
        // TODO: 后续接入 NeoForge SimpleChannel 正式注册
    }

    public static class DummyChannel {
        public void sendToServer(Object any) {
            // no-op 占位
        }
    }
}
