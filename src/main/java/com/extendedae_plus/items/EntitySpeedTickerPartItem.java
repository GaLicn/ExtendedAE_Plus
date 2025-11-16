package com.extendedae_plus.items;

import appeng.items.parts.PartItem;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class EntitySpeedTickerPartItem extends PartItem<EntitySpeedTickerPart> {
    public EntitySpeedTickerPartItem(Properties properties) {
        super(properties, EntitySpeedTickerPart.class, EntitySpeedTickerPart::new);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, world, tooltip, flag);
        // 中文 tooltip：提示需要放入实体加速卡开始加速，显示最大加速倍率和能量消耗说明
        tooltip.add(Component.translatable("item.extendedae_plus.entity_speed_ticker.tip.requirement", "需要放入实体加速卡以启用加速"));
        tooltip.add(Component.translatable("item.extendedae_plus.entity_speed_ticker.tip.max", "最高可达 1024x 加速"));
        tooltip.add(Component.translatable("item.extendedae_plus.entity_speed_ticker.tip.energy", "加速将消耗 AE 网络能量，网络能量不足时无法加速"));
    }
}