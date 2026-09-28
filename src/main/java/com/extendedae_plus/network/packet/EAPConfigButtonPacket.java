package com.extendedae_plus.network.packet;

import appeng.api.config.Setting;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.core.network.CustomAppEngPayload;
import appeng.core.network.ServerboundPacket;
import appeng.menu.AEBaseMenu;
import appeng.util.EnumCycler;
import com.extendedae_plus.api.config.EAPSettings;
import com.extendedae_plus.util.ExtendedAELogger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public record EAPConfigButtonPacket(Setting<?> option, boolean rotationDirection) implements ServerboundPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, EAPConfigButtonPacket> STREAM_CODEC = StreamCodec.ofMember(
            EAPConfigButtonPacket::write,
            EAPConfigButtonPacket::decode);

    public static final Type<EAPConfigButtonPacket> TYPE = CustomAppEngPayload.createType("eap_config_button");

    @Override
    public @NotNull Type<EAPConfigButtonPacket> type() {
        return TYPE;
    }

    public static EAPConfigButtonPacket decode(RegistryFriendlyByteBuf stream) {
        var option = EAPSettings.getOrThrow(stream.readUtf());
        var rotationDirection = stream.readBoolean();
        return new EAPConfigButtonPacket(option, rotationDirection);
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeUtf(option.getName());
        data.writeBoolean(rotationDirection);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        if (player.containerMenu instanceof AEBaseMenu baseMenu) {
            if (baseMenu.getTarget() instanceof IConfigurableObject configurableObject) {
                var cm = configurableObject.getConfigManager();
                if (cm.hasSetting(option)) {
                    cycleSetting(cm, option);
                } else {
                    ExtendedAELogger.LOGGER.info("Ignoring unsupported setting {} sent by client on {}", option, baseMenu.getTarget());
                }
            }
        }
    }

    private <T extends Enum<T>> void cycleSetting(IConfigManager cm, Setting<T> setting) {
        var currentValue = cm.getSetting(setting);
        var nextValue = EnumCycler.rotateEnum(currentValue, rotationDirection, setting.getValues());
        cm.putSetting(setting, nextValue);
    }
}
