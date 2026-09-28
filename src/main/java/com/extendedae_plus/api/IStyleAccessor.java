package com.extendedae_plus.api;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.WidgetStyle;

import java.util.Map;

public interface IStyleAccessor {

    Map<String, Blitter> getImages();

    Map<String, WidgetStyle> getWidgets();

}
