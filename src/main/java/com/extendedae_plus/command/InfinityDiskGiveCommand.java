package com.extendedae_plus.command;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.ae.items.InfinityBigIntegerCellItem;
import com.extendedae_plus.util.storage.InfinityStorageManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * /eap give_infinity_disks
 * 为执行命令的玩家生成当前世界已加载的所有 Infinity 磁盘（按 UUID）并发放到玩家物品栏
 */
public class InfinityDiskGiveCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eap").then(
                Commands.literal("give_infinity_disks").executes(InfinityDiskGiveCommand::execute)
        ));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (player.level() == null || !(player.level() instanceof ServerLevel)) {
                source.sendFailure(Component.literal("This command must be run on server side."));
                return 0;
            }
            InfinityStorageManager mgr = ExtendedAEPlus.STORAGE_INSTANCE;
            if (mgr == null) {
                source.sendFailure(Component.literal("InfinityStorageManager is not initialized."));
                return 0;
            }

            int given = 0;
            for (UUID id : mgr.getAllLoadedUUIDs()) {
                ItemStack stack = InfinityBigIntegerCellItem.withUUID(id);
                if (!player.getInventory().add(stack)) {
                    // 若玩家物品栏已满，则扔在地上
                    player.drop(stack, false);
                }
                given++;
            }
            final int finalGiven = given;
            source.sendSuccess(() -> Component.literal("Gave " + finalGiven + " infinity disks."), false);
            return given;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Error: " + ex.getMessage()));
            return 0;
      }
    }
}


