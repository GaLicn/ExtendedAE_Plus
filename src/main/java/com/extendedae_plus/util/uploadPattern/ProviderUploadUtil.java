package com.extendedae_plus.util.uploadPattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.extendedae_plus.mixin.ae2.accessor.PatternEncodingTermMenuAccessor;
import com.extendedae_plus.util.PatternProviderDataUtil;
import com.extendedae_plus.util.PatternTerminalUtil;
import com.extendedae_plus.util.wireless.WirelessTerminalLocator;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.WTDefinition;
import de.mari_023.ae2wtlib.wut.WUTHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * 与样板供应器（provider）上传相关的工具类：
 * - uploadPatternToProvider (从玩家背包上传)
 * - uploadFromEncodingMenuToProvider (从编码终端上传至指定 providerId)
 * - uploadFromEncodingMenuToProviderByIndex (按网格顺序 index 上传)
 *
 * 其中使用 PatternTerminalUtil 提供的反射/容器访问工具。
 */
public final class ProviderUploadUtil {
    private static final String PENDING_DATA_KEY = "eap_ctrlq_pending_provider_upload_id";
    private static final String PENDING_STACK_KEY = "eap_ctrlq_pending_provider_upload_stack";
    private static final String LAST_UPLOADED_PROVIDER_KEY = "eap_last_uploaded_provider_id";

    private ProviderUploadUtil() {}

    /**
     * 发送消息给玩家
     *
     * @param player 玩家
     * @param message 消息内容
     */
    private static void sendMessage(ServerPlayer player, String message) {
        // 静默：不再向玩家左下角发送任何提示信息
        // 如需恢复，取消下面注释即可：
        // if (player != null) {
        //     player.sendSystemMessage(Component.literal(message));
        // }
        // 如果玩家为null，静默忽略（用于测试环境）
    }

    /**
     * 将玩家背包中的样板上传到指定的样板供应器
     * 兼容ExtendedAE和原版AE2
     *
     * @param player 玩家
     * @param playerSlotIndex 玩家背包槽位索引
     * @param providerId 目标样板供应器的服务器ID
     * @return 是否上传成功
     */
    public static boolean uploadPatternToProvider(ServerPlayer player, int playerSlotIndex, long providerId) {
        // 1. 验证玩家是否打开了样板访问终端
        PatternAccessTermMenu menu = PatternTerminalUtil.getPatternAccessMenu(player);
        if (menu == null) {
            sendMessage(player, "ExtendedAE Plus: 请先打开样板访问终端或扩展样板管理终端");
            return false;
        }

        // 2. 获取玩家背包中的物品
        ItemStack playerItem = player.getInventory().getItem(playerSlotIndex);
        if (playerItem.isEmpty()) {
            sendMessage(player, "ExtendedAE Plus: 背包槽位为空");
            return false;
        }

        // 3. 验证是否是编码样板
        if (!PatternDetailsHelper.isEncodedPattern(playerItem)) {
            sendMessage(player, "ExtendedAE Plus: 该物品不是有效的编码样板");
            return false;
        }

        // 4. 获取目标样板供应器
        PatternContainer patternContainer = PatternTerminalUtil.getPatternContainerById(menu, providerId);
        if (patternContainer == null) {
            sendMessage(player, "ExtendedAE Plus: 找不到指定的样板供应器 (ID: " + providerId + ")");
            return false;
        }

        // 5. 获取样板供应器的库存
        InternalInventory patternInventory = patternContainer.getTerminalPatternInventory();
        if (patternInventory == null) {
            sendMessage(player, "ExtendedAE Plus: 无法访问样板供应器的库存");
            return false;
        }

        // 6. 使用AE2的标准样板过滤器进行插入
        var patternFilter = new ExtendedAEPatternFilter();
        var filteredInventory = new FilteredInternalInventory(patternInventory, patternFilter);

        // 7. 尝试插入样板
        ItemStack itemToInsert = playerItem.copy();
        ItemStack remaining = filteredInventory.addItems(itemToInsert);

        if (remaining.getCount() < itemToInsert.getCount()) {
            // 插入成功（部分或全部）
            int insertedCount = itemToInsert.getCount() - remaining.getCount();
            playerItem.shrink(insertedCount);

            if (playerItem.isEmpty()) {
                player.getInventory().setItem(playerSlotIndex, ItemStack.EMPTY);
            }

            player.getPersistentData().putLong(LAST_UPLOADED_PROVIDER_KEY, providerId);

            String terminalType = PatternTerminalUtil.isExtendedAETerminal(player) ? "扩展样板管理终端" : "样板访问终端";
            sendMessage(player, "ExtendedAE Plus: 通过" + terminalType + "成功上传 " + insertedCount + " 个样板");
            return true;
        } else {
            sendMessage(player, "ExtendedAE Plus: 上传失败 - 样板供应器已满或样板无效");
            return false;
        }
    }

