package com.extendedae_plus.mixin;

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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(value = ContainerExPatternProvider.class, priority = 3000)
public abstract class ContainerExPatternProviderMixin extends PatternProviderMenu {

    @GuiSync(11451)
    @Unique
    public int page = 0;

    @Unique
    public int maxPage = 0;

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位

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

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    public void init(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        int maxSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        this.maxPage = (maxSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
    }

    @Unique
    public int getPage() {
        return this.page;
    }

    @Unique
    public void setPage(int page) {
        this.page = page;
    }
} 