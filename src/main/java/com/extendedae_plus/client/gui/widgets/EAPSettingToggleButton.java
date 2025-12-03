package com.extendedae_plus.client.gui.widgets;

import appeng.api.config.Setting;
import appeng.api.config.YesNo;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.LocalizationEnum;
import appeng.util.EnumCycler;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.common.definitions.EAPText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class EAPSettingToggleButton<T extends Enum<T>> extends IconButton {
    private static Map<EnumPair<?>, ButtonAppearance> appearances;
    private final Setting<T> buttonSetting;
    private final IHandler<EAPSettingToggleButton<T>> onPress;
    private final EnumSet<T> validValues;
    private T currentValue;

    EAPSettingToggleButton(Setting<T> setting, T val,
                           IHandler<EAPSettingToggleButton<T>> onPress) {
        this(setting, val, t -> true, onPress);
    }

    private EAPSettingToggleButton(Setting<T> setting, T val, Predicate<T> isValidValue,
                                   IHandler<EAPSettingToggleButton<T>> onPress) {
        super(EAPSettingToggleButton::onPress);
        this.onPress = onPress;

        EnumSet<T> validValues = EnumSet.allOf(val.getDeclaringClass());
        validValues.removeIf(isValidValue.negate());
        validValues.removeIf(s -> !setting.getValues().contains(s));
        this.validValues = validValues;

        this.buttonSetting = setting;
        this.currentValue = val;

        if (appearances == null) {
            appearances = new HashMap<>();
            registerApp(Icon.VALID, EAPSettings.ACCELERATE, YesNo.YES,
                    EAPText.Accelerate,
                    EAPText.AccelerateEnabled);
            registerApp(Icon.INVALID, EAPSettings.ACCELERATE, YesNo.NO,
                    EAPText.Accelerate,
                    EAPText.AccelerateDisabled);
            registerApp(Icon.INVALID, EAPSettings.ACCELERATE, YesNo.UNDECIDED,
                    EAPText.Accelerate,
                    EAPText.AccelerateBlacklisted);

            registerApp(Icon.REDSTONE_LOW, EAPSettings.REDSTONE_CONTROL, YesNo.YES,
                    EAPText.RedstoneControl,
                    EAPText.RedstoneControlEnabled);
            registerApp(Icon.REDSTONE_IGNORE, EAPSettings.REDSTONE_CONTROL, YesNo.NO,
                    EAPText.RedstoneControl,
                    EAPText.RedstoneControlDisabled);

            registerApp(Icon.BLOCKING_MODE_YES, EAPSettings.SMART_DOUBLING, YesNo.YES,
                    EAPText.SmartDoubling,
                    EAPText.SmartDoublingEnabled);
            registerApp(Icon.BLOCKING_MODE_NO, EAPSettings.SMART_DOUBLING, YesNo.NO,
                    EAPText.SmartDoubling,
                    EAPText.SmartDoublingDisabled);

            registerApp(Icon.BLOCKING_MODE_YES, EAPSettings.ADVANCED_BLOCKING, YesNo.YES,
                    EAPText.AdvancedBlocking,
                    EAPText.AdvancedBlockingEnabled);
            registerApp(Icon.BLOCKING_MODE_NO, EAPSettings.ADVANCED_BLOCKING, YesNo.NO,
                    EAPText.AdvancedBlocking,
                    EAPText.AdvancedBlockingDisabled);

        }
    }

    private static void onPress(Button btn) {
        if (btn instanceof EAPSettingToggleButton) {
            ((EAPSettingToggleButton<?>) btn).triggerPress();
        }
    }

    private static <T extends Enum<T>> void registerApp(Icon icon, Setting<T> setting, T val,
                                                        LocalizationEnum title, Component... tooltipLines) {
        var lines = new ArrayList<Component>();
        lines.add(title.text());
        Collections.addAll(lines, tooltipLines);

        appearances.put(
                new EnumPair<>(setting, val),
                new ButtonAppearance(icon, null, lines));
    }

    private static <T extends Enum<T>> void registerApp(ItemLike item, Setting<T> setting, T val,
                                                        LocalizationEnum title, Component... tooltipLines) {
        var lines = new ArrayList<Component>();
        lines.add(title.text());
        Collections.addAll(lines, tooltipLines);

        appearances.put(
                new EnumPair<>(setting, val),
                new ButtonAppearance(null, item.asItem(), lines));
    }

    private static <T extends Enum<T>> void registerApp(Icon icon, Setting<T> setting, T val,
                                                        LocalizationEnum title, LocalizationEnum hint) {
        registerApp(icon, setting, val, title, hint.text());
    }

    private void triggerPress() {
        boolean backwards = false;
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof AEBaseScreen) {
            backwards = ((AEBaseScreen<?>) currentScreen).isHandlingRightClick();
        }
        this.onPress.handle(this, backwards);
    }

    @Nullable
    private ButtonAppearance getApperance() {
        if (this.buttonSetting != null && this.currentValue != null) {
            return appearances.get(new EnumPair<>(this.buttonSetting, this.currentValue));
        }
        return null;
    }

    @Override
    protected Icon getIcon() {
        var app = this.getApperance();
        if (app != null && app.icon != null) {
            return app.icon;
        }
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    protected Item getItemOverlay() {
        var app = this.getApperance();
        if (app != null && app.item != null) {
            return app.item;
        }
        return null;
    }

    @Override
    public List<Component> getTooltipMessage() {

        if (this.buttonSetting == null || this.currentValue == null) {
            return Collections.emptyList();
        }

        var buttonAppearance = appearances.get(new EnumPair<>(this.buttonSetting, this.currentValue));
        if (buttonAppearance == null) {
            return Collections.singletonList(ButtonToolTips.NoSuchMessage.text());
        }

        return buttonAppearance.tooltipLines;
    }

    Setting<T> getSetting() {
        return this.buttonSetting;
    }

    public T getCurrentValue() {
        return this.currentValue;
    }

    public void set(T e) {
        if (this.currentValue != e) {
            this.currentValue = e;
        }
    }

    public T getNextValue(boolean backwards) {
        return EnumCycler.rotateEnum(this.currentValue, backwards, this.validValues);
    }

    @FunctionalInterface
    public interface IHandler<T extends EAPSettingToggleButton<?>> {
        void handle(T button, boolean backwards);
    }

    private record EnumPair<T extends Enum<T>>(Setting<T> setting, T value) {
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            final EnumPair<?> other = (EnumPair<?>) obj;
            return other.setting == this.setting && other.value == this.value;
        }

        @Override
        public int hashCode() {
            return this.setting.hashCode() ^ this.value.hashCode();
        }
    }

    private record ButtonAppearance(@Nullable Icon icon, @Nullable Item item, List<Component> tooltipLines) {
    }
}
