package com.extendedae_plus.util;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 与反射 / 菜单（PatternAccessTermMenu）相关的工具方法。
 * 包含：获取 PatternAccessTermMenu、通过反射获取 byId/container、列出 provider id、供应器显示名等。
 */
public final class PatternTerminalUtil {
    private PatternTerminalUtil() {}

    /**
     * 检查当前菜单是否为ExtendedAE的扩展样板管理终端
     *
     * @param player 玩家
     * @return 是否为ExtendedAE扩展终端
     */
    public static boolean isExtendedAETerminal(ServerPlayer player) {
        if (player == null || player.containerMenu == null) {
            return false;
        }

        String containerClassName = player.containerMenu.getClass().getName();
        return containerClassName.equals("com.glodblock.github.extendedae.container.ContainerExPatternTerminal");
    }


    /**
     * 通过服务器ID获取PatternContainer
     * 兼容ExtendedAE的ContainerExPatternTerminal和原版PatternAccessTermMenu
     *
     * @param menu 样板访问终端菜单
     * @param providerId 供应器服务器ID
     * @return PatternContainer实例，如果不存在则返回null
     */
    public static PatternContainer getPatternContainerById(PatternAccessTermMenu menu, long providerId) {
        try {
            // 通过反射访问byId字段（ExtendedAE继承了这个字段）
            Field byIdField = findByIdField(menu.getClass());
            if (byIdField == null) {
                System.err.println("ExtendedAE Plus: 无法找到byId字段");
                return null;
            }

            byIdField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<Long, Object> byId = (Map<Long, Object>) byIdField.get(menu);

            Object containerTracker = byId.get(providerId);
            if (containerTracker == null) {
                return null;
            }

            // 从ContainerTracker中获取PatternContainer
            Field containerField = findContainerField(containerTracker.getClass());
            if (containerField == null) {
                System.err.println("ExtendedAE Plus: 无法找到container字段");
                return null;
            }

            containerField.setAccessible(true);
            return (PatternContainer) containerField.get(containerTracker);

        } catch (Exception e) {
            System.err.println("ExtendedAE Plus: 无法获取PatternContainer，错误: " + e.getMessage());
            return null;
        }
    }


    /**
     * 在类层次结构中查找byId字段
     */
    public static Field findByIdField(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("byId");
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 在类层次结构中查找container字段
     */
    private static Field findContainerField(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("container");
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取玩家当前的样板访问终端菜单（支持ExtendedAE和原版AE2）
     *
     * @param player 玩家
     * @return PatternAccessTermMenu实例，如果玩家没有打开则返回null
     */
    public static PatternAccessTermMenu getPatternAccessMenu(ServerPlayer player) {
        if (player == null || player.containerMenu == null) {
            return null;
        }
        // 优先检查ExtendedAE的扩展样板管理终端（使用类名检查避免直接导入）
        String containerClassName = player.containerMenu.getClass().getName();
        if (containerClassName.equals("com.glodblock.github.extendedae.container.ContainerExPatternTerminal")) {
            // ExtendedAE的容器继承自PatternAccessTermMenu，可以安全转换
            return (PatternAccessTermMenu) player.containerMenu;
        }
        // 兼容原版AE2的样板访问终端
        if (player.containerMenu instanceof PatternAccessTermMenu) {
            return (PatternAccessTermMenu) player.containerMenu;
        }
        return null;
    }

    /**
     * 列出当前菜单中所有供应器的服务器ID（原样返回 byId 的 key 集合）。
     */
    public static List<Long> getAllProviderIds(PatternAccessTermMenu menu) {
        List<Long> result = new ArrayList<>();
        if (menu == null) return result;
        try {
            Field byIdField = PatternTerminalUtil.findByIdField(menu.getClass());
            if (byIdField == null) return result;
            byIdField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, Object> byId = (Map<Long, Object>) byIdField.get(menu);
            if (byId != null) {
                result.addAll(byId.keySet());
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 基于编码终端菜单的 AE Grid 遍历，列出“可在终端中可见且有空位”的供应器容器。
     * 返回顺序稳定：按 grid 的 machineClasses 顺序，再按 activeMachines 迭代顺序。
     */
    public static List<PatternContainer> listAvailableProvidersFromGrid(PatternEncodingTermMenu menu) {
        List<PatternContainer> list = new ArrayList<>();
        if (menu == null) return list;
        try {
            IGridNode node = menu.getNetworkNode();
            if (node == null) return list;
            IGrid grid = node.getGrid();
            if (grid == null) return list;
            for (var machineClass : grid.getMachineClasses()) {
                if (PatternContainer.class.isAssignableFrom(machineClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
                    for (var container : grid.getActiveMachines(containerClass)) {
                        if (container == null || !container.isVisibleInTerminal()) continue;
                        InternalInventory inv = container.getTerminalPatternInventory();
                        if (inv == null || inv.size() <= 0) continue;
                        boolean hasEmpty = false;
                        for (int i = 0; i < inv.size(); i++) {
                            if (inv.getStackInSlot(i).isEmpty()) { hasEmpty = true; break; }
                        }
                        if (hasEmpty) list.add(container);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

}
