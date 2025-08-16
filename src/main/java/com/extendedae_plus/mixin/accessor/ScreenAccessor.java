package com.extendedae_plus.mixin.accessor;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("renderables")
    List<Renderable> extendedae_plus$getRenderables();

    @Accessor("children")
    List<GuiEventListener> extendedae_plus$getChildren();
}
