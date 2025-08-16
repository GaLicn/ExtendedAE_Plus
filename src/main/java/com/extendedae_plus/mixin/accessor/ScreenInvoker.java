package com.extendedae_plus.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("addRenderableWidget")
    <W extends GuiEventListener & Renderable> W extendedae_plus$invokeAddRenderableWidget(W widget);
}
