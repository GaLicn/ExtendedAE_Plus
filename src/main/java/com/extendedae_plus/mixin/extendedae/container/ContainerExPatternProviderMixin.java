package com.extendedae_plus.mixin.extendedae.container;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;
import com.extendedae_plus.api.bridge.ExPatternProviderMenuPageBridge;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(value = ContainerExPatternProvider.class, priority = 3000)
public abstract class ContainerExPatternProviderMixin extends PatternProviderMenu
        implements IActionHolder, ExPatternProviderMenuPageBridge {

    @Unique
    private static final int SLOTS_PER_PAGE = 36;

    @GuiSync(31415)
    @Unique
    private int eap$page = 0;

    @Unique
    private int eap$maxPage = 1;

    @GuiSync(31416)
    @Unique
    private int eap$availablePageCount = 1;

    @Unique
    private final Map<String, Consumer<Paras>> eap$actions = createHolder();

    public ContainerExPatternProviderMixin(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, PatternProviderLogicHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$init(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        this.eap$actions.put("multiply2", p -> eap$modifyPatterns(2, false));
        this.eap$actions.put("divide2", p -> eap$modifyPatterns(2, true));
        this.eap$actions.put("multiply5", p -> eap$modifyPatterns(5, false));
        this.eap$actions.put("divide5", p -> eap$modifyPatterns(5, true));
        this.eap$actions.put("multiply10", p -> eap$modifyPatterns(10, false));
        this.eap$actions.put("divide10", p -> eap$modifyPatterns(10, true));

        int totalSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        this.eap$maxPage = Math.max(1, (totalSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        if (this.isServerSide()) {
            this.eap$availablePageCount = this.eap$getDynamicPageCount();
        }
        this.eap$showPage();
    }

    @Unique
    private void eap$showPage() {
        List<Slot> patternSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN);
        int totalSlots = patternSlots.size();
        this.eap$maxPage = Math.max(1, (totalSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        int availablePages = Math.max(1, Math.min(this.eap$maxPage, this.eap$availablePageCount));
        this.eap$page = Math.max(0, Math.min(this.eap$page, availablePages - 1));

        for (int i = 0; i < patternSlots.size(); i++) {
            Slot slot = patternSlots.get(i);
            if (!(slot instanceof AppEngSlot appEngSlot)) {
                continue;
            }

            int pageId = i / SLOTS_PER_PAGE;
            boolean available = pageId < availablePages;
            appEngSlot.setSlotEnabled(available);
            appEngSlot.setActive(available && pageId == this.eap$page);
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            this.eap$availablePageCount = this.eap$getDynamicPageCount();
        }
        this.eap$showPage();
        super.broadcastChanges();
    }

    @Override
    public void onServerDataSync() {
        super.onServerDataSync();
        this.eap$showPage();
    }

    @Override
    public int eap$getPage() {
        return this.eap$page;
    }

    @Override
    public int eap$getAvailablePageCount() {
        return Math.max(1, Math.min(this.eap$maxPage, this.eap$availablePageCount));
    }

    @Override
    public void eap$setPage(int page) {
        this.eap$page = page;
        this.eap$showPage();
    }

    @Unique
    private int eap$getDynamicPageCount() {
        int exposedSlots = this.logic.getPatternInv().size();
        return Math.max(1, (exposedSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
    }

    @Unique
    private void eap$modifyPatterns(int scale, boolean divide) {
        if (scale <= 0) {
            return;
        }

        for (Slot slot : this.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (!(stack.getItem() instanceof EncodedPatternItem patternItem)) {
                continue;
            }

            var details = patternItem.decode(stack, this.getPlayer().level(), false);
            if (!(details instanceof AEProcessingPattern process)) {
                continue;
            }

            GenericStack[] inputs = process.getSparseInputs();
            GenericStack[] outputs = process.getOutputs();
            if (!eap$canScale(inputs, scale, divide) || !eap$canScale(outputs, scale, divide)) {
                continue;
            }

            GenericStack[] scaledInputs = new GenericStack[inputs.length];
            GenericStack[] scaledOutputs = new GenericStack[outputs.length];
            eap$scaleStacks(inputs, scaledInputs, scale, divide);
            eap$scaleStacks(outputs, scaledOutputs, scale, divide);
            slot.set(PatternDetailsHelper.encodeProcessingPattern(scaledInputs, scaledOutputs));
        }
    }

    @Unique
    private boolean eap$canScale(GenericStack[] stacks, int scale, boolean divide) {
        if (stacks == null) {
            return false;
        }

        if (divide) {
            for (GenericStack stack : stacks) {
                if (stack != null && stack.amount() % scale != 0) {
                    return false;
                }
            }
            return true;
        }

        for (GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            long amount = stack.amount();
            if (amount > Integer.MAX_VALUE / scale) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private void eap$scaleStacks(GenericStack[] source, GenericStack[] target, int scale, boolean divide) {
        for (int i = 0; i < source.length; i++) {
            GenericStack stack = source[i];
            if (stack == null) {
                target[i] = null;
                continue;
            }

            long amount = divide ? stack.amount() / scale : stack.amount() * scale;
            target[i] = new GenericStack(stack.what(), amount);
        }
    }

    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.eap$actions;
    }
}
