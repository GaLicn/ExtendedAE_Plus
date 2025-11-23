package com.extendedae_plus.client.gui.widgets;

import appeng.api.config.Setting;
import appeng.core.network.ServerboundPacket;
import com.extendedae_plus.network.packet.EAPConfigButtonPacket;
import net.neoforged.neoforge.network.PacketDistributor;

public class EAPServerSettingToggleButton<T extends Enum<T>> extends EAPSettingToggleButton<T> {

    public EAPServerSettingToggleButton(Setting<T> setting, T val) {
        super(setting, val, EAPServerSettingToggleButton::sendToServer);
    }

    private static <T extends Enum<T>> void sendToServer(EAPSettingToggleButton<T> button, boolean backwards) {
        ServerboundPacket message = new EAPConfigButtonPacket(button.getSetting(), backwards);
        PacketDistributor.sendToServer(message);
    }
}