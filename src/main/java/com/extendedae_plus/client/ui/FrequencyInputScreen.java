package com.extendedae_plus.client.ui;

import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.SetWirelessFrequencyC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 频率输入界面
 * 用于通过输入框设置无线收发器的频率
 */
public class FrequencyInputScreen extends Screen {
    private final BlockPos pos;
    private final long currentFrequency;
    private EditBox frequencyInput;

    public FrequencyInputScreen(BlockPos pos, long currentFrequency) {
        super(Component.translatable("extendedae_plus.screen.frequency_input.title"));
        this.pos = pos;
        this.currentFrequency = currentFrequency;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // 频率输入框
        this.frequencyInput = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, 
                Component.translatable("extendedae_plus.screen.frequency_input.field"));
        this.frequencyInput.setValue(String.valueOf(currentFrequency));
        this.frequencyInput.setMaxLength(19); // long最大值是19位
        this.frequencyInput.setFilter(text -> {
            // 只允许输入数字
            if (text.isEmpty()) return true;
            try {
                Long.parseLong(text);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        this.addRenderableWidget(this.frequencyInput);
        this.setInitialFocus(this.frequencyInput);

        // 确认按钮
        Button confirmButton = Button.builder(Component.translatable("extendedae_plus.screen.frequency_input.confirm"), 
                button -> onConfirm())
                .bounds(centerX - 105, centerY + 20, 100, 20)
                .build();
        this.addRenderableWidget(confirmButton);

        // 取消按钮
        Button cancelButton = Button.builder(Component.translatable("gui.cancel"), 
                button -> onClose())
                .bounds(centerX + 5, centerY + 20, 100, 20)
                .build();
        this.addRenderableWidget(cancelButton);
    }

    private void onConfirm() {
        try {
            String text = this.frequencyInput.getValue();
            if (text.isEmpty()) {
                // 空值视为0
                text = "0";
            }
            long frequency = Long.parseLong(text);
            if (frequency < 0) {
                frequency = 0;
            }
            
            // 发送数据包到服务端
            ModNetwork.CHANNEL.sendToServer(new SetWirelessFrequencyC2SPacket(pos, frequency));
            this.onClose();
        } catch (NumberFormatException e) {
            // 输入无效，不做处理
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        // 绘制标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 按回车键确认
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
            onConfirm();
            return true;
        }
        // 按ESC键取消
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.frequencyInput != null) {
            this.frequencyInput.tick();
        }
    }
}

