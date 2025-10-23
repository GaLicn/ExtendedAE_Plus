package com.extendedae_plus.client.screen;

import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final List<Long> gIds = new ArrayList<>();              // 代表条目使用的 providerId：选择空位数最多的那个
    private final List<String> gNames = new ArrayList<>();          // 分组名（供应器名称）
    private final List<Integer> gTotalSlots = new ArrayList<>();    // 该名称下供应器空位总和
    private final List<Integer> gCount = new ArrayList<>();         // 该名称下供应器数量

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
    // 翻页按钮
    private Button prevButton;
    private Button nextButton;

    // 页面
    private int page = 0;
    private static final int PAGE_SIZE = 6;

    // 按钮池
    private final List<Button> entryButtons = new ArrayList<>();
    private final int[] buttonIndexMap = new int[PAGE_SIZE]; // 映射到 fIds 的索引

    // 缓存 Component JSON 解析
    private static final Map<String, String> componentCache = new HashMap<>();
    private String lastLanguage = ""; // 当前语言版本

    public ProviderSelectScreen(Screen parent, List<Long> ids, List<String> names, List<Integer> emptySlots) {
        super(Component.translatable("extendedae_plus.screen.choose_provider.title"));
        this.parent = parent;
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
        // 如果有来自 JEI 的最近处理名称，则作为初始查询
        try {
            String recent = ExtendedAEPatternUploadUtil.lastProcessingName;
            if (recent != null && !recent.isBlank()) {
                this.query = recent;
                // 用后即清空，避免污染下次
                ExtendedAEPatternUploadUtil.lastProcessingName = null;
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
            refreshButtons();
        });
        this.addRenderableWidget(searchBox);

        // 初始化按钮池
        int buttonWidth = 240;
        int buttonHeight = 20;
        int gap = 5;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int btnIdx = i;
            Button btn = Button.builder(Component.literal(""), b -> {
                        int actualIdx = buttonIndexMap[btnIdx];
                        if (actualIdx >= 0 && actualIdx < fIds.size()) {
                            onChoose(actualIdx);
                        }
                    }).bounds(centerX - buttonWidth / 2, startY + i * (buttonHeight + gap), buttonWidth, buttonHeight)
                    .build();
            entryButtons.add(btn);
            buttonIndexMap[i] = -1; // 初始化为无效索引
            this.addRenderableWidget(btn);
        }

        // 分页按钮
        int navY = startY + PAGE_SIZE * (buttonHeight + gap) + 10;
        prevButton = Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(centerX - 60, navY, 20, 20)
                .build();
        nextButton = Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(centerX + 40, navY, 20, 20)
                .build();
        this.addRenderableWidget(prevButton);
        this.addRenderableWidget(nextButton);

        // 映射按钮和输入框
        // 统一按钮宽度
        int btnWidth2 = 80;
        int inputWidth = 120;
        int btnGap = 5;

        // 总宽度 = 重载按钮 + 输入框 + 添加 + 删除 + 关闭按钮 + 间距
        int totalWidth = btnWidth2 + btnGap + inputWidth + btnGap + btnWidth2 * 2 + btnGap + btnWidth2;
        int startX = centerX - totalWidth / 2;

        // 重载映射按钮
        Button reload = Button.builder(Component.translatable("extendedae_plus.screen.reload_mapping"), b -> reloadMapping())
                .bounds(startX, navY + 30, btnWidth2, 20)
                .build();
        this.addRenderableWidget(reload);

        // 中文名输入框（用于新增映射的值）
        if (cnInput == null) {
            cnInput = new EditBox(this.font, startX + btnWidth2 + btnGap, navY + 30, inputWidth, 20, Component.translatable("extendedae_plus.screen.upload.name"));
        } else {
            cnInput.setX(startX + btnWidth2 + btnGap);
            cnInput.setY(navY + 30);
            cnInput.setWidth(inputWidth);
        }
        this.addRenderableWidget(cnInput);

        // 关闭按钮
        Button close = Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(startX + btnWidth2 + btnGap + inputWidth + btnGap, navY + 30, btnWidth2, 20)
                .build();
        this.addRenderableWidget(close);

        // 添加映射按钮（使用当前搜索关键字 -> 中文）
        Button addMap = Button.builder(Component.translatable("extendedae_plus.screen.add_mapping"), b -> addMappingFromUI())
                .bounds(startX + btnWidth2 + btnGap + inputWidth + btnGap + btnWidth2 + btnGap, navY + 30, btnWidth2, 20)
                .build();
        this.addRenderableWidget(addMap);

        // 删除映射按钮（按中文值精确匹配删除）按钮
        Button delByCn = Button.builder(Component.translatable("extendedae_plus.screen.delete_mapping"), b -> deleteMappingByCnFromUI())
                .bounds(startX + btnWidth2 + btnGap + inputWidth + btnGap + btnWidth2 * 2 + btnGap * 2, navY + 30, btnWidth2, 20)
                .build();
        this.addRenderableWidget(delByCn);

        refreshButtons(); // 初始化完成后刷新按钮状态
    }

    private void changePage(int delta) {
        int newPage = page + delta;
        if (newPage < 0 || newPage * PAGE_SIZE >= fIds.size()) return;
        page = newPage;
        refreshButtons();
    }

    private void refreshButtons() {
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, fIds.size());
        for (int i = 0; i < PAGE_SIZE; i++) {
            Button btn = entryButtons.get(i);
            int idx = start + i;
            if (idx < end) {
                btn.visible = true;
                btn.active = true;
                btn.setMessage(Component.literal(buildLabel(idx)));
                buttonIndexMap[i] = idx;
            } else {
                btn.visible = false;
                btn.active = false;
                buttonIndexMap[i] = -1;
            }
        }
        if (prevButton != null) prevButton.active = page > 0;
        if (nextButton != null) nextButton.active = fIds.size() > (page + 1) * PAGE_SIZE;
    }

    private void reloadMapping() {
        try {
            ExtendedAEPatternUploadUtil.loadRecipeTypeNames();
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.reload_mapping_success"));
            }
            // 重载后不强制刷新筛选，但如需立即应用到名称匹配，可手动编辑搜索框或翻页
        } catch (Throwable t) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.reload_mapping_fail", t.getClass().getSimpleName()));
            }
        }
    }

    /**
     * 将服务器发送的名称（可能是 Component JSON）反序列化为本地化文本
     */
    private String deserializeComponentName(String name) {
        return componentCache.computeIfAbsent(name, k -> {
            try {
                // 如果名称是 JSON 格式的 Component，反序列化后获取本地化文本
                if (name.startsWith("{") || name.startsWith("\"")) {
                    Component component = Component.Serializer.fromJson(name);
                    if (component != null) {
                        return component.getString();
                    }
                }
            } catch (Exception ignored) {
                // 如果不是 JSON 或解析失败，使用原始字符串
            }
            return name;
        });
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

            // 将 Component JSON 转换为本地化文本用于分组键
            String groupKey = deserializeComponentName(name);
            map.compute(groupKey, (k, g) -> {
                if (g == null) {
                    return new Group(id, slots);
                }
                g.merge(id, slots);
                return g;
            });
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

    private void applyFilter() {
        fIds.clear();
        fNames.clear();
        fTotalSlots.clear();
        fCount.clear();
        String q = query == null ? "" : query.trim();
        String qLower = q.toLowerCase(Locale.ROOT);

        for (int i = 0; i < gIds.size(); i++) {
            String name = gNames.get(i);
            if (q.isEmpty() || nameMatches(name, q, qLower)) {
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

        // 对 fNames 进行自然排序，同时同步其它列表
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < fNames.size(); i++) indices.add(i);
        indices.sort((i1, i2) -> compareNatural(fNames.get(i1), fNames.get(i2)));

        List<Long> sortedIds = new ArrayList<>();
        List<String> sortedNames = new ArrayList<>();
        List<Integer> sortedSlots = new ArrayList<>();
        List<Integer> sortedCount = new ArrayList<>();

        for (int idx : indices) {
            sortedIds.add(fIds.get(idx));
            sortedNames.add(fNames.get(idx));
            sortedSlots.add(fTotalSlots.get(idx));
            sortedCount.add(fCount.get(idx));
        }

        fIds.clear();
        fIds.addAll(sortedIds);
        fNames.clear();
        fNames.addAll(sortedNames);
        fTotalSlots.clear();
        fTotalSlots.addAll(sortedSlots);
        fCount.clear();
        fCount.addAll(sortedCount);
    }

    // 优先使用 JEC 的拼音匹配，否则回退到大小写不敏感子串匹配
    private static Boolean JEC_AVAILABLE = null;
    private static java.lang.reflect.Method JEC_CONTAINS = null;

    private static boolean nameMatches(String name, String key, String keyLower) {
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
                Object r2 = JEC_CONTAINS.invoke(null, name.toLowerCase(Locale.ROOT), keyLower);
                if (r2 instanceof Boolean && (Boolean) r2) return true;
            }
        } catch (Throwable ignored) {
            // 回退
        }
        // 默认大小写不敏感子串
        return name.toLowerCase(Locale.ROOT).contains(keyLower);
    }

    // 自然排序比较方法
    private static final Pattern NATURAL_PATTERN = Pattern.compile("(\\D*)(\\d*)");
    private static int compareNatural(String s1, String s2) {
        Matcher m1 = NATURAL_PATTERN.matcher(s1);
        Matcher m2 = NATURAL_PATTERN.matcher(s2);

        while (m1.find() && m2.find()) {
            // 比较非数字部分
            int cmp = m1.group(1).compareTo(m2.group(1));
            if (cmp != 0) return cmp;

            // 比较数字部分
            String num1 = m1.group(2);
            String num2 = m2.group(2);
            if (!num1.isEmpty() || !num2.isEmpty()) {
                int n1 = num1.isEmpty() ? 0 : Integer.parseInt(num1);
                int n2 = num2.isEmpty() ? 0 : Integer.parseInt(num2);
                if (n1 != n2) return Integer.compare(n1, n2);
            }
        }
        return s1.length() - s2.length();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右键点击搜索框区域时，清空搜索框内容并刷新
        if (button == 1 && this.searchBox != null) {
            int x = this.searchBox.getX();
            int y = this.searchBox.getY();
            int w = this.searchBox.getWidth();
            int h = this.searchBox.getHeight();
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                if (!this.searchBox.getValue().isEmpty()) {
                    this.searchBox.setValue("");
                }
                this.query = "";
                this.page = 0;
                applyFilter();
                refreshButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

        String currentLang = Minecraft.getInstance().options.languageCode;
        if (!currentLang.equals(lastLanguage)) {
            lastLanguage = currentLang;
            componentCache.clear();
            refreshButtons();
        }
    }

    private void addMappingFromUI() {
        String key = query == null ? "" : query.trim();
        String val = cnInput == null ? "" : cnInput.getValue().trim();
        var player = Minecraft.getInstance().player;
        if (key.isEmpty()) {
            if (player != null)
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.upload.enter_search_key"));
            return;
        }
        if (val.isEmpty()) {
            if (player != null)
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.upload.enter_cn_name"));
            return;
        }
        boolean ok = ExtendedAEPatternUploadUtil.addOrUpdateAliasMapping(key, val);
        if (ok) {
            if (player != null)
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.upload.mapping_added",
                        key, val));
            // 将刚添加的中文名写入搜索框，作为当前查询
            this.query = val;
            if (this.searchBox != null) {
                this.searchBox.setValue(val);
            }
            // 更新本地过滤显示
            applyFilter();
            page = 0;
            refreshButtons();
        } else {
            if (player != null)
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.upload.mapping_failed"));
        }
    }

    // 使用中文值精确匹配删除映射
    private void deleteMappingByCnFromUI() {
        String val = cnInput == null ? "" : cnInput.getValue().trim();
        var player = Minecraft.getInstance().player;
        if (val.isEmpty()) {
            if (player != null)
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.upload.enter_cn_name_delete"));
            return;
        }
        int removed = ExtendedAEPatternUploadUtil.removeMappingsByCnValue(val);
        if (removed > 0) {
            if (player != null)
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.upload.mapping_deleted", removed, val));
            applyFilter();
            page = 0;
            refreshButtons();
        } else {
            if (player != null)
                player.sendSystemMessage(Component.translatable("extendedae_plus.screen.upload.mapping_not_found", val));
        }
    }
    
    private static class Group {
        long bestId;
        int bestSlots;
        int totalSlots;
        int count;

        Group(long id, int slots) {
            this.bestId = id;
            this.bestSlots = slots;
            this.totalSlots = Math.max(0, slots);
            this.count = 1;
        }

        void merge(long id, int slots) {
            count++;
            totalSlots += Math.max(0, slots);
            // 挑选空位最多的作为代表 id；若并列，保留先到者
            if (slots > bestSlots) {
                bestSlots = slots;
                bestId = id;
            }
        }
    }
}
