package com.extendedae_plus.mixin;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.VerticalButtonBar;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.NewIcon;
import com.extendedae_plus.util.PatternProviderUIHelper;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.extendedae_plus.network.UpdatePagePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(GuiExPatternProvider.class)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> {

    @Unique
    ScreenStyle screenStyle;

    @Unique
    private VerticalButtonBar rightToolbar;

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Unique
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        int maxSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
        // 只有当槽位数超过每页显示数量时才显示翻页信息
        if (maxSlots > SLOTS_PER_PAGE) {
            Font fontRenderer = Minecraft.getInstance().font;

            // 获取当前页码
            int currentPage = getCurrentPage();
            int maxPage = getMaxPage();

                               // 获取ae通用界面样式
                   int color = screenStyle.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
                   // 调整页码显示位置：在"样板"文字的右边
                   guiGraphics.drawString(font, Component.literal("第 " + (currentPage + 1) + "/" + maxPage + " 页"),
                           leftPos + 8 + 50, topPos + 30, color, false);
        }
    }

    @Unique
    public void updateBeforeRender() {
        super.updateBeforeRender();
        try {
            ContainerExPatternProvider menu1 = this.getMenu();

            // 调用showPage方法
            java.lang.reflect.Method showPageMethod = menu1.getClass().getMethod("showPage");
            showPageMethod.invoke(menu1);

            // 获取当前页码和最大页码
            Field fieldPage = menu1.getClass().getDeclaredField("page");
            fieldPage.setAccessible(true);
            Integer page = (Integer) fieldPage.get(menu1);

            Field fieldMaxPage = menu1.getClass().getDeclaredField("maxPage");
            fieldMaxPage.setAccessible(true);
            Integer maxPage = (Integer) fieldMaxPage.get(menu1);

                               // 更新按钮可见性 - 始终显示，支持循环翻页
                   if (nextPage != null && prevPage != null) {
                       this.nextPage.setVisibility(true);
                       this.prevPage.setVisibility(true);
                   }
                   if (x2Button != null) {
                       this.x2Button.setVisibility(true);
                   }
                   if (divideBy2Button != null) {
                       this.divideBy2Button.setVisibility(true);
                   }
            
            // 调整槽位位置
            this.adjustSlotPositions(page);
        } catch (Exception e) {
            // 忽略反射错误
        }
    }
    
    @Unique
    private void adjustSlotPositions(int currentPage) {
        try {
            List<Slot> slots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN);
            int totalSlots = slots.size();
            
            if (totalSlots <= SLOTS_PER_PAGE) {
                return; // 不需要翻页
            }
            
            int slot_id = 0;
            for (Slot s : slots) {
                int page_id = slot_id / SLOTS_PER_PAGE;
                
                if (page_id == currentPage) {
                    // 当前页的槽位需要调整位置
                    int slotInPage = slot_id % SLOTS_PER_PAGE;
                    int row = slotInPage / 9;  // 0-3
                    int col = slotInPage % 9;  // 0-8
                    
                    // 计算目标位置（始终在前4行）
                    int x = 8 + col * 18;
                    int y = 42 + row * 18;
                    
                    // 使用反射设置槽位位置，支持混淆环境
                    Field xField = null;
                    Field yField = null;
                    
                    // 尝试不同的字段名（开发环境和生产环境可能不同）
                    String[] xFieldNames = {"x", "field_75262_c"};
                    String[] yFieldNames = {"y", "field_75263_d"};
                    
                    for (String fieldName : xFieldNames) {
                        try {
                            xField = Slot.class.getDeclaredField(fieldName);
                            xField.setAccessible(true);
                            break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                    
                    for (String fieldName : yFieldNames) {
                        try {
                            yField = Slot.class.getDeclaredField(fieldName);
                            yField.setAccessible(true);
                            break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                    
                    if (xField != null && yField != null) {
                        xField.set(s, x);
                        yField.set(s, y);
                    }
                }
                ++slot_id;
            }
        } catch (Exception e) {
            // 忽略反射错误
        }
    }

    @Unique
    private int getCurrentPage() {
        try {
            ContainerExPatternProvider menu1 = this.getMenu();
            Field fieldPage = menu1.getClass().getDeclaredField("page");
            fieldPage.setAccessible(true);
            return (Integer) fieldPage.get(menu1);
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private int getMaxPage() {
        try {
            ContainerExPatternProvider menu1 = this.getMenu();
            Field fieldMaxPage = menu1.getClass().getDeclaredField("maxPage");
            fieldMaxPage.setAccessible(true);
            return (Integer) fieldMaxPage.get(menu1);
        } catch (Exception e) {
            return 1;
        }
    }

    public ActionEPPButton nextPage;
    public ActionEPPButton prevPage;
    public ActionEPPButton x2Button;
    public ActionEPPButton divideBy2Button;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void injectInit(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.screenStyle = style;
        this.rightToolbar = new VerticalButtonBar();

        // 只有当槽位数超过每页显示数量时才添加翻页按钮
        int maxSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
        if (maxSlots > SLOTS_PER_PAGE) {
            // 前进后退按钮
                               this.prevPage = new ActionEPPButton((b) -> {
                       int currentPage = getCurrentPage();
                       int maxPage = getMaxPage();
                       // 循环翻页：第一页向前翻到最后一页
                       int newPage = (currentPage - 1 + maxPage) % maxPage;
                       try {
                           ContainerExPatternProvider menu1 = this.getMenu();
                           java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                           setPageMethod.invoke(menu1, newPage);
                       } catch (Exception e) {
                           // 忽略反射错误
                       }
                   }, Icon.ARROW_LEFT);

                               this.nextPage = new ActionEPPButton((b) -> {
                        int currentPage = getCurrentPage();
                        int maxPage = getMaxPage();
                        // 循环翻页：最后一页向后翻到第一页
                        int newPage = (currentPage + 1) % maxPage;
                        try {
                            ContainerExPatternProvider menu1 = this.getMenu();
                            java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                            setPageMethod.invoke(menu1, newPage);
                        } catch (Exception e) {
                            // 忽略反射错误
                        }
                    }, Icon.ARROW_RIGHT);

            this.addToLeftToolbar(this.nextPage);
            this.addToLeftToolbar(this.prevPage);
        }
        
        // x2 按钮 - 单机模式直接调用服务器端逻辑
        this.x2Button = new ActionEPPButton((b) -> {
            try {
                // 单机模式：直接在逻辑服务器端执行样板倍增
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (minecraft.level != null && minecraft.player != null) {
                    // 获取逻辑服务器端的玩家实例
                    net.minecraft.server.level.ServerPlayer serverPlayer = minecraft.getSingleplayerServer()
                        .getPlayerList().getPlayer(minecraft.player.getUUID());
                    
                    if (serverPlayer != null) {
                        // 在服务器端执行样板倍增逻辑
                        executePatternScalingOnServer(serverPlayer, "MULTIPLY", 2.0);
                    } else {
                        System.out.println("ExtendedAE Plus: 无法获取服务器端玩家实例");
                    }
                } else {
                    System.out.println("ExtendedAE Plus: 单机服务器未启动或玩家为null");
                }
                
            } catch (Exception e) {
                System.out.println("ExtendedAE Plus: 执行样板倍增时发生错误：" + e.getMessage());
                e.printStackTrace();
            }
        }, NewIcon.MULTIPLY2);
        
        // /2 按钮 - 单机模式直接调用服务器端逻辑
        this.divideBy2Button = new ActionEPPButton((b) -> {
            try {
                // 单机模式：直接在逻辑服务器端执行样板除法
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (minecraft.level != null && minecraft.player != null) {
                    // 获取逻辑服务器端的玩家实例
                    net.minecraft.server.level.ServerPlayer serverPlayer = minecraft.getSingleplayerServer()
                        .getPlayerList().getPlayer(minecraft.player.getUUID());
                    
                    if (serverPlayer != null) {
                        // 在服务器端执行样板除法逻辑
                        executePatternScalingOnServer(serverPlayer, "DIVIDE", 2.0);
                    } else {
                        System.out.println("ExtendedAE Plus: 无法获取服务器端玩家实例");
                    }
                } else {
                    System.out.println("ExtendedAE Plus: 单机服务器未启动或玩家为null");
                }
                
            } catch (Exception e) {
                System.out.println("ExtendedAE Plus: 执行样板除法时发生错误：" + e.getMessage());
                e.printStackTrace();
            }
        }, NewIcon.DIVIDE2);
        
        this.addToLeftToolbar(this.x2Button);
        this.addToLeftToolbar(this.divideBy2Button);
    }
    
    /**
     * 在服务器端执行样板缩放操作（单机模式）
     */
    @Unique
    private void executePatternScalingOnServer(net.minecraft.server.level.ServerPlayer serverPlayer, String scalingType, double scaleFactor) {
        try {
            // 获取服务器端的样板供应器逻辑
            appeng.helpers.patternprovider.PatternProviderLogic patternProvider = getServerSidePatternProvider(serverPlayer);
            if (patternProvider == null) {
                System.out.println("ExtendedAE Plus: 无法获取服务器端样板供应器");
                return;
            }
            
            // 在服务器端执行样板缩放
            com.extendedae_plus.util.PatternProviderDataUtil.PatternScalingResult result;
            if ("MULTIPLY".equals(scalingType)) {
                result = com.extendedae_plus.util.PatternProviderDataUtil.duplicatePatternAmountsExtendedAEStyle(patternProvider, scaleFactor);
            } else {
                result = com.extendedae_plus.util.PatternProviderDataUtil.dividePatternAmounts(patternProvider, scaleFactor);
            }
            
            // 在客户端显示结果（单机模式下可以直接访问客户端）
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null && result != null) {
                String message;
                if (result.isSuccessful()) {
                    if (result.getFailedPatterns() > 0) {
                        message = String.format("✅ ExtendedAE Plus: 样板%s成功！处理了 %d 个样板，跳过 %d 个样板", 
                                              "MULTIPLY".equals(scalingType) ? "倍增" : "除法",
                                              result.getScaledPatterns(), result.getFailedPatterns());
                    } else {
                        message = String.format("✅ ExtendedAE Plus: 样板%s成功！处理了 %d 个样板", 
                                              "MULTIPLY".equals(scalingType) ? "倍增" : "除法",
                                              result.getScaledPatterns());
                    }
                } else {
                    message = String.format("❌ ExtendedAE Plus: 样板%s失败！错误: %s", 
                                          "MULTIPLY".equals(scalingType) ? "倍增" : "除法",
                                          String.join("; ", result.getErrors()));
                }
                
                // 显示消息到动作栏
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
                System.out.println("ExtendedAE Plus: " + message);
            }
            
        } catch (Exception e) {
            System.out.println("ExtendedAE Plus: 服务器端执行样板缩放时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取服务器端的样板供应器逻辑
     */
    @Unique
    private appeng.helpers.patternprovider.PatternProviderLogic getServerSidePatternProvider(net.minecraft.server.level.ServerPlayer serverPlayer) {
        try {
            if (serverPlayer.containerMenu != null) {
                // 检查是否是样板供应器菜单
                String menuClassName = serverPlayer.containerMenu.getClass().getSimpleName();
                System.out.println("ExtendedAE Plus: 服务器端菜单类名: " + menuClassName);
                
                if (menuClassName.contains("PatternProvider")) {
                    // 尝试多种可能的字段名称
                    String[] possibleFieldNames = {"logic", "patternProvider", "host", "provider"};
                    
                    for (String fieldName : possibleFieldNames) {
                        try {
                            var field = findFieldInHierarchy(serverPlayer.containerMenu.getClass(), fieldName);
                            if (field != null) {
                                field.setAccessible(true);
                                Object fieldValue = field.get(serverPlayer.containerMenu);
                                
                                if (fieldValue instanceof appeng.helpers.patternprovider.PatternProviderLogic) {
                                    System.out.println("ExtendedAE Plus: 通过字段 '" + fieldName + "' 找到PatternProviderLogic");
                                    return (appeng.helpers.patternprovider.PatternProviderLogic) fieldValue;
                                }
                                
                                // 如果字段值是host，尝试从host获取logic
                                if (fieldValue != null && fieldName.equals("host")) {
                                    try {
                                        var logicMethod = fieldValue.getClass().getMethod("getLogic");
                                        Object logic = logicMethod.invoke(fieldValue);
                                        if (logic instanceof appeng.helpers.patternprovider.PatternProviderLogic) {
                                            System.out.println("ExtendedAE Plus: 通过host.getLogic()找到PatternProviderLogic");
                                            return (appeng.helpers.patternprovider.PatternProviderLogic) logic;
                                        }
                                    } catch (Exception hostException) {
                                        // 忽略host方法调用失败
                                    }
                                }
                            }
                        } catch (Exception fieldException) {
                            // 继续尝试下一个字段名
                        }
                    }
                    
                    // 如果直接字段访问失败，尝试通过方法获取
                    try {
                        var getLogicMethod = serverPlayer.containerMenu.getClass().getMethod("getLogic");
                        Object logic = getLogicMethod.invoke(serverPlayer.containerMenu);
                        if (logic instanceof appeng.helpers.patternprovider.PatternProviderLogic) {
                            System.out.println("ExtendedAE Plus: 通过getLogic()方法找到PatternProviderLogic");
                            return (appeng.helpers.patternprovider.PatternProviderLogic) logic;
                        }
                    } catch (Exception methodException) {
                        // 方法调用失败，继续其他尝试
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("ExtendedAE Plus: 获取服务器端样板供应器逻辑时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * 在类继承层次中查找字段
     */
    @Unique
    private java.lang.reflect.Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }
}