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

@Mixin(value = ContainerExPatternProvider.class, priority = 1001)
public abstract class ContainerExPatternProviderMixin extends PatternProviderMenu {

    @GuiSync(11451)
    @Unique
    public int page = 0;

    @Unique
    public int maxPage = 0;

    public ContainerExPatternProviderMixin(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(menuType, id, playerInventory, host);
    }

    @Unique
    public void showPage() {
        List<Slot> slots = this.getSlots(SlotSemantics.ENCODED_PATTERN);
        int slot_id = 0;

        for (Slot s : slots) {
            int page_id = slot_id / 36;

            if (page_id > 0 && page_id == this.page) {
                // 使用反射修改槽位位置
                try {
                    Field xField = Slot.class.getDeclaredField("x");
                    Field yField = Slot.class.getDeclaredField("y");
                    xField.setAccessible(true);
                    yField.setAccessible(true);
                    
                    Slot baseSlot = slots.get(slot_id % 36);
                    xField.set(s, xField.get(baseSlot));
                    yField.set(s, yField.get(baseSlot));
                } catch (Exception e) {
                    // 忽略反射错误
                }
            }

            ((AppEngSlot) s).setActive(page_id == this.page);
            ++slot_id;
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    public void init(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        int maxSlots = this.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        this.maxPage = (maxSlots + 36 - 1) / 36;
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