    /**
     * 将图样编码终端的“已编码图样”上传到指定的样板供应器（通过 providerId 定位）。
     */
    public static boolean uploadFromEncodingMenuToProvider(ServerPlayer player, PatternEncodingTermMenu menu, long providerId) {
        if (player == null || menu == null) {
            return false;
        }
        var encodedSlot = ((PatternEncodingTermMenuAccessor) (Object) menu)
                .eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        PatternAccessTermMenu accessMenu = PatternTerminalUtil.getPatternAccessMenu(player);
        if (accessMenu == null) {
            return false;
        }
        // 先确定目标容器名称，用于同名回退
        String targetName = PatternProviderDataUtil.getProviderDisplayName(providerId, accessMenu);
        // 构建尝试顺序：先指定ID，其次同名的其他ID
        java.util.List<Long> tryIds = new java.util.ArrayList<>();
        tryIds.add(providerId);
        try {
            java.util.List<Long> all = PatternTerminalUtil.getAllProviderIds(accessMenu);
            for (Long id : all) {
                if (id == null || id == providerId) continue;
                String name = PatternProviderDataUtil.getProviderDisplayName(id, accessMenu);
                if (name != null && name.equals(targetName)) {
                    tryIds.add(id);
                }
            }
        } catch (Throwable ignored) {}

        // 按顺序逐个尝试插入
        for (Long id : tryIds) {
            PatternContainer c = PatternTerminalUtil.getPatternContainerById(accessMenu, id);
            if (c == null || !c.isVisibleInTerminal()) continue;
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;

            var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
            ItemStack toInsert = stack.copy();
            ItemStack remain = filtered.addItems(toInsert);
            if (remain.getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - remain.getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                player.getPersistentData().putLong(LAST_UPLOADED_PROVIDER_KEY, id);
                return true;
            }
        }
        return false;
    }

    /**
     * 基于“索引”的定向上传：使用 listAvailableProvidersFromGrid(menu) 的顺序，
     * 将编码槽样板插入到第 index 个供应器。
     */
    public static boolean uploadFromEncodingMenuToProviderByIndex(ServerPlayer player, PatternEncodingTermMenu menu, int index) {
        if (player == null || menu == null || index < 0) return false;
        List<PatternContainer> list = PatternTerminalUtil.listAvailableProvidersFromGrid(menu);
        if (index >= list.size()) return false;
        var container = list.get(index);
        if (container == null) return false;

        var encodedSlot = ((PatternEncodingTermMenuAccessor) (Object) menu)
                .eap$getEncodedPatternSlot();
        ItemStack stack = encodedSlot.getItem();
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }

        // 以名称为键，同名供应器依次尝试：先 index 指定的，再同名的其他
        String targetName = PatternProviderDataUtil.getProviderDisplayName(container);
        java.util.List<PatternContainer> tryList = new java.util.ArrayList<>();
        tryList.add(container);
        try {
            for (PatternContainer c : list) {
                if (c == null || c == container) continue;
                String name = PatternProviderDataUtil.getProviderDisplayName(c);
                if (name != null && name.equals(targetName)) {
                    tryList.add(c);
                }
            }
        } catch (Throwable ignored) {}

