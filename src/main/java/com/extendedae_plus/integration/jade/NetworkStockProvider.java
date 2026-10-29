package com.extendedae_plus.integration.jade;

import appeng.api.stacks.AEItemKey;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.client.jei.NetworkItemCache;
import com.extendedae_plus.util.NumberFormatUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum NetworkStockProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ExtendedAEPlus.id("ae2_network_stock");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public boolean isRequired() {
        // 库存行常开，不提供单独的 Jade 组件开关。
        return true;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!NetworkItemCache.INSTANCE.isConnected()) {
            return;
        }

        // 只查询准星目标的选取物品，保留其变体信息，不读取容器内容。
        AEItemKey key = AEItemKey.of(accessor.getPickedResult());
        if (key == null) {
            return;
        }

        long amount = NetworkItemCache.INSTANCE.getAmount(key);
        // 与 JEI 物品库存共用 k / M / G 等数量格式。
        tooltip.add(Component.translatable("extendedae_plus.jade.ae2_stock", NumberFormatUtil.formatNumber(amount)));
    }
}
