package com.extendedae_plus.mixin.extendedae;

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
public abstract class ContainerExPatternProviderMixin extends PatternProviderMenu implements IActionHolder {

    @GuiSync(11451)
    @Unique
    public int page = 0;

    @Unique
    public int maxPage = 0;

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位

    @Unique
    private final Map<String, Consumer<Paras>> actions = createHolder();

    public ContainerExPatternProviderMixin(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Unique
    public void showPage() {
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

            if (page_id == this.page) {
                // 当前页的槽位激活
                ((AppEngSlot) s).setActive(true);
            } else {
                // 其他页的槽位隐藏
                ((AppEngSlot) s).setActive(false);
            }
            ++slot_id;
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        int maxSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        this.maxPage = (maxSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;

        // 注册通用动作（供 CGenericPacket 分发）
        this.actions.put("multiply2", p -> { modifyPatterns(2, false); });
        this.actions.put("divide2",   p -> { modifyPatterns(2, true);  });
        this.actions.put("multiply5", p -> { modifyPatterns(5, false); });
        this.actions.put("divide5",   p -> { modifyPatterns(5, true);  });
        this.actions.put("multiply10",p -> { modifyPatterns(10, false);});
        this.actions.put("divide10",  p -> { modifyPatterns(10, true); });
    }

    @Unique
    public int getPage() {
        return this.page;
    }

    @Unique
    public void setPage(int page) {
        this.page = page;
    }

    @Unique
    private void modifyPatterns(int scale, boolean div) {
        if (scale <= 0) return;
        for (var slot : this.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof EncodedPatternItem pattern) {
                var detail = pattern.decode(stack, this.getPlayer().level(), false);
                if (detail instanceof AEProcessingPattern process) {
                    var input = process.getSparseInputs();
                    var output = process.getOutputs();
                    if (checkModify(input, scale, div) && checkModify(output, scale, div)) {
                        var mulInput = new GenericStack[input.length];
                        var mulOutput = new GenericStack[output.length];
                        modifyStacks(input, mulInput, scale, div);
                        modifyStacks(output, mulOutput, scale, div);
                        var newPattern = PatternDetailsHelper.encodeProcessingPattern(mulInput, mulOutput);
                        slot.set(newPattern);
                    }
                }
            }
        }
    }

    @Unique
    private boolean checkModify(GenericStack[] stacks, int scale, boolean div) {
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
    private void modifyStacks(GenericStack[] src, GenericStack[] dst, int scale, boolean div) {
        for (int i = 0; i < src.length; i++) {
            var stack = src[i];
            if (stack != null) {
                long amt = stack.amount();
                long newAmt = div ? (amt / scale) : (amt * scale);
                dst[i] = new GenericStack(stack.what(), newAmt);
            } else {
                dst[i] = null;
            }
        }
    }

    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.actions;
    }
}