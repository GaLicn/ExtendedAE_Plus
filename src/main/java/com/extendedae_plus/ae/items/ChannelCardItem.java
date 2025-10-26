package com.extendedae_plus.ae.items;

import appeng.items.materials.UpgradeCardItem;
import com.extendedae_plus.util.wireless.WirelessTeamUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 频道卡：存储频道号、所有者UUID和团队信息
 * - 右键空气：增加频道号
 * - 潜行左键空气：写入/清除玩家UUID和团队信息
 * - 潜行左键收发器：将频道卡的所有者信息写入收发器
 * 继承 AE2 的 UpgradeCardItem 以复用升级卡判定与提示框架。
 */
public class ChannelCardItem extends UpgradeCardItem {
    public static final String TAG_CHANNEL = "channel";
    public static final String TAG_OWNER_UUID = "ownerUUID";
    public static final String TAG_TEAM_NAME = "teamName"; // 用于显示

    public ChannelCardItem(Item.Properties properties) {
        super(properties);
    }

    public static void setChannel(ItemStack stack, long channel) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(TAG_CHANNEL, channel);
    }

    public static long getChannel(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_CHANNEL) ? tag.getLong(TAG_CHANNEL) : 0L;
    }

    /**
     * 设置频道卡的所有者UUID
     */
    public static void setOwnerUUID(ItemStack stack, UUID ownerUUID) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_OWNER_UUID, ownerUUID);
    }

    /**
     * 获取频道卡的所有者UUID
     */
    @Nullable
    public static UUID getOwnerUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_OWNER_UUID) ? tag.getUUID(TAG_OWNER_UUID) : null;
    }

    /**
     * 设置团队名称（用于显示）
     */
    public static void setTeamName(ItemStack stack, String teamName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_TEAM_NAME, teamName);
    }

    /**
     * 获取团队名称
     */
    @Nullable
    public static String getTeamName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_TEAM_NAME) ? tag.getString(TAG_TEAM_NAME) : null;
    }

    /**
     * 清除所有者信息
     */
    public static void clearOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_OWNER_UUID);
            tag.remove(TAG_TEAM_NAME);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        
        // 显示频道
        long ch = getChannel(stack);
        if (ch == 0L) {
            lines.add(Component.translatable("item.extendedae_plus.channel_card.channel.unset"));
        } else {
            lines.add(Component.translatable("item.extendedae_plus.channel_card.channel", ch));
        }
        
        // 显示所有者信息
        UUID ownerUUID = getOwnerUUID(stack);
        String teamName = getTeamName(stack);
        
        if (ownerUUID != null) {
            if (teamName != null && !teamName.isEmpty()) {
                lines.add(Component.translatable("item.extendedae_plus.channel_card.owner.team", teamName));
            } else {
                lines.add(Component.translatable("item.extendedae_plus.channel_card.owner.player", ownerUUID.toString().substring(0, 8)));
            }
        } else {
            lines.add(Component.translatable("item.extendedae_plus.channel_card.owner.unset"));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!level.isClientSide) {
            long ch = getChannel(stack);
            long next;
            
            if (player.isShiftKeyDown()) {
                // 潜行右键：减少频率
                next = Math.max(0L, ch - 1L);
            } else {
                // 普通右键：增加频率
                next = ch + 1L;
            }
            
            if (next != ch) {
                setChannel(stack, next);
                player.displayClientMessage(Component.translatable("item.extendedae_plus.channel_card.set", next), true);
            }
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) {
        // 左键实体时不做任何事，避免伤害实体
        if (player.isShiftKeyDown()) {
            return true; // 取消默认行为
        }
        return super.onLeftClickEntity(stack, player, entity);
    }
    
    @Override
    public boolean onBlockStartBreak(ItemStack stack, net.minecraft.core.BlockPos pos, Player player) {
        // 潜行左键方块时触发绑定/解绑
        if (!player.isShiftKeyDown()) {
            return false; // 不拦截
        }
        
        if (player.level().isClientSide) {
            return true; // 客户端拦截，防止破坏方块
        }
        
        // 服务端处理
        if (player.level() instanceof ServerLevel serverLevel) {
            // 检查是否是收发器（让Block类处理）
            var blockState = player.level().getBlockState(pos);
            if (blockState.getBlock() instanceof com.extendedae_plus.content.wireless.WirelessTransceiverBlock) {
                return false; // 不拦截，让Block类处理
            }
            
            // 执行绑定/解绑
            UUID currentOwner = getOwnerUUID(stack);
            
            if (currentOwner != null) {
                // 已有所有者，清除
                clearOwner(stack);
                player.displayClientMessage(
                    Component.translatable("item.extendedae_plus.channel_card.owner.cleared"), 
                    true
                );
            } else {
                // 写入当前玩家的UUID和团队信息
                UUID playerUUID = player.getUUID();
                setOwnerUUID(stack, playerUUID);
                
                // 获取团队名称用于显示
                Component teamName = WirelessTeamUtil.getNetworkOwnerName(serverLevel, playerUUID);
                setTeamName(stack, teamName.getString());
                
                player.displayClientMessage(
                    Component.translatable("item.extendedae_plus.channel_card.owner.bound", teamName), 
                    true
                );
            }
        }
        
        return true; // 拦截，防止破坏方块
    }
}
