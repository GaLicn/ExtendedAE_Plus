package com.extendedae_plus.mixin.ae2.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = AEBaseMenu.class, priority = 2000, remap = false)
public abstract class AEBaseMenuExtendedPatternProviderCardGuardMixin {

    @Unique
    private static final int EAP$SLOTS_PER_PAGE = 36;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void eap$preventRemovingRequiredExpansionCard(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        AEBaseMenu menu = (AEBaseMenu) (Object) this;
        if (!(menu instanceof ContainerExPatternProvider)
                || slotId < 0
                || slotId >= menu.slots.size()
                || player == null
                || !this.eap$isExpansionCardRemovalClick(clickType)) {
            return;
        }

        Slot clickedSlot = menu.slots.get(slotId);
        if (!menu.getSlots(SlotSemantics.UPGRADE).contains(clickedSlot)) {
            return;
        }

        ItemStack stack = clickedSlot.getItem();
        if (!UpgradeSlotCompat.isExtendedPatternProviderExpansionCard(stack)) {
            return;
        }

        int currentCards = this.eap$countInstalledExpansionCards();
        int requiredCards = this.eap$getRequiredExpansionCards();
        if (currentCards - 1 < requiredCards) {
            int remainingUnlockedSlots = Math.max(0, currentCards * EAP$SLOTS_PER_PAGE);
            if (this.eap$tryCompactPatternsForCardRemoval(remainingUnlockedSlots)) {
                requiredCards = this.eap$getRequiredExpansionCards();
            }
        }

        if (currentCards - 1 < requiredCards) {
            player.displayClientMessage(Component.translatable(
                    "extendedae_plus.message.pattern_provider.expansion_card_locked"), true);
            ci.cancel();
        }
    }

    @Unique
    private boolean eap$isExpansionCardRemovalClick(ClickType clickType) {
        return clickType == ClickType.PICKUP
                || clickType == ClickType.QUICK_MOVE
                || clickType == ClickType.SWAP
                || clickType == ClickType.THROW
                || clickType == ClickType.PICKUP_ALL;
    }

    @Unique
    private int eap$countInstalledExpansionCards() {
        AEBaseMenu menu = (AEBaseMenu) (Object) this;
        int count = 0;
        for (Slot slot : menu.getSlots(SlotSemantics.UPGRADE)) {
            if (UpgradeSlotCompat.isExtendedPatternProviderExpansionCard(slot.getItem())) {
                count++;
            }
        }
        return count;
    }

    @Unique
    private int eap$getRequiredExpansionCards() {
        AEBaseMenu menu = (AEBaseMenu) (Object) this;
        int highestUsedSlot = -1;
        List<Slot> patternSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        for (int i = 0; i < patternSlots.size(); i++) {
            if (!patternSlots.get(i).getItem().isEmpty()) {
                highestUsedSlot = i;
            }
        }

        if (highestUsedSlot < 0) {
            return 0;
        }

        int requiredPages = highestUsedSlot / EAP$SLOTS_PER_PAGE + 1;
        return Math.max(0, requiredPages - 1);
    }

    @Unique
    private boolean eap$tryCompactPatternsForCardRemoval(int remainingUnlockedSlots) {
        AEBaseMenu menu = (AEBaseMenu) (Object) this;
        List<Slot> patternSlots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        if (patternSlots.isEmpty()) {
            return true;
        }

        int unlockedLimit = Math.max(0, Math.min(patternSlots.size(), remainingUnlockedSlots));
        if (unlockedLimit >= patternSlots.size()) {
            return true;
        }

        int nextEmptyUnlockedSlot = 0;
        for (int i = unlockedLimit; i < patternSlots.size(); i++) {
            ItemStack stack = patternSlots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }

            while (nextEmptyUnlockedSlot < unlockedLimit
                    && !patternSlots.get(nextEmptyUnlockedSlot).getItem().isEmpty()) {
                nextEmptyUnlockedSlot++;
            }

            if (nextEmptyUnlockedSlot >= unlockedLimit) {
                return false;
            }

            // 先把高页样板挪到保留页空位，再允许取卡，避免样板被锁在不可访问槽位。
            patternSlots.get(nextEmptyUnlockedSlot).set(stack.copy());
            patternSlots.get(i).set(ItemStack.EMPTY);
            nextEmptyUnlockedSlot++;
        }

        return true;
    }
}
