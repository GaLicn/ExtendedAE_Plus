package com.extendedae_plus.integration.jade;

import com.extendedae_plus.content.wireless.WirelessTransceiverBlock;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlock;
import com.extendedae_plus.content.wireless.LabeledWirelessTransceiverBlockEntity;
import com.extendedae_plus.integration.jade.LabeledWirelessTransceiverProvider;
import com.extendedae_plus.integration.jade.LabeledWirelessTransceiverComponents;
import com.extendedae_plus.integration.jade.WirelessTransceiverProvider;
import com.extendedae_plus.integration.jade.WirelessTransceiverJadePluginComponents;
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
		registration.registerBlockDataProvider(LabeledWirelessTransceiverProvider.INSTANCE, LabeledWirelessTransceiverBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		// 遍历组件常量，逐一注册
		for (var component : WirelessTransceiverJadePluginComponents.values()) {
			registration.registerBlockComponent(component, WirelessTransceiverBlock.class);
		}
		registration.registerBlockComponent(LabeledWirelessTransceiverComponents.LABEL_AND_CHANNEL, LabeledWirelessTransceiverBlock.class);
	}
}