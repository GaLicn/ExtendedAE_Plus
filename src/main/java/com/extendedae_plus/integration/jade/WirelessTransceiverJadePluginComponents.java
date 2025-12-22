package com.extendedae_plus.integration.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 单文件聚合的 Jade 组件提供者，包含五个子组件常量，分别对应五个独立的开关/UID。
 */
public enum WirelessTransceiverJadePluginComponents implements IBlockComponentProvider {
    FREQUENCY("wt_frequency") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("frequency")) {
                long frequency = data.getLong("frequency");
                tooltip.add(Component.translatable("extendedae_plus.tooltip.frequency", frequency));
            }
        }
    },
    MODE("wt_master_mode") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("masterMode")) {
                boolean masterMode = data.getBoolean("masterMode");
                tooltip.add(Component.translatable("extendedae_plus.tooltip.master_mode",
                        Component.translatable(masterMode ? "extendedae_plus.state.master" : "extendedae_plus.state.slave")));
            }
        }
    },
    MASTER_LOCATION("wt_master_location") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("masterMode") && !data.getBoolean("masterMode") && data.contains("masterPos")) {
                BlockPos pos = BlockPos.of(data.getLong("masterPos"));
                String dim = data.contains("masterDim") ? data.getString("masterDim") : "";
                String customName = data.contains("customName") ? data.getString("customName") : null;
                if (customName != null) {
                    tooltip.add(Component.translatable("extendedae_plus.jade.master_named", customName, pos.getX(), pos.getY(), pos.getZ()));
                } else {
                    tooltip.add(Component.translatable("extendedae_plus.jade.master_pos", pos.getX(), pos.getY(), pos.getZ()));
                }
                if (!dim.isEmpty()) {
                    tooltip.add(Component.translatable("extendedae_plus.jade.dimension", dim));
                }
            }
        }
    },
    LOCKED("wt_locked") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("locked")) {
                boolean locked = data.getBoolean("locked");
                tooltip.add(Component.translatable("extendedae_plus.tooltip.locked",
                        Component.translatable(locked ? "extendedae_plus.state.locked" : "extendedae_plus.state.unlocked")));
            }
        }
    },
    NETWORK_USABLE("wt_network_usable") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("networkUsable")) {
                boolean usable = data.getBoolean("networkUsable");
                tooltip.add(Component.translatable(usable
                        ? "extendedae_plus.jade.network.online"
                        : "extendedae_plus.jade.network.offline"));
            }
        }
    },
    OWNER("wt_owner") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("ownerName")) {
                String ownerName = data.getString("ownerName");
                tooltip.add(Component.translatable("extendedae_plus.tooltip.owner", ownerName));
            } else if (data.hasUUID("placerId")) {
                // 有placerId但没有名称，显示UUID
                java.util.UUID placerId = data.getUUID("placerId");
                tooltip.add(Component.translatable("extendedae_plus.tooltip.owner", placerId.toString().substring(0, 8) + "..."));
            } else {
                // 没有所有者信息（公共收发器）
                tooltip.add(Component.translatable("extendedae_plus.tooltip.owner.public"));
            }
        }
    },
    CHANNELS("wt_channels") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("usedChannels") && data.contains("maxChannels")) {
                int usedChannels = data.getInt("usedChannels");
                int maxChannels = data.getInt("maxChannels");
                // 参考AE2的显示方式
                if (maxChannels <= 0) {
                    // 无限频道或未设置
                    tooltip.add(Component.translatable("extendedae_plus.tooltip.channels", usedChannels));
                } else {
                    // 显示 "已使用/最大"
                    tooltip.add(Component.translatable("extendedae_plus.tooltip.channels_of", usedChannels, maxChannels));
                }
            }
        }
    };

    private final ResourceLocation uid;

    WirelessTransceiverJadePluginComponents(String path) {
        this.uid = ResourceLocation.fromNamespaceAndPath("extendedae_plus", path);
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public final void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null) return;
        add(accessor, tooltip, config, data);
    }

    protected abstract void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data);
}


