package com.extendedae_plus.mixin.advancedae.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import com.extendedae_plus.api.advancedBlocking.IAdvancedBlocking;
import com.extendedae_plus.api.advancedBlocking.IPatternProviderMenuAdvancedSync;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvPatternProviderMenu.class)
public abstract class AdvPatternProviderMenuAdvancedMixin implements IPatternProviderMenuAdvancedSync {
    @Final
    @Shadow(remap = false)
    protected AdvPatternProviderLogic logic;

    // 选择一个未占用的 GUI 同步 id（AE2 已用到 7），这里使用 21 以避冲突
    @Unique
    @GuiSync(20)
    public boolean eap$AdvancedBlocking = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncAdvancedBlocking(CallbackInfo ci) {
        // 避免@Shadow父类方法，改用公共API：AEBaseMenu#isClientSide()
        if (!((AEBaseMenu) (Object) this).isClientSide()) {
            var l = this.logic;
            if (l instanceof IAdvancedBlocking holder) {
                this.eap$AdvancedBlocking = holder.eap$getAdvancedBlocking();
            }
        }
    }

    // 构造器尾注入（public ctor）
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initAdvancedSync_Public(int id, Inventory playerInventory, AdvPatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof IAdvancedBlocking holder) {
                this.eap$AdvancedBlocking = holder.eap$getAdvancedBlocking();
            }
        } catch (Throwable ignored) {}
    }

    // 构造器尾注入（protected ctor with MenuType）
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initAdvancedSync_Protected(MenuType menuType, int id, Inventory playerInventory, AdvPatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof IAdvancedBlocking holder) {
                this.eap$AdvancedBlocking = holder.eap$getAdvancedBlocking();
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean eap$getAdvancedBlockingSynced() {
        return this.eap$AdvancedBlocking;
    }
}
