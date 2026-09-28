package com.extendedae_plus.util.wireless;

import appeng.api.upgrades.IUpgradeInventory;
import com.extendedae_plus.ae.wireless.WirelessSlaveLink;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.materials.ChannelCardItem;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 统一处理频道卡升级槽扫描与无线从端保活判定，避免各宿主实现漂移。
 */
public final class ChannelCardLinkHelper {

    private ChannelCardLinkHelper() {
    }

    public record BoundChannel(long channel, @Nullable UUID owner) {
    }

    @Nullable
    public static BoundChannel findBoundChannel(@Nullable IUpgradeInventory upgrades, Supplier<UUID> fallbackOwner) {
        if (upgrades == null) {
            return null;
        }

        for (ItemStack stack : upgrades) {
            if (stack.isEmpty() || stack.getItem() != ModItems.CHANNEL_CARD.get()) {
                continue;
            }

            UUID owner = ChannelCardItem.getOwnerUUID(stack);
            if (owner == null) {
                owner = fallbackOwner.get();
            }

            return new BoundChannel(ChannelCardItem.getChannel(stack), owner);
        }

        return null;
    }

    public static boolean hasChannelCard(@Nullable IUpgradeInventory upgrades) {
        if (upgrades == null) {
            return false;
        }

        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.CHANNEL_CARD.get()) {
                return true;
            }
        }

        return false;
    }

    public static boolean sameTarget(long lastChannel, @Nullable UUID lastOwner, @Nullable BoundChannel boundChannel) {
        return boundChannel != null
                && lastChannel == boundChannel.channel()
                && Objects.equals(lastOwner, boundChannel.owner());
    }

    public static boolean hasActiveLink(@Nullable WirelessSlaveLink link) {
        return link != null && (link.getFrequency() != 0L || link.isConnected());
    }

    public static boolean shouldKeepTicking(@Nullable IUpgradeInventory upgrades,
                                            @Nullable WirelessSlaveLink link,
                                            boolean initialized) {
        return !initialized || hasChannelCard(upgrades) || hasActiveLink(link);
    }

    public static void disconnect(@Nullable WirelessSlaveLink link) {
        if (link == null) {
            return;
        }

        link.setPlacerId(null);
        link.setFrequency(0L);
        link.updateStatus();
    }
}
