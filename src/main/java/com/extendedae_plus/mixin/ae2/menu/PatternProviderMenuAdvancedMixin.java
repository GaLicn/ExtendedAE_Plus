package com.extendedae_plus.mixin.ae2.menu;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.AdvancedBlockingHolder;
import com.extendedae_plus.api.PatternProviderMenuAdvancedSync;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuAdvancedMixin implements PatternProviderMenuAdvancedSync {
    @Final
    @Shadow(remap = false)
    protected PatternProviderLogic logic;

    // 选择一个未占用的 GUI 同步 id（AE2 已用到 7），这里使用 20 以避冲突
    @Unique
    @GuiSync(20)
    public boolean eap$AdvancedBlocking = false;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void eap$syncAdvancedBlocking(CallbackInfo ci) {
        // 避免@Shadow父类方法，改用公共API：AEBaseMenu#isClientSide()
        if (!((AEBaseMenu) (Object) this).isClientSide()) {
            var l = this.logic;
            if (l instanceof AdvancedBlockingHolder holder) {
                this.eap$AdvancedBlocking = holder.eap$getAdvancedBlocking();
            }
        }
    }

    // 构造器尾注入（public ctor）
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initAdvancedSync_Public(int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof AdvancedBlockingHolder holder) {
                this.eap$AdvancedBlocking = holder.eap$getAdvancedBlocking();
            }
        } catch (Throwable ignored) {}
    }

    // 构造器尾注入（protected ctor with MenuType）
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V", at = @At("TAIL"), remap = false)
    private void eap$initAdvancedSync_Protected(MenuType<? extends PatternProviderMenu> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        try {
            var l = this.logic;
            if (l instanceof AdvancedBlockingHolder holder) {
                this.eap$AdvancedBlocking = holder.eap$getAdvancedBlocking();
            }
        } catch (Throwable t) {
            LOGGER.error("Error initializing advanced sync", t);
        }
    }

    @Override
    public boolean eap$getAdvancedBlockingSynced() {
        return this.eap$AdvancedBlocking;
    }
}
