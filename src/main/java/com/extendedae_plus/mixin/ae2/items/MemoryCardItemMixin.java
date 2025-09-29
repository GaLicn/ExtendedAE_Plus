package com.extendedae_plus.mixin.ae2.items;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.localization.PlayerMessages;
import appeng.items.tools.MemoryCardItem;
import appeng.items.tools.NetworkToolItem;
import appeng.util.inv.PlayerInternalInventory;
import com.extendedae_plus.ae.parts.EntitySpeedTickerPart;
import com.extendedae_plus.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(value = MemoryCardItem.class, remap = false)
public class MemoryCardItemMixin {

    /**
     * 写入 Memory Card 时保留实体加速卡的完整 NBT 数据
     */
    @Inject(method = "storeUpgrades", at = @At("HEAD"), cancellable = true)
    private static void storeUpgradesCustom(IUpgradeableObject upgradeableObject, CompoundTag output, CallbackInfo ci) {
        try {
            CompoundTag desiredUpgradesTag = new CompoundTag();
            ListTag entitySpeedCards = new ListTag();
            InternalInventory upgrades = upgradeableObject.getUpgrades();

            for (int i = 0; i < upgrades.size(); i++) {
                ItemStack upgradeStack = upgrades.getStackInSlot(i);
                if (upgradeStack.isEmpty()) continue;

                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(upgradeStack.getItem());
                String key = itemId.toString();

                if (upgradeStack.getItem().equals(ModItems.ENTITY_SPEED_CARD.get())) {
                    CompoundTag stackTag = new CompoundTag();
                    stackTag.putInt("Slot", i);
                    upgradeStack.save(stackTag);
                    entitySpeedCards.add(stackTag);
                } else {
                    desiredUpgradesTag.putInt(key, desiredUpgradesTag.getInt(key) + upgradeStack.getCount());
                }
            }

            if (!entitySpeedCards.isEmpty()) {
                desiredUpgradesTag.put("entity_speed_cards", entitySpeedCards);
            }

            output.put("upgrades", desiredUpgradesTag);
            ci.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从 Memory Card 恢复升级时，从玩家背包或网络工具提取实体加速卡
     */
    @Inject(method = "restoreUpgrades", at = @At("HEAD"), cancellable = true)
    private static void restoreUpgradesCustom(Player player, CompoundTag input, IUpgradeableObject upgradeableObject, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!input.contains("upgrades")) {
                cir.setReturnValue(false);
                return;
            }

            CompoundTag desiredUpgradesTag = input.getCompound("upgrades");
            InternalInventory upgrades = upgradeableObject.getUpgrades();

            // 收集背包和网络工具作为升级卡来源
            var upgradeSources = new ArrayList<InternalInventory>();
            upgradeSources.add(new PlayerInternalInventory(player.getInventory()));
            var networkTool = NetworkToolItem.findNetworkToolInv(player);
            if (networkTool != null) {
                upgradeSources.add(networkTool.getInventory());
            }

            // 清空所有槽位中的 EntitySpeedCardItem
            for (int i = 0; i < upgrades.size(); i++) {
                ItemStack stack = upgrades.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem().equals(ModItems.ENTITY_SPEED_CARD.get())) {
                    ItemStack removed = upgrades.extractItem(i, stack.getCount(), false);
                    for (var source : upgradeSources) {
                        if (!removed.isEmpty()) {
                            removed = source.addItems(removed);
                        }
                    }
                    if (!removed.isEmpty()) {
                        player.drop(removed, false);
                    }
                }
            }

            // 恢复 EntitySpeedCardItem
            if (desiredUpgradesTag.contains("entity_speed_cards", Tag.TAG_LIST)) {
                ListTag entitySpeedCards = desiredUpgradesTag.getList("entity_speed_cards", Tag.TAG_COMPOUND);
                for (int i = 0; i < entitySpeedCards.size(); i++) {
                    CompoundTag stackTag = entitySpeedCards.getCompound(i);
                    ItemStack desiredStack = ItemStack.of(stackTag);
                    int slot = stackTag.contains("Slot") ? stackTag.getInt("Slot") : i;

                    if (player.getAbilities().instabuild) {
                        // 创造模式：直接生成
                        if (slot >= 0 && slot < upgrades.size()) {
                            upgrades.setItemDirect(slot, desiredStack);
                        } else {
                            upgrades.addItems(desiredStack);
                        }
                    } else {
                        // 非创造模式：从背包或网络工具提取
                        int missingAmount = desiredStack.getCount();
                        ItemStack extracted = ItemStack.EMPTY;

                        for (var source : upgradeSources) {
                            ItemStack potential = new ItemStack(desiredStack.getItem(), missingAmount);
                            if (desiredStack.hasTag()) {
                                potential.setTag(desiredStack.getTag().copy());
                            }
                            ItemStack cards = source.removeItems(missingAmount, potential, null);
                            if (!cards.isEmpty()) {
                                ItemStack overflow = upgrades.addItems(cards);
                                if (!overflow.isEmpty()) {
                                    player.getInventory().placeItemBackInInventory(overflow);
                                }
                                missingAmount -= cards.getCount();
                                extracted = cards;
                            }
                            if (missingAmount <= 0) break;
                        }

                        if (missingAmount > 0 && !player.level().isClientSide()) {
                            player.displayClientMessage(
                                    PlayerMessages.MissingUpgrades.text(desiredStack.getItem().getDescription(), missingAmount),
                                    true
                            );
                        } else if (!extracted.isEmpty()) {
                            if (slot >= 0 && slot < upgrades.size()) {
                                upgrades.setItemDirect(slot, extracted);
                            } else {
                                upgrades.addItems(extracted);
                            }
                        }
                    }
                }
            }

            // 恢复其他升级卡（按 AE2 原逻辑）
            for (String key : desiredUpgradesTag.getAllKeys()) {
                ResourceLocation id;
                try {
                    id = new ResourceLocation(key);
                } catch (Exception ex) {
                    continue;
                }

                var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null || item.equals(ModItems.ENTITY_SPEED_CARD.get())) continue;

                int desiredCount = desiredUpgradesTag.getInt(key);
                if (desiredCount > 0) {
                    if (player.getAbilities().instabuild) {
                        // 创造模式：直接生成
                        ItemStack stack = new ItemStack(item, desiredCount);
                        upgrades.addItems(stack);
                    } else {
                        // 非创造模式：从背包或网络工具提取
                        int missingAmount = desiredCount;
                        ItemStack potential = new ItemStack(item, missingAmount);
                        ItemStack overflow = upgrades.addItems(potential, true);
                        if (!overflow.isEmpty()) {
                            missingAmount -= overflow.getCount();
                        }

                        for (var source : upgradeSources) {
                            ItemStack cards = source.removeItems(missingAmount, potential, null);
                            if (!cards.isEmpty()) {
                                overflow = upgrades.addItems(cards);
                                if (!overflow.isEmpty()) {
                                    player.getInventory().placeItemBackInInventory(overflow);
                                }
                                missingAmount -= cards.getCount();
                            }
                            if (missingAmount <= 0) break;
                        }

                        if (missingAmount > 0 && !player.level().isClientSide()) {
                            player.displayClientMessage(
                                    PlayerMessages.MissingUpgrades.text(item.getDescription(), missingAmount),
                                    true
                            );
                        }
                    }
                }
            }

            // 标记保存并通知升级变化
            if (upgradeableObject instanceof EntitySpeedTickerPart speedTickerPart) {
                BlockEntity be = speedTickerPart.getBlockEntity();
                if (be != null) {
                    be.setChanged();
                }
                speedTickerPart.upgradesChanged();
            }

            cir.setReturnValue(true);
        } catch (Exception e) {
            e.printStackTrace();
            cir.setReturnValue(false);
        }
    }
}