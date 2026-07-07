package com.extendedae_plus.network.provider;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: Return the pattern from the exact slot recorded on the last successful upload.
 */
public class ReturnLastPatternC2SPacket {
    public ReturnLastPatternC2SPacket() {}

    public static void encode(ReturnLastPatternC2SPacket msg, FriendlyByteBuf buf) {}

    public static ReturnLastPatternC2SPacket decode(FriendlyByteBuf buf) {
        return new ReturnLastPatternC2SPacket();
    }

    public static void handle(ReturnLastPatternC2SPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            ProviderUploadUtil.LastUploadRecord record = ProviderUploadUtil.getLastUploadRecord(player);
            if (record == null || record.slot() < 0) {
                return;
            }

            if (record.isMatrix()) {
                InternalInventory inv = findMatrixInventory(player, record);
                if (inv != null) {
                    returnPatternFromSlot(player, inv, record.slot());
                }
                return;
            }

            PatternContainer targetContainer = ProviderUploadUtil.findProviderContainer(player, record);
            if (targetContainer == null
                    || !targetContainer.isVisibleInTerminal()
                    || !ProviderUploadUtil.isAccessiblePatternSlot(targetContainer, record.slot())) {
                return;
            }

            InternalInventory inv = targetContainer.getTerminalPatternInventory();
            if (inv != null) {
                returnPatternFromSlot(player, inv, record.slot());
            }
        });
        ctx.setPacketHandled(true);
    }

    private static InternalInventory findMatrixInventory(ServerPlayer player, ProviderUploadUtil.LastUploadRecord record) {
        if (!record.dimension().isEmpty() && !record.dimension().equals(player.level().dimension().location().toString())) {
            var level = ProviderUploadUtil.findLevel(player, record.dimension());
            if (level == null) {
                return null;
            }
            return findMatrixInventory(level.getBlockEntity(BlockPos.of(record.pos())), record);
        }

        return findMatrixInventory(player.level().getBlockEntity(BlockPos.of(record.pos())), record);
    }

    private static InternalInventory findMatrixInventory(BlockEntity blockEntity, ProviderUploadUtil.LastUploadRecord record) {
        if (record.matrixPlus()) {
            if (blockEntity instanceof PatternCorePlusBlockEntity plus && plus.isFormed() && plus.getMainNode().isActive()) {
                return plus.getTerminalPatternInventory();
            }
            return null;
        }

        if (blockEntity instanceof TileAssemblerMatrixPattern pattern && pattern.isFormed() && pattern.getMainNode().isActive()) {
            return pattern.getTerminalPatternInventory();
        }
        return null;
    }

    private static boolean returnPatternFromSlot(ServerPlayer player, InternalInventory inv, int slot) {
        if (slot < 0 || slot >= inv.size()) {
            return false;
        }

        ItemStack stack = inv.getStackInSlot(slot);
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        ItemStack extracted = inv.extractItem(slot, 1, false);
        if (extracted.isEmpty()) {
            return false;
        }

        if (!player.getInventory().add(extracted)) {
            player.drop(extracted, false);
        }
        return true;
    }
}
