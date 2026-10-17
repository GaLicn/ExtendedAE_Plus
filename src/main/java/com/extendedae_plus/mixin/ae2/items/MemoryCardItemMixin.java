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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

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
            var desiredUpgrades = new IdentityHashMap<Item, Integer>();
            var desiredEntitySpeedCards = new ArrayList<ItemStack>();

            // 收集背包和网络工具作为升级卡来源
            var upgradeSources = new ArrayList<InternalInventory>();
            upgradeSources.add(new PlayerInternalInventory(player.getInventory()));
            var networkTool = NetworkToolItem.findNetworkToolInv(player);
            if (networkTool != null) {
                upgradeSources.add(networkTool.getInventory());
            }

            if (desiredUpgradesTag.contains("entity_speed_cards", Tag.TAG_LIST)) {
                ListTag entitySpeedCards = desiredUpgradesTag.getList("entity_speed_cards", Tag.TAG_COMPOUND);
                for (int i = 0; i < entitySpeedCards.size(); i++) {
                    ItemStack desiredStack = ItemStack.of(entitySpeedCards.getCompound(i));
                    if (!desiredStack.isEmpty()) {
                        desiredEntitySpeedCards.add(desiredStack);
                    }
                }
            }

            for (String key : desiredUpgradesTag.getAllKeys()) {
                if ("entity_speed_cards".equals(key)) {
                    continue;
                }

                ResourceLocation id;
                try {
                    id = new ResourceLocation(key);
                } catch (Exception ex) {
                    continue;
                }

                var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null || item.equals(ModItems.ENTITY_SPEED_CARD.get())) {
                    continue;
                }

                int desiredCount = desiredUpgradesTag.getInt(key);
                if (desiredCount > 0) {
                    desiredUpgrades.put(item, desiredCount);
                }
            }

            if (player.getAbilities().instabuild) {
                for (int i = 0; i < upgrades.size(); i++) {
                    upgrades.setItemDirect(i, ItemStack.EMPTY);
                }
                for (var desiredStack : desiredEntitySpeedCards) {
                    upgrades.addItems(desiredStack.copy());
                }
                for (var entry : desiredUpgrades.entrySet()) {
                    upgrades.addItems(new ItemStack(entry.getKey(), entry.getValue()));
                }
                markUpgradesChanged(upgradeableObject);
                cir.setReturnValue(true);
                return;
            }

            // 先精确移除多余的实体加速卡（按完整 NBT 匹配）
            var desiredEntityStacks = new ArrayList<ItemStack>();
            var desiredEntityCounts = new ArrayList<Integer>();
            for (var desiredStack : desiredEntitySpeedCards) {
                mergeExactCount(desiredEntityStacks, desiredEntityCounts, desiredStack, desiredStack.getCount());
            }

            var currentEntityStacks = new ArrayList<ItemStack>();
            var currentEntityCounts = new ArrayList<Integer>();
            collectInstalledEntitySpeedCards(upgrades, currentEntityStacks, currentEntityCounts);
            for (int i = 0; i < upgrades.size(); i++) {
                ItemStack stack = upgrades.getStackInSlot(i);
                if (stack.isEmpty() || !stack.getItem().equals(ModItems.ENTITY_SPEED_CARD.get())) {
                    continue;
                }

                int installed = getExactCount(currentEntityStacks, currentEntityCounts, stack);
                int desired = getExactCount(desiredEntityStacks, desiredEntityCounts, stack);
                int excess = installed - desired;
                if (excess <= 0) {
                    continue;
                }

                ItemStack removed = upgrades.extractItem(i, Math.min(excess, stack.getCount()), false);
                if (removed.isEmpty()) {
                    continue;
                }

                decrementExactCount(currentEntityStacks, currentEntityCounts, stack, removed.getCount());
                for (var source : upgradeSources) {
                    if (!removed.isEmpty()) {
                        removed = source.addItems(removed);
                    }
                }
                if (!removed.isEmpty()) {
                    player.drop(removed, false);
                }
            }

            // 按 AE2 原逻辑移除多余的其他升级卡
            for (int i = 0; i < upgrades.size(); i++) {
                var current = upgrades.getStackInSlot(i);
                if (current.isEmpty() || current.getItem().equals(ModItems.ENTITY_SPEED_CARD.get())) {
                    continue;
                }

                var desiredCount = desiredUpgrades.getOrDefault(current.getItem(), 0);
                var totalInstalled = upgradeableObject.getInstalledUpgrades(current.getItem());
                var toRemove = totalInstalled - desiredCount;
                if (toRemove > 0) {
                    var removed = upgrades.extractItem(i, toRemove, false);

                    for (var upgradeSource : upgradeSources) {
                        if (!removed.isEmpty()) {
                            removed = upgradeSource.addItems(removed);
                        }
                    }
                    if (!removed.isEmpty()) {
                        player.drop(removed, false);
                    }
                }
            }

            // 精确补回缺失的实体加速卡（按完整 NBT 匹配）
            var afterRemovalEntityStacks = new ArrayList<ItemStack>();
            var afterRemovalEntityCounts = new ArrayList<Integer>();
            collectInstalledEntitySpeedCards(upgrades, afterRemovalEntityStacks, afterRemovalEntityCounts);
            for (int i = 0; i < desiredEntityStacks.size(); i++) {
                var desiredStack = desiredEntityStacks.get(i);
                int desiredCount = desiredEntityCounts.get(i);
                int missingAmount = desiredCount - getExactCount(afterRemovalEntityStacks, afterRemovalEntityCounts, desiredStack);
                if (missingAmount <= 0) {
                    continue;
                }

                var potential = desiredStack.copy();
                potential.setCount(missingAmount);
                var overflow = upgrades.addItems(potential, true);
                if (!overflow.isEmpty()) {
                    missingAmount -= overflow.getCount();
                }
                if (missingAmount <= 0) {
                    continue;
                }

                for (var source : upgradeSources) {
                    var preciseRequest = desiredStack.copy();
                    preciseRequest.setCount(missingAmount);
                    var cards = source.removeItems(missingAmount, preciseRequest, null);
                    if (!cards.isEmpty()) {
                        overflow = upgrades.addItems(cards);
                        if (!overflow.isEmpty()) {
                            player.getInventory().placeItemBackInInventory(overflow);
                        }
                        missingAmount -= cards.getCount();
                    }
                    if (missingAmount <= 0) {
                        break;
                    }
                }

                if (missingAmount > 0 && !player.level().isClientSide()) {
                    player.displayClientMessage(
                            PlayerMessages.MissingUpgrades.text(desiredStack.getHoverName(), missingAmount),
                            true
                    );
                }
            }

            // 恢复其他升级卡（按 AE2 原逻辑）
            for (var entry : desiredUpgrades.entrySet()) {
                int missingAmount = entry.getValue() - upgradeableObject.getInstalledUpgrades(entry.getKey());
                if (missingAmount > 0) {
                    ItemStack potential = new ItemStack(entry.getKey(), missingAmount);
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
                                PlayerMessages.MissingUpgrades.text(entry.getKey().getDescription(), missingAmount),
                                true
                        );
                    }
                }
            }

            markUpgradesChanged(upgradeableObject);
            cir.setReturnValue(true);
        } catch (Exception e) {
            e.printStackTrace();
            cir.setReturnValue(false);
        }
    }

    private static void markUpgradesChanged(IUpgradeableObject upgradeableObject) {
        if (upgradeableObject instanceof EntitySpeedTickerPart speedTickerPart) {
            BlockEntity be = speedTickerPart.getBlockEntity();
            if (be != null) {
                be.setChanged();
            }
            speedTickerPart.upgradesChanged();
        }
    }

    private static void collectInstalledEntitySpeedCards(InternalInventory upgrades, List<ItemStack> stacks,
                                                         List<Integer> counts) {
        for (int i = 0; i < upgrades.size(); i++) {
            var stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem().equals(ModItems.ENTITY_SPEED_CARD.get())) {
                mergeExactCount(stacks, counts, stack, stack.getCount());
            }
        }
    }

    private static void mergeExactCount(List<ItemStack> stacks, List<Integer> counts, ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return;
        }

        for (int i = 0; i < stacks.size(); i++) {
            if (sameExactStack(stacks.get(i), stack)) {
                counts.set(i, counts.get(i) + amount);
                return;
            }
        }
        stacks.add(copySingle(stack));
        counts.add(amount);
    }

    private static int getExactCount(List<ItemStack> stacks, List<Integer> counts, ItemStack stack) {
        for (int i = 0; i < stacks.size(); i++) {
            if (sameExactStack(stacks.get(i), stack)) {
                return counts.get(i);
            }
        }
        return 0;
    }

    private static void decrementExactCount(List<ItemStack> stacks, List<Integer> counts, ItemStack stack, int amount) {
        if (amount <= 0) {
            return;
        }

        for (int i = 0; i < stacks.size(); i++) {
            if (sameExactStack(stacks.get(i), stack)) {
                int remaining = counts.get(i) - amount;
                if (remaining <= 0) {
                    stacks.remove(i);
                    counts.remove(i);
                } else {
                    counts.set(i, remaining);
                }
                return;
            }
        }
    }

    private static boolean sameExactStack(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    private static ItemStack copySingle(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
