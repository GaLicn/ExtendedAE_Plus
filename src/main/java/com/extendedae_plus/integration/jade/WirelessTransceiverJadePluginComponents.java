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
                tooltip.add(Component.translatable("extendedae_plus.tooltip.master_mode", masterMode ? "主模式" : "从模式"));
            }
        }
    },
    MASTER_LOCATION("wt_master_location") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("masterMode") && !data.getBoolean("masterMode") && data.contains("masterPos")) {
                BlockPos pos = BlockPos.of(data.getLong("masterPos"));
                String dim = data.contains("masterDim") ? data.getString("masterDim") : "";
                tooltip.add(Component.literal("主节点位置: (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"));
                if (!dim.isEmpty()) {
                    tooltip.add(Component.literal("维度: " + dim));
                }
            }
        }
    },
    LOCKED("wt_locked") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("locked")) {
                boolean locked = data.getBoolean("locked");
                tooltip.add(Component.translatable("extendedae_plus.tooltip.locked", locked ? "已锁定" : "未锁定"));
            }
        }
    },
    NETWORK_USABLE("wt_network_usable") {
        @Override
        protected void add(BlockAccessor accessor, ITooltip tooltip, IPluginConfig config, CompoundTag data) {
            if (data.contains("networkUsable")) {
                boolean usable = data.getBoolean("networkUsable");
                tooltip.add(Component.literal((usable ? "设备在线" : "设备离线")));
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


