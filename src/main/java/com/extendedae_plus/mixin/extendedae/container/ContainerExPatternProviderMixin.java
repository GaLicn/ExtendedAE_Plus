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
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(value = ContainerExPatternProvider.class, priority = 3000, remap = false)
public abstract class ContainerExPatternProviderMixin extends PatternProviderMenu {

    // 使用高位唯一ID，避免与其他模组在同一类上的 @GuiSync 冲突
    @GuiSync(31415)
    @Unique
    public int eap$page = 0;

    @Unique
    public int eap$maxPage = 0;

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
        
        // 如果总槽位数不超过36个，不需要翻页
        if (totalSlots <= SLOTS_PER_PAGE) {
            for (Slot s : slots) {
                ((AppEngSlot) s).setActive(true);
            }
            return;
        }

        int slot_id = 0;

        for (Slot s : slots) {
            int page_id = slot_id / SLOTS_PER_PAGE;

            // 当前页的槽位激活
            // 其他页的槽位隐藏
            ((AppEngSlot) s).setActive(page_id == this.eap$page);
            ++slot_id;
        }
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"), remap = false, require = 0)
    private void eap$initPages(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        int maxSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        this.eap$maxPage = (maxSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
    }

    @Unique
    public int getPage() {
        return this.eap$page;
    }

    @Unique
    public void setPage(int page) {
        this.eap$page = page;
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
    private boolean eap$checkModify(List<GenericStack> stacks, int scale, boolean div) {
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
                    long amt = stack.amount();
                    // 先检查乘法是否会导致超出 Long.MAX_VALUE，避免溢出
                    if (amt > Integer.MAX_VALUE / scale) {
                        return false;
                    }
                    // 已移除原有的业务上限检查（999999 * amountPerUnit），仅保留溢出检查
                }
            }
            return true;
        }
    }

    @Unique
    private List<GenericStack> eap$modifyStacks(List<GenericStack> src, int scale, boolean div) {
        var dst = new ArrayList<GenericStack>(src.size());
        for (var stack : src) {
            if (stack != null) {
                long amt = stack.amount();
                long newAmt;
                if (div) { // 如果是除法操作
                    newAmt = amt / scale; // 执行除法
                } else { // 如果是乘法操作
                    // 防御性检查：确保不会发生溢出，尽管上层应已验证
                    if (amt > Integer.MAX_VALUE / scale) {
                        // 遇到潜在溢出时跳过，保持为 null；调用方应通过 eap$checkModify 避免此分支
                        dst.add(null);
                        continue;
                    }
                    newAmt = amt * scale; // 执行乘法
                }
                dst.add(new GenericStack(stack.what(), newAmt));
            } else {
                dst.add(null);
            }
        }
        return dst;
    }
}