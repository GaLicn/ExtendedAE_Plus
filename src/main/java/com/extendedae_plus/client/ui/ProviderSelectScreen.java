package com.extendedae_plus.client.ui;

import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简单的供应器选择弹窗。
 * 展示若干个可点击的供应器条目，点击后发送带 providerId 的上传请求。
 */
public class ProviderSelectScreen extends Screen {
    private final Screen parent;
    // 原始数据
    private final List<Long> ids;
    private final List<String> names;
    private final List<Integer> emptySlots;

    // 分组后的数据（同名合并）
    private final List<Long> gIds = new ArrayList<>();           // 代表条目使用的 providerId：选择空位数最多的那个
    private final List<String> gNames = new ArrayList<>();        // 分组名（供应器名称）
    private final List<Integer> gTotalSlots = new ArrayList<>();  // 该名称下供应器空位总和
    private final List<Integer> gCount = new ArrayList<>();       // 该名称下供应器数量

    // 过滤后的数据（由查询生成）
    private final List<Long> fIds = new ArrayList<>();
    private final List<String> fNames = new ArrayList<>();
    private final List<Integer> fTotalSlots = new ArrayList<>();
    private final List<Integer> fCount = new ArrayList<>();

    // 搜索框
    private EditBox searchBox;
    // 中文名输入框（用于添加映射）
    private EditBox cnInput;
    private String query = "";
    private boolean needsRefresh = false;

    private int page = 0;
    private static final int PAGE_SIZE = 6;

    private final List<Button> entryButtons = new ArrayList<>();

