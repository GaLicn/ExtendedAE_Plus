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
            long channel = data.contains("channel") ? data.getLong("channel") : 0L;
            tooltip.add(Component.translatable("extendedae_plus.jade.label", label.isEmpty() ? "-" : label));
            tooltip.add(Component.translatable("extendedae_plus.jade.frequency", channel));

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