        for (PatternContainer c : tryList) {
            InternalInventory inv = c.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;
            var filtered = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter());
            ItemStack toInsert = stack.copy();
            ItemStack remain = filtered.addItems(toInsert);
            if (remain.getCount() < toInsert.getCount()) {
                int inserted = toInsert.getCount() - remain.getCount();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    encodedSlot.set(ItemStack.EMPTY);
                } else {
                    encodedSlot.set(stack);
                }
                
                // For index-based, we can store the negative index to let the return logic resolve it
                player.getPersistentData().putLong(LAST_UPLOADED_PROVIDER_KEY, index == 0 && c == list.get(0) ? (-1L - index) : -1000000L); // store specifically if logic allows, but to be sure let's store the index format
                // actually we know `c` might be from tryList, let's find the true index of `c` in `list`
                int trueIndex = list.indexOf(c);
                if (trueIndex >= 0) {
                    player.getPersistentData().putLong(LAST_UPLOADED_PROVIDER_KEY, -1L - trueIndex);
                }

                return true;
            }
        }
        return false;
    }

    /**
     * 缓存 Ctrl+Q 生成的待上传样板（不放入玩家背包）。
     */
    public static String beginPendingCtrlQUpload(ServerPlayer player, ItemStack pattern) {
        if (player == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
            return null;
        }
        clearPendingCtrlQUpload(player);
        String id = UUID.randomUUID().toString();
        player.getPersistentData().putString(PENDING_DATA_KEY, id);
        player.getPersistentData().put(PENDING_STACK_KEY, pattern.copy().save(new CompoundTag()));
        return id;
    }

    public static void clearPendingCtrlQUpload(ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(PENDING_DATA_KEY);
        player.getPersistentData().remove(PENDING_STACK_KEY);
    }

    public static boolean hasPendingCtrlQPattern(ServerPlayer player) {
        if (player == null) return false;
        String id = player.getPersistentData().getString(PENDING_DATA_KEY);
        if (id == null || id.isBlank()) return false;
        return !getPendingCtrlQPattern(player).isEmpty();
    }

    /**
     * 将 pending Ctrl+Q 样板上传到玩家网络中的目标 provider（负数索引 ID）。
     */
    public static boolean uploadPendingCtrlQPattern(ServerPlayer player, long providerId) {
        if (player == null) return false;
        ItemStack pending = getPendingCtrlQPattern(player);
        if (pending.isEmpty()) return false;

        ItemStack remain = insertPatternIntoProviderFromPlayerNetwork(player, pending, providerId);
        if (remain.getCount() >= pending.getCount()) {
            return false;
        }

        if (remain.isEmpty()) {
            clearPendingCtrlQUpload(player);
        } else {
            player.getPersistentData().put(PENDING_STACK_KEY, remain.save(new CompoundTag()));
        }

        player.getPersistentData().putLong(LAST_UPLOADED_PROVIDER_KEY, providerId);
        return true;
    }

    /**
     * 将 pending Ctrl+Q 样板回退到玩家背包；若背包已满则掉落在地上。
     */
    public static boolean returnPendingCtrlQPatternToInventory(ServerPlayer player) {
        if (player == null) return false;
        ItemStack pending = getPendingCtrlQPattern(player);
        if (pending.isEmpty()) return false;

        clearPendingCtrlQUpload(player);
        if (!player.getInventory().add(pending.copy())) {
            player.drop(pending.copy(), false);
        }
        return true;
    }

    /**
     * 列出玩家无线终端网络中的可用 provider，顺序与负数索引上传保持一致。
     */
    public static List<PatternContainer> listAvailableProvidersFromPlayerNetwork(ServerPlayer player) {
        IGrid grid = findPlayerGrid(player);
        return PatternTerminalUtil.listAvailableProvidersFromGrid(grid);
    }

    private static ItemStack getPendingCtrlQPattern(ServerPlayer player) {
        if (player == null) return ItemStack.EMPTY;
        String id = player.getPersistentData().getString(PENDING_DATA_KEY);
        if (id == null || id.isBlank()) return ItemStack.EMPTY;

        CompoundTag data = player.getPersistentData();
        if (!data.contains(PENDING_STACK_KEY)) return ItemStack.EMPTY;
        CompoundTag stackTag = data.getCompound(PENDING_STACK_KEY);
        ItemStack stack = ItemStack.of(stackTag);
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            clearPendingCtrlQUpload(player);
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static ItemStack insertPatternIntoProviderFromPlayerNetwork(ServerPlayer player, ItemStack pattern, long providerId) {
        if (player == null || pattern == null || pattern.isEmpty() || !PatternDetailsHelper.isEncodedPattern(pattern)) {
            return pattern == null ? ItemStack.EMPTY : pattern;
        }

        int index = decodeProviderIndex(providerId);
        if (index < 0) return pattern;

        List<PatternContainer> providers = listAvailableProvidersFromPlayerNetwork(player);
        if (index >= providers.size()) return pattern;

        PatternContainer target = providers.get(index);
        if (target == null) return pattern;

        ItemStack remain = pattern.copy();
        for (PatternContainer container : buildSameNameTryList(providers, target)) {
            InternalInventory inv = container.getTerminalPatternInventory();
            if (inv == null || inv.size() <= 0) continue;

            ItemStack nextRemain = new FilteredInternalInventory(inv, new ExtendedAEPatternFilter()).addItems(remain.copy());
            if (nextRemain.getCount() < remain.getCount()) {
                remain = nextRemain;
                if (remain.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return remain;
    }

    private static int decodeProviderIndex(long providerId) {
        if (providerId >= 0) return -1;
        long idx = -1L - providerId;
        if (idx > Integer.MAX_VALUE) return -1;
        return (int) idx;
    }

    private static List<PatternContainer> buildSameNameTryList(List<PatternContainer> all, PatternContainer target) {
        String targetName = PatternProviderDataUtil.getProviderDisplayName(target);
        List<PatternContainer> tryList = new java.util.ArrayList<>();
        tryList.add(target);
        for (PatternContainer container : all) {
            if (container == null || container == target) continue;
            String name = PatternProviderDataUtil.getProviderDisplayName(container);
            if (name != null && name.equals(targetName)) {
                tryList.add(container);
            }
        }
        return tryList;
    }

    private static IGrid findPlayerGrid(ServerPlayer player) {
        WirelessTerminalLocator.LocatedTerminal located = WirelessTerminalLocator.find(player);
        ItemStack terminal = located.stack;
        if (terminal.isEmpty()) {
            return null;
        }

        String curiosSlotId = located.getCuriosSlotId();
        int curiosIndex = located.getCuriosIndex();

        if (curiosSlotId != null && curiosIndex >= 0) {
            try {
                String current = WUTHandler.getCurrentTerminal(terminal);
                WTDefinition def = WUTHandler.wirelessTerminals.get(current);
                if (def != null) {
                    WTMenuHost wtHost = def.wTMenuHostFactory().create(player, null, terminal, (p, sub) -> {});
                    if (wtHost != null) {
                        IGridNode node = wtHost.getActionableNode();
                        if (node != null) {
                            return node.getGrid();
                        }
                    }
                }
            } catch (Exception ignored) {
                return null;
            }
        } else {
            WirelessTerminalItem wt = terminal.getItem() instanceof WirelessTerminalItem t ? t : null;
            if (wt != null) {
                return wt.getLinkedGrid(terminal, player.serverLevel(), player);
            }
        }

        return null;
    }

    public static long getLastUploadedProviderId(ServerPlayer player) {
        if (player == null) return Long.MIN_VALUE;
        CompoundTag data = player.getPersistentData();
        if (data.contains(LAST_UPLOADED_PROVIDER_KEY)) {
            return data.getLong(LAST_UPLOADED_PROVIDER_KEY);
        }
        return Long.MIN_VALUE;
    }

    /**
     * ExtendedAE兼容的样板过滤器
     * 使用AE2的PatternDetailsHelper进行样板验证
     */
    private static class ExtendedAEPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }
    }
}
