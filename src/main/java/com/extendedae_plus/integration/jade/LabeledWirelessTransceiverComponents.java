package com.extendedae_plus.integration.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum LabeledWirelessTransceiverComponents implements IBlockComponentProvider {
    LABEL_AND_CHANNEL("labeled_wireless_component") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, net.minecraft.nbt.CompoundTag data) {
            String label = data.contains("label") ? data.getString("label") : "";
            tooltip.add(Component.translatable("extendedae_plus.jade.label", label.isEmpty() ? "-" : label));

            // 所有者
            if (data.contains("ownerName")) {
                tooltip.add(Component.translatable("extendedae_plus.jade.owner", data.getString("ownerName")));
            } else if (data.contains("placerId")) {
                java.util.UUID placerId = data.getUUID("placerId");
                tooltip.add(Component.translatable("extendedae_plus.jade.owner", placerId.toString().substring(0, 8) + "..."));
            } else {
                tooltip.add(Component.translatable("extendedae_plus.jade.owner.public"));
            }

            // 频道占用
            if (data.contains("usedChannels") && data.contains("maxChannels")) {
                int used = data.getInt("usedChannels");
                int max = data.getInt("maxChannels");
                if (max <= 0) {
                    tooltip.add(Component.translatable("extendedae_plus.jade.channels", used));
                } else {
                    tooltip.add(Component.translatable("extendedae_plus.jade.channels_of", used, max));
                }
            }

            // 网络在线
            if (data.contains("networkUsable")) {
                boolean online = data.getBoolean("networkUsable");
                tooltip.add(Component.translatable(online ? "extendedae_plus.jade.online" : "extendedae_plus.jade.offline"));
            }
        }
    };

    private final ResourceLocation uid;

    LabeledWirelessTransceiverComponents(String name) {
        this.uid = new ResourceLocation("extendedae_plus", name);
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getServerData() != null) {
            add(accessor, tooltip, config, accessor.getServerData());
        }
    }

    protected abstract void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, net.minecraft.nbt.CompoundTag data);
}
