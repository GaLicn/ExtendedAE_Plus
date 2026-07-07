package com.extendedae_plus.client.screen;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.helpers.InventoryAction;
import appeng.util.inv.AppEngInternalInventory;
import com.extendedae_plus.client.screen.widget.SuperAssemblerMatrixSlot;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.menu.SuperAssemblerMatrixMenu;
import com.extendedae_plus.network.SuperAssemblerMatrixActionC2SPacket;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SuperAssemblerMatrixScreen extends AEBaseScreen<SuperAssemblerMatrixMenu> {

    private static final int ROW_HEIGHT = 18;
    private static final int GUI_PADDING_X = 8;
    private static final int SLOT_SIZE = 18;
    private static final ResourceLocation BG = AppEng.makeId("textures/guis/assembler_matrix.png");
    private static final Rect2i EMPTY_ROW1 = new Rect2i(0, 199, 162, 18);
    private static final Rect2i EMPTY_ROW2 = new Rect2i(0, 217, 162, 18);
    private static final Rect2i EMPTY_ROW3 = new Rect2i(0, 235, 162, 18);

    private final Scrollbar scrollbar;
    private final Long2ReferenceMap<PatternInfo> infos = new Long2ReferenceOpenHashMap<>();
    private final Set<ItemStack> matchedStack = new ObjectOpenCustomHashSet<>(new Hash.Strategy<>() {
        @Override
        public int hashCode(ItemStack stack) {
            return stack.getItem().hashCode() ^ (stack.hasTag() ? stack.getTag().hashCode() : 0xFFFFFFFF);
        }

        @Override
        public boolean equals(ItemStack a, ItemStack b) {
            return a == b || (a != null && b != null && ItemStack.isSameItemSameTags(a, b));
        }
    });
    private final ArrayList<PatternRow> rows = new ArrayList<>();
    private final AETextField searchField;
    private long concurrentExecutions;

    public SuperAssemblerMatrixScreen(SuperAssemblerMatrixMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.scrollbar = this.widgets.addScrollBar("scrollbar");
        this.searchField = this.widgets.addTextField("search");
        this.searchField.setResponder(str -> this.refreshList());
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.searchField.setTooltipMessage(Collections.singletonList(
                Component.translatable("gui.extendedae_plus.super_assembler_matrix.tooltip")));

        var cancel = new ActionEPPButton(
                button -> ModNetwork.CHANNEL.sendToServer(new SuperAssemblerMatrixActionC2SPacket("cancel")),
                Icon.CLEAR
        );
        cancel.setMessage(Component.translatable("gui.extendedae_plus.super_assembler_matrix.cancel"));
        this.addToLeftToolbar(cancel);
    }

    @Override
    public void init() {
        super.init();
        this.setInitialFocus(this.searchField);
        this.resetScrollbar();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && this.searchField.isMouseOver(mouseX, mouseY)) {
            this.searchField.setValue("");
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        this.menu.slots.removeIf(slot -> slot instanceof SuperAssemblerMatrixSlot);
        int textColor = this.style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        int scrollLevel = this.scrollbar.getCurrentScroll();
        for (int i = 0; i < 4; i++) {
            if (scrollLevel + i < this.rows.size()) {
                var row = this.rows.get(scrollLevel + i);
                for (int col = 0; col < row.slots; col++) {
                    var slot = new SuperAssemblerMatrixSlot(row.inventory, col, row.offset, row.id,
                            col * SLOT_SIZE + GUI_PADDING_X, (i + 1) * SLOT_SIZE + 13);
                    this.menu.slots.add(slot);
                    if (!this.searchField.getValue().isEmpty()) {
                        if (this.matchedStack.contains(slot.getStoredStack())) {
                            fillRect(guiGraphics, new Rect2i(slot.x, slot.y, 16, 16), 0x8A00FF00);
                        } else {
                            fillRect(guiGraphics, new Rect2i(slot.x, slot.y, 16, 16), 0x6A000000);
                        }
                    }
                }
            }
        }
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.extendedae_plus.super_assembler_matrix.concurrent",
                        this.concurrentExecutions),
                80, 19,
                textColor, false
        );
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        int size = this.rows.size();
        if (size < 4) {
            while (size < 4) {
                if (size == 0) {
                    blit(guiGraphics, offsetX + GUI_PADDING_X - 1, offsetY + SLOT_SIZE * size + 30, EMPTY_ROW1);
                } else if (size == 3) {
                    blit(guiGraphics, offsetX + GUI_PADDING_X - 1, offsetY + SLOT_SIZE * size + 30, EMPTY_ROW3);
                } else {
                    blit(guiGraphics, offsetX + GUI_PADDING_X - 1, offsetY + SLOT_SIZE * size + 30, EMPTY_ROW2);
                }
                size++;
            }
        }
    }

    @Override
    protected void slotClicked(Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        if (slot instanceof SuperAssemblerMatrixSlot matrixSlot) {
            InventoryAction action = null;
            switch (clickType) {
                case PICKUP -> action = mouseButton == 1
                        ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                        : InventoryAction.PICKUP_OR_SET_DOWN;
                case QUICK_MOVE -> action = mouseButton == 1
                        ? InventoryAction.PICKUP_SINGLE
                        : InventoryAction.SHIFT_CLICK;
                case CLONE -> {
                    if (this.getPlayer().getAbilities().instabuild) {
                        action = InventoryAction.CREATIVE_DUPLICATE;
                    }
                }
                default -> {
                }
            }
            if (action != null) {
                NetworkHandler.instance().sendToServer(new InventoryActionPacket(
                        action, matrixSlot.getActuallySlot(), matrixSlot.getID()));
            }
            return;
        }
        super.slotClicked(slot, slotIdx, mouseButton, clickType);
    }

    public void receiveUpdate(long id, int inventorySize, Int2ObjectMap<ItemStack> updateMap) {
        var info = this.infos.computeIfAbsent(id, ignored -> new PatternInfo(id, inventorySize));
        for (var entry : updateMap.int2ObjectEntrySet()) {
            var row = info.getRowBySlot(entry.getIntKey());
            row.setItemByInvSlot(entry.getIntKey(), entry.getValue());
        }
        this.refreshList();
    }

    public void setConcurrentExecutions(long concurrentExecutions) {
        this.concurrentExecutions = concurrentExecutions;
    }

    private void blit(GuiGraphics guiGraphics, int offsetX, int offsetY, Rect2i srcRect) {
        guiGraphics.blit(BG, offsetX, offsetY, srcRect.getX(), srcRect.getY(),
                srcRect.getWidth(), srcRect.getHeight());
    }

    private void resetScrollbar() {
        this.scrollbar.setHeight(4 * ROW_HEIGHT - 2);
        this.scrollbar.setRange(0, this.rows.size() - 4, 2);
    }

    private void refreshList() {
        this.rows.clear();
        this.matchedStack.clear();
        for (var id : this.getSortedInfo()) {
            var info = this.infos.get(id);
            for (var row : info.internalRows) {
                if (this.filterRows(row)) {
                    this.rows.add(row);
                }
            }
        }
        this.resetScrollbar();
    }

    private boolean filterRows(PatternRow row) {
        var filter = this.searchField.getValue();
        if (filter.isBlank()) {
            return true;
        }
        var token = tokenize(filter);
        boolean anyMatch = false;
        for (var stack : row.inventory) {
            if (this.itemStackMatchesSearchTerm(stack, token)) {
                anyMatch = true;
            }
        }
        return anyMatch;
    }

    private boolean itemStackMatchesSearchTerm(ItemStack stack, List<String> searchTokens) {
        IPatternDetails details = null;
        if (stack.getItem() instanceof EncodedPatternItem) {
            details = PatternDetailsHelper.decodePattern(stack, this.menu.getPlayer().level());
        }
        if (details == null) {
            return false;
        }
        for (var output : details.getOutputs()) {
            if (output != null) {
                var nameTokens = tokenize(output.what().getDisplayName().getString());
                if (compareTokens(searchTokens, nameTokens)) {
                    this.matchedStack.add(stack);
                    return true;
                }
            }
        }
        for (var input : details.getInputs()) {
            if (input != null && input.getPossibleInputs().length > 0) {
                var nameTokens = tokenize(input.getPossibleInputs()[0].what().getDisplayName().getString());
                if (compareTokens(searchTokens, nameTokens)) {
                    this.matchedStack.add(stack);
                    return true;
                }
            }
        }
        return false;
    }

    private long[] getSortedInfo() {
        return this.infos.keySet().longStream().sorted().toArray();
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var tokens = new ArrayList<String>();
        for (var token : text.trim().toLowerCase().split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }

    private static boolean compareTokens(List<String> filter, List<String> target) {
        int start = 0;
        while (start <= target.size() - filter.size()) {
            int current = start;
            int matched = 0;
            while (current < target.size() && matched < filter.size()) {
                if (target.get(current).contains(filter.get(matched))) {
                    matched++;
                }
                current++;
            }
            if (matched >= filter.size()) {
                return true;
            }
            start++;
        }
        return false;
    }

    private static class PatternInfo {
        private final List<PatternRow> internalRows = new ArrayList<>();

        private PatternInfo(long id, int inventorySize) {
            int left = inventorySize;
            int offset = 0;
            do {
                int slots = Math.min(left, 9);
                this.internalRows.add(new PatternRow(id, offset, slots));
                left -= slots;
                offset += slots;
            } while (left > 0);
        }

        private PatternRow getRowBySlot(int slot) {
            return this.internalRows.get(slot / 9);
        }
    }

    private static class PatternRow {
        private final AppEngInternalInventory inventory;
        private final long id;
        private final int offset;
        private final int slots;

        private PatternRow(long id, int offset, int slots) {
            this.id = id;
            this.offset = offset;
            this.slots = slots;
            this.inventory = new AppEngInternalInventory(slots);
        }

        private void setItemByInvSlot(int slot, ItemStack stack) {
            this.inventory.setItemDirect(slot - this.offset, stack);
        }
    }
}
