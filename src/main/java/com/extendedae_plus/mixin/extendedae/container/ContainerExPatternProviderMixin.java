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
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(value = ContainerExPatternProvider.class, priority = 3000, remap = false)
public abstract class ContainerExPatternProviderMixin extends PatternProviderMenu
        implements ExPatternProviderMenuPageBridge {

    // 使用高位唯一ID，避免与其他模组在同一类上的 @GuiSync 冲突
    @GuiSync(31415)
    @Unique
    public int eap$page = 0;

    @Unique
    public int eap$maxPage = 0;

    @GuiSync(31416)
    @Unique
    public int eap$availablePageCount = 1;

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位

    // glodium IActionHolder 已移除，相关 actionMap 由专用网络包替代。

    public ContainerExPatternProviderMixin(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Unique
    public void eap$showPage() {
        List<Slot> slots = this.getSlots(SlotSemantics.ENCODED_PATTERN);
        int totalSlots = slots.size();
        this.eap$maxPage = Math.max(1, (totalSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        int availablePages = Math.max(1, Math.min(this.eap$maxPage, this.eap$availablePageCount));
        this.eap$page = Math.max(0, Math.min(this.eap$page, availablePages - 1));

        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            Slot slot = slots.get(slotIndex);
            int pageId = slotIndex / SLOTS_PER_PAGE;
            boolean available = pageId < availablePages;
            AppEngSlot appEngSlot = (AppEngSlot) slot;
            appEngSlot.setSlotEnabled(available);
            appEngSlot.setActive(available && pageId == this.eap$page);
        }
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"), remap = false, require = 0)
    private void eap$initPages(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        int maxSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        this.eap$maxPage = (maxSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (this.isServerSide()) {
            this.eap$availablePageCount = this.eap$getDynamicPageCount();
        }
        this.eap$showPage();
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
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        this.eap$showPage();
    }

    @Unique
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
    private void eap$modifyPatterns(int scale, boolean div) {
        if (scale <= 0) return;
        for (var slot : this.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof EncodedPatternItem pattern) {
                var detail = PatternDetailsHelper.decodePattern(stack, this.getPlayer().level());
                if (detail instanceof AEProcessingPattern process) {
                    var input = process.getSparseInputs(); // List<GenericStack>
                    var output = process.getOutputs();      // List<GenericStack>
                    if (eap$checkModify(input, scale, div) && eap$checkModify(output, scale, div)) {
                        var mulInput = eap$modifyStacks(input, scale, div);
                        var mulOutput = eap$modifyStacks(output, scale, div);
                        var newPattern = PatternDetailsHelper.encodeProcessingPattern(mulInput, mulOutput);
                        slot.set(newPattern);
                    }
                }
            }
        }
    }

    @Unique
    private boolean eap$checkModify(java.util.List<GenericStack> stacks, int scale, boolean div) {
        if (stacks == null) return false;
        if (div) {
            for (var stack : stacks) {
                if (stack != null) {
                    if (stack.amount() % scale != 0) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            for (var stack : stacks) {
                if (stack != null) {
                    long upper = 999999L * stack.what().getAmountPerUnit();
                    if (stack.amount() * scale > upper) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    @Unique
    private java.util.List<GenericStack> eap$modifyStacks(java.util.List<GenericStack> src, int scale, boolean div) {
        var dst = new java.util.ArrayList<GenericStack>(src.size());
        for (var stack : src) {
            if (stack != null) {
                long amt = stack.amount();
                long newAmt = div ? (amt / scale) : (amt * scale);
                dst.add(new GenericStack(stack.what(), newAmt));
            } else {
                dst.add(null);
            }
        }
        return dst;
    }
}
