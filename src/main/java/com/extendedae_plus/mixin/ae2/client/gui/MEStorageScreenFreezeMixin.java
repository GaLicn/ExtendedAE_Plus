package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.me.common.MEStorageMenu;
import com.extendedae_plus.api.client.IRepoUpdateFreezeAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class MEStorageScreenFreezeMixin<C extends MEStorageMenu> extends AEBaseScreen<C> {
    @Shadow
    @Final
    protected Repo repo;

    @Unique
    private ToggleButton eap$freezeUpdatesButton;

    public MEStorageScreenFreezeMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void eap$addFreezeUpdatesButton(C menu, Inventory playerInventory, Component title, ScreenStyle style,
            CallbackInfo ci) {
        this.eap$freezeUpdatesButton = this.addToLeftToolbar(new ToggleButton(
                Icon.LOCKED,
                Icon.UNLOCKED,
                frozen -> ((IRepoUpdateFreezeAccess) this.repo).eap$setUpdatesFrozen(frozen)));
        this.eap$freezeUpdatesButton.setTooltipOn(List.of(
                Component.translatable("gui.extendedae_plus.terminal.resume_updates"),
                Component.translatable("gui.extendedae_plus.terminal.resume_updates.tooltip")));
        this.eap$freezeUpdatesButton.setTooltipOff(List.of(
                Component.translatable("gui.extendedae_plus.terminal.freeze_updates"),
                Component.translatable("gui.extendedae_plus.terminal.freeze_updates.tooltip")));
    }

    @ModifyArg(
            method = "updateBeforeRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/client/gui/me/common/Repo;setPaused(Z)V",
                    remap = false),
            index = 0,
            remap = false)
    private boolean eap$includeFreezeInPauseState(boolean paused) {
        return paused || ((IRepoUpdateFreezeAccess) this.repo).eap$isUpdatesFrozen();
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void eap$syncFreezeUpdatesButton(CallbackInfo ci) {
        if (this.eap$freezeUpdatesButton != null) {
            this.eap$freezeUpdatesButton.setState(
                    ((IRepoUpdateFreezeAccess) this.repo).eap$isUpdatesFrozen());
        }
    }
}
