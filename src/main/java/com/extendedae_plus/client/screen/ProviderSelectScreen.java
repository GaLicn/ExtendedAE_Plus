package com.extendedae_plus.client.screen;

import com.extendedae_plus.network.CancelPendingPatternC2SPacket;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单的供应器选择弹窗。
 * 展示若干个可点击的供应器条目，点击后发送带 providerId 的上传请求。
 */
public class ProviderSelectScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    // 优先使用 JEC 的拼音匹配，否则回退到大小写不敏感子串匹配
    private static Boolean JEC_AVAILABLE = null;
    private static java.lang.reflect.Method JEC_CONTAINS = null;

    // 置顶的供应器名称集合（静态变量，持久化到配置文件）
    private static final Set<String> pinnedProviders = new HashSet<>();
    private static final String PINNED_CONFIG_PATH = "extendedae_plus/pinned_providers.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // 静态初始化块：加载置顶配置
    static {
        try {
            loadPinnedProviders();
        } catch (Throwable t) {
            // 加载失败时静默处理，不影响界面使用
        }
    }

    private final Screen parent;
    // 原始数据
    private final List<Long> ids;
    private final List<Component> names; // 改为 Component
    private final List<Integer> emptySlots;
    // 分组后的数据（同名合并）
    private final List<Long> gIds = new ArrayList<>();           // 代表条目使用的 providerId：选择空位数最多的那个
    private final List<String> gNames = new ArrayList<>();        // 分组名（供应器名称）- 转换为 String 用于显示和匹配
    private final List<Integer> gTotalSlots = new ArrayList<>();  // 该名称下供应器空位总和
    private final List<Integer> gCount = new ArrayList<>();       // 该名称下供应器数量
    // 过滤后的数据（由查询生成）
    private final List<Long> fIds = new ArrayList<>();
    private final List<String> fNames = new ArrayList<>();
    private final List<Integer> fTotalSlots = new ArrayList<>();
    private final List<Integer> fCount = new ArrayList<>();
    private final List<Button> entryButtons = new ArrayList<>();
    // 搜索框
    private EditBox searchBox;
    // 中文名输入框（用于添加映射）
    private EditBox cnInput;
    private String query = "";
    private boolean needsRefresh = false;
    private int page = 0;

    public ProviderSelectScreen(Screen parent, List<Long> ids, List<Component> names, List<Integer> emptySlots) {
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
        this.buildGroups();
        this.applyFilter();
    }

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

    private void changePage(int delta) {
        int newPage = this.page + delta;
        if (newPage < 0) return;
        if (newPage * PAGE_SIZE >= this.fIds.size()) return;
        this.page = newPage;
        // 避免在回调中直接重建 UI，改为下帧刷新
        this.needsRefresh = true;
    }

    private void reloadMapping() {
        try {
            ExtendedAEPatternUploadUtil.loadRecipeTypeNames();
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.reload_success"));
            }
            // 重载后不强制刷新筛选，但如需立即应用到名称匹配，可手动编辑搜索框或翻页
        } catch (Throwable t) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.reload_fail", t.getClass().getSimpleName()));
            }
        }
    }

    private String buildLabel(int idx) {
        String name = this.fNames.get(idx);
        int totalSlots = this.fTotalSlots.get(idx);
        int count = this.fCount.get(idx);

        // 如果是置顶条目，在最左侧添加星星标志
        String prefix = pinnedProviders.contains(name) ? "★ " : "";

        // 不显示具体 id，显示合并统计：名称（总空位）x数量
        return prefix + name + "  (" + totalSlots + ")  x" + count;
    }

    private void onChoose(int idx) {
        if (idx < 0 || idx >= this.fIds.size()) return;
        long providerId = this.fIds.get(idx);
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new UploadEncodedPatternToProviderC2SPacket(providerId));
        }
        this.onClose();
    }

    private void buildGroups() {
        // 使用 LinkedHashMap 保持首次出现顺序
        Map<String, Group> map = new LinkedHashMap<>();
        for (int i = 0; i < this.names.size(); i++) {
            String name = this.names.get(i).getString(); // 将 Component 转换为 String
            long id = this.ids.get(i);
            int slots = this.emptySlots.get(i);
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
            this.gNames.add(name);
            this.gIds.add(g.bestId);
            this.gTotalSlots.add(g.totalSlots);
            this.gCount.add(g.count);
        }
    }

    private void applyFilter() {
        this.fIds.clear();
        this.fNames.clear();
        this.fTotalSlots.clear();
        this.fCount.clear();
        String q = this.query == null ? "" : this.query.trim();
        for (int i = 0; i < this.gIds.size(); i++) {
            String name = this.gNames.get(i);
            if (q.isEmpty() || nameMatches(name, q)) {
                this.fIds.add(this.gIds.get(i));
                this.fNames.add(name);
                this.fTotalSlots.add(this.gTotalSlots.get(i));
                this.fCount.add(this.gCount.get(i));
            }
        }

        // 对 fNames 进行排序，置顶的条目排在前面，然后按自然排序
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < this.fNames.size(); i++) indices.add(i);
        indices.sort((i1, i2) -> {
            String name1 = this.fNames.get(i1);
            String name2 = this.fNames.get(i2);
            boolean pinned1 = pinnedProviders.contains(name1);
            boolean pinned2 = pinnedProviders.contains(name2);

            // 置顶的排在前面
            if (pinned1 && !pinned2) return -1;
            if (!pinned1 && pinned2) return 1;

            // 都置顶或都不置顶，按自然排序
            return compareNatural(name1, name2);
        });

        List<Long> sortedIds = new ArrayList<>();
        List<String> sortedNames = new ArrayList<>();
        List<Integer> sortedSlots = new ArrayList<>();
        List<Integer> sortedCount = new ArrayList<>();

        for (int idx : indices) {
            sortedIds.add(this.fIds.get(idx));
            sortedNames.add(this.fNames.get(idx));
            sortedSlots.add(this.fTotalSlots.get(idx));
            sortedCount.add(this.fCount.get(idx));
        }

        this.fIds.clear();
        this.fIds.addAll(sortedIds);
        this.fNames.clear();
        this.fNames.addAll(sortedNames);
        this.fTotalSlots.clear();
        this.fTotalSlots.addAll(sortedSlots);
        this.fCount.clear();
        this.fCount.addAll(sortedCount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(CancelPendingPatternC2SPacket.INSTANCE);
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.entryButtons.clear();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 70;

        // 搜索框（置于条目上方）
        if (this.searchBox == null) {
            this.searchBox = new EditBox(this.font, centerX - 120, startY - 25, 240, 18, Component.translatable("extendedae_plus.screen.search"));
        } else {
            // 重新定位，保持输入值
            this.searchBox.setX(centerX - 120);
            this.searchBox.setY(startY - 25);
            this.searchBox.setWidth(240);
        }
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(text -> {
            // 只有当输入真正发生变化时，才重置页码与过滤
            if (Objects.equals(text, this.query)) return;
            this.query = text;
            this.page = 0;
            this.applyFilter();
            // 避免在回调中直接重建 UI，延迟到下一次 tick
            this.needsRefresh = true;
        });
        this.addRenderableWidget(this.searchBox);

        int start = this.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, this.fIds.size());

        int buttonWidth = 240;
        int buttonHeight = 20;
        int gap = 5;

        for (int i = start; i < end; i++) {
            int idx = i;
            String label = this.buildLabel(idx);
            Button btn = Button.builder(Component.literal(label), b -> this.onChoose(idx))
                    .bounds(centerX - buttonWidth / 2, startY + (i - start) * (buttonHeight + gap), buttonWidth, buttonHeight)
                    .build();
            this.entryButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        // 分页按钮
        int navY = startY + PAGE_SIZE * (buttonHeight + gap) + 10;
        Button prev = Button.builder(Component.literal("<"), b -> this.changePage(-1))
                .bounds(centerX - 60, navY, 20, 20)
                .build();
        Button next = Button.builder(Component.literal(">"), b -> this.changePage(1))
                .bounds(centerX + 40, navY, 20, 20)
                .build();
        prev.active = this.page > 0;
        next.active = (this.page + 1) * PAGE_SIZE < this.fIds.size();
        this.addRenderableWidget(prev);
        this.addRenderableWidget(next);

        // 重载映射按钮（热重载 recipe_type_names.json）——移至下一行，与关闭按钮并排
        Button reload = Button.builder(Component.translatable("extendedae_plus.screen.reload_mapping"), b -> this.reloadMapping())
                .bounds(centerX - 130, navY + 30, 80, 20)
                .build();
        this.addRenderableWidget(reload);

        // 中文名输入框（用于新增映射的值）
        if (this.cnInput == null) {
            this.cnInput = new EditBox(this.font, centerX + 50, navY + 30, 120, 20, Component.translatable("extendedae_plus.screen.cn_name"));
        } else {
            this.cnInput.setX(centerX + 50);
            this.cnInput.setY(navY + 30);
            this.cnInput.setWidth(120);
        }
        this.addRenderableWidget(this.cnInput);

        // 增加映射按钮（使用当前搜索关键字 -> 中文）
        Button addMap = Button.builder(Component.translatable("extendedae_plus.screen.add_mapping"), b -> this.addMappingFromUI())
                .bounds(centerX + 175, navY + 30, 60, 20)
                .build();
        this.addRenderableWidget(addMap);

        // 删除映射（按中文值精确匹配删除）按钮
        Button delByCn = Button.builder(Component.translatable("extendedae_plus.screen.remove_mapping"), b -> this.deleteMappingByCnFromUI())
                .bounds(centerX + 240, navY + 30, 60, 20)
                .build();
        this.addRenderableWidget(delByCn);

        // 关闭按钮
        Button close = Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(centerX - 40, navY + 30, 80, 20)
                .build();
        this.addRenderableWidget(close);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.needsRefresh) {
            this.needsRefresh = false;
            // 重新构建当前屏幕内容
            this.init();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
                this.applyFilter();
                this.needsRefresh = true;
                return true;
            }
        }

        // 右键点击条目按钮时，切换置顶状态
        if (button == 1) {
            for (int i = 0; i < this.entryButtons.size(); i++) {
                Button btn = this.entryButtons.get(i);
                if (btn.visible && btn.active) {
                    int x = btn.getX();
                    int y = btn.getY();
                    int w = btn.getWidth();
                    int h = btn.getHeight();
                    if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                        // 计算实际索引
                        int start = this.page * PAGE_SIZE;
                        int actualIdx = start + i;
                        if (actualIdx >= 0 && actualIdx < this.fNames.size()) {
                            togglePin(actualIdx);
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void addMappingFromUI() {
        String key = this.query == null ? "" : this.query.trim();
        String val = this.cnInput == null ? "" : this.cnInput.getValue().trim();
        var player = Minecraft.getInstance().player;
        if (key.isEmpty()) {
            if (player != null) player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.search_required"));
            return;
        }
        if (val.isEmpty()) {
            if (player != null) player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.cn_required"));
            return;
        }
        boolean ok = ExtendedAEPatternUploadUtil.addOrUpdateAliasMapping(key, val);
        if (ok) {
            if (player != null) player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.add_success", key, val));
            // 将刚添加的中文名写入搜索框，作为当前查询
            this.query = val;
            if (this.searchBox != null) {
                this.searchBox.setValue(val);
            }
            // 更新本地过滤显示（若名称包含中文可被搜索）
            this.applyFilter();
            // 回到第一页以展示最新筛选结果
            this.page = 0;
            this.needsRefresh = true;
        } else {
            if (player != null) player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.add_fail"));
        }
    }

    // 使用中文值精确匹配删除映射
    private void deleteMappingByCnFromUI() {
        String val = this.cnInput == null ? "" : this.cnInput.getValue().trim();
        var player = Minecraft.getInstance().player;
        if (val.isEmpty()) {
            if (player != null) player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.delete_cn_required"));
            return;
        }
        int removed = ExtendedAEPatternUploadUtil.removeMappingsByCnValue(val);
        if (removed > 0) {
            if (player != null) player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.delete_success", removed, val));
            this.applyFilter();
            this.needsRefresh = true;
        } else {
            if (player != null) player.sendSystemMessage(Component.translatable("extendedae_plus.message.mapping.delete_not_found", val));
        }
    }

    // 切换供应器的置顶状态
    private void togglePin(int idx) {
        if (idx < 0 || idx >= this.fNames.size()) return;
        String name = this.fNames.get(idx);

        if (pinnedProviders.contains(name)) {
            pinnedProviders.remove(name);
        } else {
            pinnedProviders.add(name);
        }

        // 保存到配置文件
        savePinnedProviders();

        // 重新应用过滤和排序
        this.applyFilter();
        this.needsRefresh = true;
    }

    /**
     * 从配置文件加载置顶的供应器名称列表
     */
    private static synchronized void loadPinnedProviders() {
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(PINNED_CONFIG_PATH);
            if (!Files.exists(cfgPath)) {
                return; // 文件不存在时不做处理
            }

            String json = Files.readString(cfgPath);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return;

            JsonElement pinnedElement = obj.get("pinned");
            if (pinnedElement != null && pinnedElement.isJsonArray()) {
                JsonArray arr = pinnedElement.getAsJsonArray();
                pinnedProviders.clear();
                for (JsonElement elem : arr) {
                    if (elem.isJsonPrimitive()) {
                        String name = elem.getAsString();
                        if (name != null && !name.isBlank()) {
                            pinnedProviders.add(name);
                        }
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            // 加载失败时静默处理
        }
    }

    /**
     * 保存置顶的供应器名称列表到配置文件
     */
    private static synchronized void savePinnedProviders() {
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve(PINNED_CONFIG_PATH);
            Files.createDirectories(cfgPath.getParent());

            JsonObject obj = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String name : pinnedProviders) {
                arr.add(name);
            }
            obj.add("pinned", arr);

            Files.writeString(cfgPath, GSON.toJson(obj));
        } catch (IOException e) {
            // 保存失败时静默处理
        }
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

    private static class Group {
        long bestId = Long.MIN_VALUE;
        int bestSlots = Integer.MIN_VALUE;
        int totalSlots = 0;
        int count = 0;
    }
}
