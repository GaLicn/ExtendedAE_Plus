package com.extendedae_plus.integration.jade;

import com.extendedae_plus.content.wireless.WirelessTransceiverBlock;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("extendedae_plus") // 你的 mod ID
public class WirelessTransceiverJadePlugin implements IWailaPlugin {

	@Override
	public void register(IWailaCommonRegistration registration) {
		// 注册服务端数据提供者（用于同步数据）
		registration.registerBlockDataProvider(WirelessTransceiverProvider.INSTANCE, WirelessTransceiverBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		// 遍历组件常量，逐一注册
		for (var component : WirelessTransceiverJadePluginComponents.values()) {
			registration.registerBlockComponent(component, WirelessTransceiverBlock.class);
		}
	}
}