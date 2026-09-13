package com.extendedae_plus_gtladd.util.uploadPattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.extendedae_plus.util.GlobalSendMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class GTMatrixUploadUtil {
    private GTMatrixUploadUtil() {
    }

    public static void uploadFromEncodingMenuToMatrix(ServerPlayer player, PatternEncodingTermMenu menu) {
        if (player != null && menu != null) {
            RestrictedInputSlot encodedSlot = ((PatternEncodingTermMenuAccessor) menu).eap$getEncodedPatternSlot();
            ItemStack stack = encodedSlot.getItem();
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
                if (details instanceof AECraftingPattern || details instanceof AESmithingTablePattern || details instanceof AEStonecuttingPattern) {
                    if (!AEUtils.molecularFilter(stack, player.level())) {
                        player.displayClientMessage(Component.translatable("extendedae_plus_gtladd.upload_to_GTMatrix.error"), false);
                        refundBlankPattern(player, menu, stack.getCount());
                    } else {
                        IGridNode node = menu.getNetworkNode();
                        if (node != null) {
                            IGrid grid = node.getGrid();
                            if (grid != null) {
                                int stackCount = stack.getCount();
                                ItemStack toInsert = stack.copy();
                                List<InternalInventory> inventories = findAllGTMatrixPatternInventories(grid);
                                if (GTMatrixContainsPattern(inventories, stack)) {
                                    GlobalSendMessage.sendPlayerMessage(player, Component.translatable("extendedae_plus_gtladd.upload_to_GTMatrix.repetition"));
                                    refundBlankPattern(player, menu, stackCount);
                                    encodedSlot.set(ItemStack.EMPTY);
                                } else {
                                    for (InternalInventory inv : inventories) {
                                        if (inv != null) {
                                            ItemStack remain = inv.addItems(toInsert);
                                            if (remain.getCount() < stackCount) {
                                                completeUploadSuccess(player, encodedSlot, stack, remain);
                                                return;
                                            }
                                        }
                                    }

                                    GlobalSendMessage.sendPlayerMessage(player, inventories.isEmpty() ? Component.translatable("extendedae_plus_gtladd.upload_to_matrix.fail_no_GTMatrix") : Component.translatable("extendedae_plus_gtladd.upload_to_GTMatrix.fail_full"));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static List<InternalInventory> findAllGTMatrixPatternInventories(IGrid grid) {
        List<InternalInventory> result = new ArrayList<>();
        if (grid == null) {
            return result;
        } else {
            try {
                for (MEMolecularAssemblerIOPartMachine tile : grid.getMachines(MEMolecularAssemblerIOPartMachine.class)) {
                    if (tile != null && tile.isFormed() && tile.getMainNode().isActive()) {
                        InternalInventory inv = tile.getTerminalPatternInventory();
                        if (inv != null) {
                            result.add(inv);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            return result;
        }
    }

    private static boolean GTMatrixContainsPattern(@NotNull List<InternalInventory> inventories, @NotNull ItemStack pattern) {
        for (InternalInventory inv : inventories) {
            if (inv != null) {
                for (int i = 0; i < inv.size(); ++i) {
                    ItemStack s = inv.getStackInSlot(i);
                    if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, pattern)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void completeUploadSuccess(ServerPlayer player, RestrictedInputSlot encodedSlot, ItemStack stack, ItemStack remain) {
        int inserted = stack.getCount() - remain.getCount();
        if (inserted > 0) {
            stack.shrink(inserted);
            if (stack.isEmpty()) {
                encodedSlot.set(ItemStack.EMPTY);
            }

            GlobalSendMessage.sendPlayerMessage(player, Component.translatable("extendedae_plus_gtladd.upload_to_GTMatrix.success"));
        }

    }

    private static void refundBlankPattern(ServerPlayer player, PatternEncodingTermMenu menu, int count) {
        try {
            PatternEncodingTermMenuAccessor accessor = (PatternEncodingTermMenuAccessor) menu;
            RestrictedInputSlot blankSlot = accessor.eap$getBlankPatternSlot();
            ItemStack blanks = AEItems.BLANK_PATTERN.stack(count);
            if (blankSlot != null && blankSlot.mayPlace(blanks)) {
                ItemStack remain = blankSlot.safeInsert(blanks);
                if (!remain.isEmpty() && player != null) {
                    player.getInventory().placeItemBackInInventory(remain, false);
                }
            } else if (player != null) {
                player.getInventory().placeItemBackInInventory(blanks, false);
            }
        } catch (Throwable ignored) {
            if (player != null) {
                player.getInventory().placeItemBackInInventory(AEItems.BLANK_PATTERN.stack(count), false);
            }
        }

    }
}
