package com.extendedae_plus.integration.ae2;

import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.integrations.igtooltip.TooltipProvider;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.AEBasePart;
import com.extendedae_plus.compat.AdvancedAEPatternProviderCompat;
import com.extendedae_plus.util.ModCheckUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 片状供应器的名称存储在部件中，Jade 默认只能读取宿主方块实体的名称，
 * 因此通过 AE2 部件提示接口同步并显示自定义名称。
 */
public final class PatternProviderPartTooltips implements TooltipProvider {
    private static final String NAME_TAG = "extendedae_plus:pattern_provider_name";

    @Override
    public void registerCommon(CommonRegistration registration) {
        PartTooltips.addServerData(AEBasePart.class, PatternProviderPartTooltips::provideServerData);
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        PartTooltips.addName(AEBasePart.class, PatternProviderPartTooltips::getName);
    }

    private static void provideServerData(Player player, AEBasePart part, CompoundTag data) {
        if (isPatternProvider(part) && part.getCustomName() != null) {
            data.putString(NAME_TAG, Component.Serializer.toJson(part.getCustomName()));
        }
    }

    private static @Nullable Component getName(AEBasePart part, TooltipContext context) {
        if (!isPatternProvider(part)) {
            return null;
        }

        // AE2 的名称提供者接收宿主数据，部件数据按安装方向保存在子标签中。
        var side = part.getSide();
        var partData = context.serverData().getCompound("cableBusPart" + (side == null ? "center" : side.name()));
        if (!partData.contains(NAME_TAG, Tag.TAG_STRING)) {
            return null;
        }
        return Component.Serializer.fromJson(partData.getString(NAME_TAG));
    }

    private static boolean isPatternProvider(AEBasePart part) {
        return part instanceof PatternProviderLogicHost
                || ModCheckUtils.isLoaded(ModCheckUtils.MODID_AAE)
                && AdvancedAEPatternProviderCompat.isPatternProvider(part);
    }
}
