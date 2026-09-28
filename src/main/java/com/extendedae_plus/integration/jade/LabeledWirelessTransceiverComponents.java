package com.extendedae_plus.integration.jade;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum LabeledWirelessTransceiverComponents implements IBlockComponentProvider {
    LABEL_AND_CHANNEL("labeled_wireless_component") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            String label = data.getString("label").orElse("");
            tooltip.add(Component.translatable("extendedae_plus.jade.label", label.isEmpty() ? "-" : label));

            // 所有者
            if (data.contains("ownerName")) {
                data.getString("ownerName").ifPresent(ownerName ->
                        tooltip.add(Component.translatable("extendedae_plus.jade.owner", ownerName)));
            } else if (data.contains("placerId")) {
                data.getIntArray("placerId").map(UUIDUtil::uuidFromIntArray).ifPresent(placerId ->
                        tooltip.add(Component.translatable("extendedae_plus.jade.owner", placerId.toString().substring(0, 8) + "...")));
            } else {
                tooltip.add(Component.translatable("extendedae_plus.jade.owner.public"));
            }

            // 频道占用
            if (data.contains("usedChannels") && data.contains("maxChannels")) {
                data.getInt("usedChannels").ifPresent(used -> data.getInt("maxChannels").ifPresent(max -> {
                    if (max <= 0) {
                        tooltip.add(Component.translatable("extendedae_plus.jade.channels", used));
                    } else {
                        tooltip.add(Component.translatable("extendedae_plus.jade.channels_of", used, max));
                    }
                }));
            }

            // 网络在线
            data.getBoolean("networkUsable").ifPresent(online ->
                    tooltip.add(Component.translatable(online ? "extendedae_plus.jade.online" : "extendedae_plus.jade.offline")));
        }
    };

    private final Identifier uid;

    LabeledWirelessTransceiverComponents(String name) {
        this.uid = Identifier.fromNamespaceAndPath("extendedae_plus", name);
    }

    @Override
    public Identifier getUid() {
        return uid;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getServerData() != null) {
            add(accessor, tooltip, config, accessor.getServerData());
        }
    }

    protected abstract void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data);
}
