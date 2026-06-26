package com.extendedae_plus.network;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 取回最近一次成功上传的编码样板。
 */
public class ReturnLastPatternC2SPacket implements CustomPacketPayload {
    public static final Type<ReturnLastPatternC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "return_last_pattern"));

    public static final ReturnLastPatternC2SPacket INSTANCE = new ReturnLastPatternC2SPacket();

    public static final StreamCodec<FriendlyByteBuf, ReturnLastPatternC2SPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    private ReturnLastPatternC2SPacket() {}

    public static void handle(final ReturnLastPatternC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ExtendedAEPatternUploadUtil.LastUploadRecord record = ExtendedAEPatternUploadUtil.getLastUploadRecord(player);
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

            PatternContainer targetContainer = ExtendedAEPatternUploadUtil.findProviderContainer(player, record);
            if (targetContainer == null || !targetContainer.isVisibleInTerminal()) {
                return;
            }

            InternalInventory inv = targetContainer.getTerminalPatternInventory();
            if (inv != null) {
                returnPatternFromSlot(player, inv, record.slot());
            }
        });
    }

    private static InternalInventory findMatrixInventory(ServerPlayer player, ExtendedAEPatternUploadUtil.LastUploadRecord record) {
        var level = ExtendedAEPatternUploadUtil.findLevel(player, record.dimension());
        if (level == null) {
            return null;
        }
        return findMatrixInventory(level.getBlockEntity(BlockPos.of(record.pos())), record);
    }

    private static InternalInventory findMatrixInventory(BlockEntity blockEntity, ExtendedAEPatternUploadUtil.LastUploadRecord record) {
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
