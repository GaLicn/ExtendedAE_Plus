package com.extendedae_plus.client.screen;

import com.extendedae_plus.network.SetWirelessFrequencyC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 频率输入GUI界面
 * 用于设置无线收发器的频率
 * 
 * API变化说明：
 * 1. @OnlyIn(Dist.CLIENT)在1.21.1中已移除，不再需要
 * 2. GuiGraphics替代了PoseStack作为渲染参数（1.20+的变化）
 * 3. 网络数据包通过PacketDistributor发送
 * 4. EditBox的API在1.21.1中保持稳定
 */
public class FrequencyInputScreen extends Screen {
    
    private static final int WINDOW_WIDTH = 200;
    private static final int WINDOW_HEIGHT = 80;
    
    private final BlockPos pos;
    private final long currentFrequency;
    private EditBox frequencyInput;
    private Button confirmButton;
    
    /**
     * 构造函数
     * @param pos 无线收发器的位置
     * @param currentFrequency 当前频率
     */
    private FrequencyInputScreen(BlockPos pos, long currentFrequency) {
        super(Component.translatable("gui.extendedae_plus.frequency_input.title"));
        this.pos = pos;
        this.currentFrequency = currentFrequency;
    }
    
    /**
     * 静态工厂方法：打开频率输入界面
     */
    public static void open(BlockPos pos, long currentFrequency) {
        Minecraft.getInstance().setScreen(new FrequencyInputScreen(pos, currentFrequency));
    }
    
    /**
     * 输入验证：只允许数字和负号
     */
    private boolean isValidInput(String input) {
        if (input.isEmpty()) {
            return true;
        }
        // 允许负号在开头
        if (input.equals("-")) {
            return true;
        }
        try {
            Long.parseLong(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 渲染背景
     *
     * API变化说明：
     * - GuiGraphics替代了旧的PoseStack + BufferSource组合
     * - renderBackground方法签名在1.21.1中简化
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染暗色背景
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 计算窗口位置
        int x = (this.width - WINDOW_WIDTH) / 2;
        int y = (this.height - WINDOW_HEIGHT) / 2;

        // 绘制窗口背景
        guiGraphics.fill(x, y, x + WINDOW_WIDTH, y + WINDOW_HEIGHT, 0xC0000000);

        // 绘制窗口边框
        guiGraphics.fill(x, y, x + WINDOW_WIDTH, y + 1, 0xFFFFFFFF); // 顶部
        guiGraphics.fill(x, y + WINDOW_HEIGHT - 1, x + WINDOW_WIDTH, y + WINDOW_HEIGHT, 0xFFFFFFFF); // 底部
        guiGraphics.fill(x, y, x + 1, y + WINDOW_HEIGHT, 0xFFFFFFFF); // 左侧
        guiGraphics.fill(x + WINDOW_WIDTH - 1, y, x + WINDOW_WIDTH, y + WINDOW_HEIGHT, 0xFFFFFFFF); // 右侧

        // 绘制标题
        Component title = Component.translatable("gui.extendedae_plus.frequency_input.title");
        guiGraphics.drawString(
                this.font,
                title,
                x + (WINDOW_WIDTH - this.font.width(title)) / 2,
                y + 10,
                0xFFFFFFFF,
                false
        );
    }
    
    /**
     * 按键处理：回车键确认，ESC键取消
     *
     * API说明：keyPressed方法在1.21.1中保持一致
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 回车键确认
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
            this.onConfirm();
            return true;
        }
        // ESC键取消
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    protected void init() {
        super.init();

        // 计算居中位置
        int x = (this.width - WINDOW_WIDTH) / 2;
        int y = (this.height - WINDOW_HEIGHT) / 2;

        // 创建输入框
        // API说明：EditBox构造函数参数：font, x, y, width, height, component
        this.frequencyInput = new EditBox(
                this.font,
                x + 10,
                y + 30,
                WINDOW_WIDTH - 20,
                20,
                Component.translatable("gui.extendedae_plus.frequency_input.field")
        );

        // 设置输入框属性
        this.frequencyInput.setMaxLength(19); // long类型最大19位数字
        this.frequencyInput.setValue(String.valueOf(this.currentFrequency));
        this.frequencyInput.setFilter(this::isValidInput); // 只允许数字和负号
        this.frequencyInput.setFocused(true);

        // 添加输入框到组件列表
        this.addRenderableWidget(this.frequencyInput);

        // 创建确认按钮
        // API说明：Button.builder方法在1.21.1中使用
        this.confirmButton = Button.builder(
                Component.translatable("gui.extendedae_plus.frequency_input.confirm"),
                button -> this.onConfirm()
        )
        .bounds(x + 10, y + 55, 80, 20)
        .build();

        this.addRenderableWidget(this.confirmButton);

        // 创建取消按钮
        Button cancelButton = Button.builder(
                Component.translatable("gui.extendedae_plus.frequency_input.cancel"),
                button -> this.onClose()
        )
        .bounds(x + 110, y + 55, 80, 20)
        .build();

        this.addRenderableWidget(cancelButton);
    }
    
    /**
     * 暂停游戏状态
     * 返回false表示不暂停游戏（允许多人游戏中正常使用）
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    /**
     * 确认按钮处理
     */
    private void onConfirm() {
        String input = this.frequencyInput.getValue();
        if (input.isEmpty()) {
            this.onClose();
            return;
        }

        try {
            long frequency = Long.parseLong(input);

            // 发送数据包到服务端
            // API说明：NeoForge使用PacketDistributor.sendToServer
            PacketDistributor.sendToServer(new SetWirelessFrequencyC2SPacket(this.pos, frequency));

            this.onClose();
        } catch (NumberFormatException e) {
            // 输入无效，不做处理
        }
    }
}