    public ProviderSelectScreen(Screen parent, List<Long> ids, List<String> names, List<Integer> emptySlots) {
        super(Component.translatable("extendedae_plus.screen.choose_provider.title"));
        this.parent = parent;
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
        // 如果有来自 JEI 的最近处理名称，则作为初始查询
        try {
            String recent = com.extendedae_plus.util.ExtendedAEPatternUploadUtil.lastProcessingName;
            if (recent != null && !recent.isBlank()) {
                this.query = recent;
                // 用后即清空，避免污染下次
                com.extendedae_plus.util.ExtendedAEPatternUploadUtil.lastProcessingName = null;
            }
        } catch (Throwable ignored) {}
        buildGroups();
        applyFilter();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        entryButtons.clear();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 70;

        // 搜索框（置于条目上方）
        if (searchBox == null) {
            searchBox = new EditBox(this.font, centerX - 120, startY - 25, 240, 18, Component.translatable("extendedae_plus.screen.search"));
        } else {
            // 重新定位，保持输入值
            searchBox.setX(centerX - 120);
            searchBox.setY(startY - 25);
            searchBox.setWidth(240);
        }
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            // 只有当输入真正发生变化时，才重置页码与过滤
            if (Objects.equals(text, query)) return;
            query = text;
            page = 0;
            applyFilter();
            // 避免在回调中直接重建 UI，延迟到下一次 tick
            needsRefresh = true;
        });
        this.addRenderableWidget(searchBox);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, fIds.size());

        int buttonWidth = 240;
        int buttonHeight = 20;
        int gap = 5;

        for (int i = start; i < end; i++) {
            int idx = i;
            String label = buildLabel(idx);
            Button btn = Button.builder(Component.literal(label), b -> onChoose(idx))
                    .bounds(centerX - buttonWidth / 2, startY + (i - start) * (buttonHeight + gap), buttonWidth, buttonHeight)
                    .build();
            entryButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        // 分页按钮
        int navY = startY + PAGE_SIZE * (buttonHeight + gap) + 10;
        Button prev = Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(centerX - 60, navY, 20, 20)
                .build();
        Button next = Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(centerX + 40, navY, 20, 20)
                .build();
        prev.active = page > 0;
        next.active = (page + 1) * PAGE_SIZE < fIds.size();
        this.addRenderableWidget(prev);
        this.addRenderableWidget(next);

        // 重载映射按钮（热重载 recipe_type_names.json）——移至下一行，与关闭按钮并排
        Button reload = Button.builder(Component.translatable("extendedae_plus.screen.reload_mapping"), b -> reloadMapping())
                .bounds(centerX - 130, navY + 30, 80, 20)
                .build();
        this.addRenderableWidget(reload);

        // 中文名输入框（用于新增映射的值）
        if (cnInput == null) {
            cnInput = new EditBox(this.font, centerX + 50, navY + 30, 120, 20, Component.translatable("extendedae_plus.screen.cn_name"));
        } else {
            cnInput.setX(centerX + 50);
            cnInput.setY(navY + 30);
            cnInput.setWidth(120);
        }
        this.addRenderableWidget(cnInput);

        // 增加映射按钮（使用当前搜索关键字 -> 中文）
        Button addMap = Button.builder(Component.translatable("extendedae_plus.screen.add_mapping"), b -> addMappingFromUI())
                .bounds(centerX + 175, navY + 30, 60, 20)
                .build();
        this.addRenderableWidget(addMap);

        // 删除映射（按中文值精确匹配删除）按钮
        Button delByCn = Button.builder(Component.literal("删除映射"), b -> deleteMappingByCnFromUI())
                .bounds(centerX + 240, navY + 30, 60, 20)
                .build();
        this.addRenderableWidget(delByCn);

        // 关闭按钮
        Button close = Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(centerX - 40, navY + 30, 80, 20)
                .build();
        this.addRenderableWidget(close);
    }

    private void changePage(int delta) {
        int newPage = page + delta;
        if (newPage < 0) return;
        if (newPage * PAGE_SIZE >= fIds.size()) return;
        page = newPage;
        // 避免在回调中直接重建 UI，改为下帧刷新
        needsRefresh = true;
    }

    private void reloadMapping() {
        try {
            com.extendedae_plus.util.ExtendedAEPatternUploadUtil.loadRecipeTypeNames();
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("ExtendedAE Plus: 已重载映射表"));
            }
            // 重载后不强制刷新筛选，但如需立即应用到名称匹配，可手动编辑搜索框或翻页
        } catch (Throwable t) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("ExtendedAE Plus: 重载映射表失败: " + t.getClass().getSimpleName()));
            }
        }
    }

    private String buildLabel(int idx) {
        String name = fNames.get(idx);
        int totalSlots = fTotalSlots.get(idx);
        int count = fCount.get(idx);
        // 不显示具体 id，显示合并统计：名称（总空位）x数量
        return name + "  (" + totalSlots + ")  x" + count;
    }

    private void onChoose(int idx) {
        if (idx < 0 || idx >= fIds.size()) return;
        long providerId = fIds.get(idx);
        ModNetwork.CHANNEL.sendToServer(new UploadEncodedPatternToProviderC2SPacket(providerId));
        this.onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void buildGroups() {
        // 使用 LinkedHashMap 保持首次出现顺序
        Map<String, Group> map = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            long id = ids.get(i);
            int slots = emptySlots.get(i);
            Group g = map.computeIfAbsent(name, k -> new Group());
            g.count++;
            g.totalSlots += Math.max(0, slots);
            // 挑选空位最多的作为代表 id；若并列，保留先到者
            if (slots > g.bestSlots) {
                g.bestSlots = slots;
                g.bestId = id;
            }
        }
        for (Map.Entry<String, Group> e : map.entrySet()) {
            String name = e.getKey();
            Group g = e.getValue();
            gNames.add(name);
            gIds.add(g.bestId);
            gTotalSlots.add(g.totalSlots);
            gCount.add(g.count);
        }
    }

    private static class Group {
        long bestId = Long.MIN_VALUE;
        int bestSlots = Integer.MIN_VALUE;
        int totalSlots = 0;
        int count = 0;
    }

    private void applyFilter() {
        fIds.clear();
        fNames.clear();
        fTotalSlots.clear();
        fCount.clear();
        String q = query == null ? "" : query.trim();
        for (int i = 0; i < gIds.size(); i++) {
            String name = gNames.get(i);
            if (q.isEmpty() || nameMatches(name, q)) {
                fIds.add(gIds.get(i));
                fNames.add(name);
                fTotalSlots.add(gTotalSlots.get(i));
                fCount.add(gCount.get(i));
            }
        }
        // 若查询不为空但没有任何匹配，则回退为显示全部，避免“空列表”误导用户
        if (!q.isEmpty() && fIds.isEmpty()) {
            for (int i = 0; i < gIds.size(); i++) {
                fIds.add(gIds.get(i));
                fNames.add(gNames.get(i));
                fTotalSlots.add(gTotalSlots.get(i));
                fCount.add(gCount.get(i));
            }
        }
    }

    // 优先使用 JEC 的拼音匹配，否则回退到大小写不敏感子串匹配
    private static Boolean JEC_AVAILABLE = null;
    private static java.lang.reflect.Method JEC_CONTAINS = null;

    private static boolean nameMatches(String name, String key) {
        if (name == null) return false;
        if (key == null || key.isEmpty()) return true;
        try {
            if (JEC_AVAILABLE == null) {
                try {
                    Class<?> cls = Class.forName("me.towdium.jecharacters.utils.Match");
                    // 使用 contains(CharSequence, CharSequence)
                    JEC_CONTAINS = cls.getMethod("contains", CharSequence.class, CharSequence.class);
                    JEC_AVAILABLE = true;
                } catch (Throwable t) {
                    JEC_AVAILABLE = false;
                }
            }
            if (Boolean.TRUE.equals(JEC_AVAILABLE) && JEC_CONTAINS != null) {
                Object r = JEC_CONTAINS.invoke(null, name, key);
                if (r instanceof Boolean && (Boolean) r) return true;
                // 再尝试大小写不敏感：双方转为小写重新匹配
                String nL = name.toLowerCase();
                String kL = key.toLowerCase();
                Object r2 = JEC_CONTAINS.invoke(null, nL, kL);
                if (r2 instanceof Boolean && (Boolean) r2) return true;
            }
        } catch (Throwable ignored) {
            // 回退
        }
        // 默认大小写不敏感子串
        return name.toLowerCase().contains(key.toLowerCase());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) {
            searchBox.tick();
        }
        if (cnInput != null) {
            cnInput.tick();
        }
        if (needsRefresh) {
            needsRefresh = false;
            // 重新构建当前屏幕内容
            init();
        }
    }

    private void addMappingFromUI() {
        String key = query == null ? "" : query.trim();
        String val = cnInput == null ? "" : cnInput.getValue().trim();
        var player = Minecraft.getInstance().player;
        if (key.isEmpty()) {
            if (player != null) player.sendSystemMessage(Component.literal("请输入搜索关键字后再添加映射"));
            return;
        }
        if (val.isEmpty()) {
            if (player != null) player.sendSystemMessage(Component.literal("请输入中文名称"));
            return;
        }
        boolean ok = com.extendedae_plus.util.ExtendedAEPatternUploadUtil.addOrUpdateAliasMapping(key, val);
        if (ok) {
            if (player != null) player.sendSystemMessage(Component.literal("已添加/更新映射: " + key + " -> " + val));
            // 更新本地过滤显示（若名称包含中文可被搜索）
            applyFilter();
            needsRefresh = true;
        } else {
            if (player != null) player.sendSystemMessage(Component.literal("写入映射失败"));
        }
    }

    // 使用中文值精确匹配删除映射
    private void deleteMappingByCnFromUI() {
        String val = cnInput == null ? "" : cnInput.getValue().trim();
        var player = Minecraft.getInstance().player;
        if (val.isEmpty()) {
            if (player != null) player.sendSystemMessage(Component.literal("请输入中文名称后再删除映射"));
            return;
        }
        int removed = com.extendedae_plus.util.ExtendedAEPatternUploadUtil.removeMappingsByCnValue(val);
        if (removed > 0) {
            if (player != null) player.sendSystemMessage(Component.literal("已删除 " + removed + " 条映射，中文= " + val));
            applyFilter();
            needsRefresh = true;
        } else {
            if (player != null) player.sendSystemMessage(Component.literal("未找到中文为 '" + val + "' 的映射"));
        }
    }
}
