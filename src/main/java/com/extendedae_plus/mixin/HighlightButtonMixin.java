package com.extendedae_plus.mixin;

import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HighlightButton.class, priority = 1000)
public abstract class HighlightButtonMixin {

    @Shadow(remap = false)
    private static void highlight(Button btn) {}

    @Inject(method = "highlight", at = @At("TAIL"), remap = false)
    private static void onHighlight(Button btn, CallbackInfo ci) {
        if (btn instanceof HighlightButton hb) {
            // 获取当前打开的GUI屏幕
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.screen instanceof GuiExPatternTerminal<?> terminal) {
                // 通过反射获取HighlightButton的serverId信息
                try {
                    // 获取HighlightButton的pos字段，用于标识对应的样板供应器
                    var posField = HighlightButton.class.getDeclaredField("pos");
                    posField.setAccessible(true);
                    var pos = posField.get(hb);
                    
                    if (pos != null) {
                        // 通过反射访问infoMap字段
                        var infoMapField = GuiExPatternTerminal.class.getDeclaredField("infoMap");
                        infoMapField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        var infoMap = (java.util.Map<Long, Object>) infoMapField.get(terminal);
                        
                        // 查找对应的样板供应器ID
                        for (var entry : infoMap.entrySet()) {
                            var info = entry.getValue();
                            // 通过反射调用pos()方法
                            var posMethod = info.getClass().getMethod("pos");
                            var infoPos = posMethod.invoke(info);
                            
                            if (pos.equals(infoPos)) {
                                long serverId = entry.getKey();
                                
                                // 通过反射调用setter方法
                                try {
                                    var setMethod = terminal.getClass().getMethod("setCurrentlyChoicePatternProvider", long.class);
                                    setMethod.invoke(terminal, serverId);
                                    System.out.println("ExtendedAE Plus: 通过Mixin设置了当前选择的样板供应器ID: " + serverId);
                                } catch (Exception e) {
                                    System.out.println("ExtendedAE Plus: 无法设置样板供应器ID，错误: " + e.getMessage());
                                    e.printStackTrace();
                                }
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("ExtendedAE Plus: 通过Mixin设置样板供应器ID时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
} 