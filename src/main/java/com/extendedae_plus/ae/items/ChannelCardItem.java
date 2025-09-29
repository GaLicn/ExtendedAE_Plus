package com.extendedae_plus.ae.items;

import appeng.items.materials.UpgradeCardItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 频道卡（MVP）：仅存储一个 long 类型的频道号到 NBT："channel"。
 * 继承 AE2 的 UpgradeCardItem 以复用升级卡判定与提示框架。
 */
public class ChannelCardItem extends UpgradeCardItem {
    public static final String TAG_CHANNEL = "channel";

    public ChannelCardItem(Item.Properties properties) {
        super(properties);
    }

    public static void setChannel(ItemStack stack, long channel) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(TAG_CHANNEL, channel);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static long getChannel(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_CHANNEL) ? tag.getLong(TAG_CHANNEL) : 0L;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        long ch = getChannel(stack);
        if (ch == 0L) {
            lines.add(Component.translatable("item.extendedae_plus.channel_card.channel.unset"));
        } else {
            lines.add(Component.translatable("item.extendedae_plus.channel_card.channel", ch));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            long ch = getChannel(stack);
            boolean dec = player.isShiftKeyDown();
            long next = dec ? Math.max(0L, ch - 1L) : ch + 1L;
            if (next != ch) {
                setChannel(stack, next);
                player.displayClientMessage(Component.translatable("item.extendedae_plus.channel_card.set", next), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
