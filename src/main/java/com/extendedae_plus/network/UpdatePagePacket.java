package com.extendedae_plus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdatePagePacket {
    private final int newPage;

    public UpdatePagePacket(int newPage) {
        this.newPage = newPage;
    }

    public UpdatePagePacket(FriendlyByteBuf buf) {
        this.newPage = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(newPage);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // 这里处理页码更新逻辑
            // 由于我们使用@GuiSync，实际上不需要额外的网络处理
        });
        return true;
    }
} 