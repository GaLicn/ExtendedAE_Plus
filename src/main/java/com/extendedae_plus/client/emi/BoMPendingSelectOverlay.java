package com.extendedae_plus.client.emi;

import com.extendedae_plus.client.screen.ProviderSelectScreen;
import com.extendedae_plus.network.BatchPendingActionC2SPacket;
import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import com.extendedae_plus.util.uploadPattern.BatchPatternUploadUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合成树界面里的迷你机器选择区。
 * <p>
 * 一键编码时映射没有唯一命中的样板由服务端排队，本面板直接画在配方树界面上，
 * 让玩家不切屏就能给当前这份样板点一台机器；点完服务端自动送来下一项。
 * 候选多、需要搜索或翻页时可以「更多…」切到完整的
 * {@link ProviderSelectScreen}（同一份列表、同一项待选）。
 * <p>
 * 本类刻意不引用任何 EMI 类型：宿主界面只按 {@link Screen} 记，
 * 这样没装 EMI 时加载到它也不会因为缺类炸掉。宿主由 BoMScreen 的渲染注入每帧登记。
 */
public final class BoMPendingSelectOverlay {
	/** 面板宽度按底部三个按钮（更多/跳过/全部放弃）加翻页箭头的最坏情况定，英文标签也放得下。 */
	private static final int PANEL_W = 176;
	private static final int PAD = 4;
	private static final int ROW_H = 13;
	private static final int ROWS_PER_PAGE = 5;
	private static final int FOOTER_H = 13;
	/** 材料图标一行最多画几个，多出来的折成「+N」。 */
	private static final int MAX_INPUT_ICONS = 9;

	private static final int COLOR_BG = 0xF0181818;
	/** 与合成树树枝同一个纯白（EMI 的 {@code drawLine} 用 0xFFFFFFFF），面板贴在树上不显得是外来物。 */
	private static final int COLOR_BORDER = 0xFFFFFFFF;
	private static final int COLOR_ROW_HOVER = 0x55FFFFFF;
	private static final int COLOR_TITLE = 0xFFFFAA00;
	private static final int COLOR_TEXT = 0xFFE0E0E0;
	private static final int COLOR_DIM = 0xFFA0A0A0;

	/** 当前正在渲染配方树的界面，由渲染注入每帧登记；只用来判断面板还有没有宿主。 */
	private static Screen host;

	private static ProvidersListS2CPacket.PendingSelection pending;
	private static List<Long> rawIds = List.of();
	private static List<Component> rawNames = List.of();
	private static List<Integer> rawSlots = List.of();
	/** 同名合并后的候选行，按面板打开时的列表算一次。 */
	private static final List<Row> ROWS = new ArrayList<>();
	/** 配方材料图标，打开时按配方 ID 解析一次；解析不出来（跨模组配方常见）就只显示产物。 */
	private static final List<ItemStack> INPUT_ICONS = new ArrayList<>();
	private static int hiddenInputs;
	private static int page;

	// 上一帧的几何与命中区：渲染必然发生在同一帧的点击处理之前。
	private static Rect panelRect = Rect.EMPTY;
	private static final List<Rect> ROW_RECTS = new ArrayList<>();
	private static Rect skipRect = Rect.EMPTY;
	private static Rect abortRect = Rect.EMPTY;
	private static Rect moreRect = Rect.EMPTY;
	private static Rect prevRect = Rect.EMPTY;
	private static Rect nextRect = Rect.EMPTY;

	private record Row(long id, String name, int totalSlots, int count) {}

	private record Rect(int x, int y, int w, int h) {
		private static final Rect EMPTY = new Rect(0, 0, 0, 0);

		boolean contains(double px, double py) {
			return this.w > 0 && this.h > 0
				&& px >= this.x && px < this.x + this.w
				&& py >= this.y && py < this.y + this.h;
		}
	}

	private BoMPendingSelectOverlay() {}

	/** 配方树界面每帧登记自己，作为「面板能不能挂在这儿」与「宿主是否还在」的依据。 */
	public static void noteHost(Screen screen) {
		host = screen;
	}

	/** 该界面是否就是刚刚渲染过配方树的那个界面。 */
	public static boolean canHost(Screen screen) {
		return screen != null && screen == host;
	}

	public static boolean isOpen() {
		return pending != null;
	}

	/**
	 * 接管一项待选：把服务端下发的列表按同名合并，只留搜索词命中的候选。
	 * 映射一台都没命中时不筛，直接列出全部机器让玩家自己挑。
	 */
	public static void open(ProvidersListS2CPacket.PendingSelection selection,
	                        List<Long> ids, List<Component> names, List<Integer> slots) {
		pending = selection;
		rawIds = ids == null ? List.of() : ids;
		rawNames = names == null ? List.of() : names;
		rawSlots = slots == null ? List.of() : slots;
		page = 0;
		buildRows(selection == null ? "" : selection.presetQuery());
		buildInputIcons(selection == null ? null : selection.recipeId());
	}

	/** 只清面板状态，不通知服务端：切到完整界面时用。 */
	public static void close() {
		pending = null;
		rawIds = List.of();
		rawNames = List.of();
		rawSlots = List.of();
		ROWS.clear();
		INPUT_ICONS.clear();
		hiddenInputs = 0;
		ROW_RECTS.clear();
		panelRect = Rect.EMPTY;
		page = 0;
	}

	/** 面板占住的屏幕区域，宿主界面用它让出鼠标（悬停、点击、滚轮、拖动都不该穿透）。 */
	public static boolean contains(double mouseX, double mouseY) {
		return isOpen() && panelRect.contains(mouseX, mouseY);
	}

	/**
	 * 宿主界面没了就放弃整条队列：面板只在配方树上画，界面一走玩家再也点不到，
	 * 样板留在服务端队列里等于被吞掉（编码时已经扣过空白样板）。
	 * 切去完整选择界面走的是 {@link #close()}，那时面板已经不算打开，不会误触发。
	 */
	public static void hostChanged(Screen next) {
		if (isOpen() && next != host) {
			abortQueue();
		}
	}

	public static void hostClosing(Screen closing) {
		if (isOpen() && closing == host) {
			abortQueue();
		}
	}

	private static void abortQueue() {
		PacketDistributor.sendToServer(new BatchPendingActionC2SPacket(BatchPendingActionC2SPacket.Action.ABORT));
		close();
	}

	/** 屏幕坐标系内绘制（宿主的缩放矩阵之外），面板固定贴在左侧居中，不随树缩放平移。 */
	public static void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
		if (!isOpen()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int pageRows = Math.min(ROWS_PER_PAGE, Math.max(1, ROWS.size() - page * ROWS_PER_PAGE));
		int inputsH = INPUT_ICONS.isEmpty() ? 0 : 18;
		int headerH = 12 + inputsH + 10;
		int panelH = PAD + headerH + pageRows * ROW_H + 2 + FOOTER_H + PAD;
		int x = 8;
		int y = Math.max(4, (screenH - panelH) / 2);
		panelRect = new Rect(x, y, PANEL_W, panelH);

		graphics.fill(x, y, x + PANEL_W, y + panelH, COLOR_BG);
		graphics.renderOutline(x, y, PANEL_W, panelH, COLOR_BORDER);

		int textX = x + PAD;
		int cursorY = y + PAD;

		// 抬头：产物图标 + 名字 + 进度，玩家得知道正在给哪份样板选机器。
		ItemStack output = pending.output();
		if (!output.isEmpty()) {
			graphics.renderFakeItem(output, textX, cursorY - 1);
		}
		Component title = output.isEmpty()
			? Component.translatable("extendedae_plus.screen.pending_unknown_output")
			: output.getHoverName();
		int titleX = output.isEmpty() ? textX : textX + 18;
		graphics.drawString(font,
			font.plainSubstrByWidth(title.getString(), PANEL_W - PAD * 2 - (titleX - textX) - 30),
			titleX, cursorY + 3, COLOR_TEXT, false);
		String progress = pending.index() + "/" + pending.total();
		graphics.drawString(font, progress, x + PANEL_W - PAD - font.width(progress), cursorY + 3, COLOR_TITLE, false);
		cursorY += 12;

		// 材料图标：只是让玩家一眼认出这是哪个配方，点错机器比点错配方更难察觉。
		if (!INPUT_ICONS.isEmpty()) {
			int iconX = textX;
			for (ItemStack input : INPUT_ICONS) {
				graphics.renderFakeItem(input, iconX, cursorY);
				iconX += 17;
			}
			if (hiddenInputs > 0) {
				graphics.drawString(font, "+" + hiddenInputs, iconX + 1, cursorY + 5, COLOR_DIM, false);
			}
			cursorY += 18;
		}

		// 一行标题就够：为什么落到手动选择（映射命中几台）在完整界面里说明，
		// 面板上占两行反而把候选列表挤没了。
		graphics.drawString(font, Component.translatable("extendedae_plus.screen.pending_select_provider"),
			textX, cursorY + 2, COLOR_DIM, false);
		cursorY += 10;

		renderRows(graphics, font, x, cursorY, pageRows, mouseX, mouseY);
		renderFooter(graphics, font, x, y + panelH - PAD - FOOTER_H, mouseX, mouseY);
	}

	private static void renderRows(GuiGraphics graphics, Font font, int panelX, int top,
	                               int pageRows, int mouseX, int mouseY) {
		ROW_RECTS.clear();
		int rowX = panelX + PAD;
		int rowW = PANEL_W - PAD * 2;
		if (ROWS.isEmpty()) {
			// 服务端只在网络里有可用供应器时才排队，走到这儿基本是搜索词把列表筛空了。
			graphics.drawString(font, Component.translatable("extendedae_plus.screen.pending_no_candidate"),
				rowX, top + 3, COLOR_DIM, false);
			return;
		}

		int start = page * ROWS_PER_PAGE;
		for (int i = 0; i < pageRows && start + i < ROWS.size(); i++) {
			Row row = ROWS.get(start + i);
			int rowY = top + i * ROW_H;
			Rect rect = new Rect(rowX, rowY, rowW, ROW_H - 1);
			ROW_RECTS.add(rect);
			if (rect.contains(mouseX, mouseY)) {
				graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), COLOR_ROW_HOVER);
			}
			// 空位与台数右对齐，名字按剩余宽度截断：长机器名不该把这两个数字挤出面板。
			String stats = "(" + row.totalSlots() + ")" + (row.count() > 1 ? " x" + row.count() : "");
			int statsW = font.width(stats);
			graphics.drawString(font, stats, rowX + rowW - statsW - 2, rowY + 3, COLOR_DIM, false);
			graphics.drawString(font, font.plainSubstrByWidth(row.name(), rowW - statsW - 8),
				rowX + 2, rowY + 3, COLOR_TEXT, false);
		}
	}

	private static void renderFooter(GuiGraphics graphics, Font font, int panelX, int top,
	                                int mouseX, int mouseY) {
		int rowX = panelX + PAD;
		int rowW = PANEL_W - PAD * 2;
		graphics.fill(rowX, top - 2, rowX + rowW, top - 1, COLOR_BORDER);

		int pages = Math.max(1, (ROWS.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		if (pages > 1) {
			prevRect = new Rect(rowX, top, 10, FOOTER_H);
			nextRect = new Rect(rowX + 12, top, 10, FOOTER_H);
			drawButton(graphics, font, prevRect, Component.literal("<"), page > 0, mouseX, mouseY);
			drawButton(graphics, font, nextRect, Component.literal(">"), page + 1 < pages, mouseX, mouseY);
		} else {
			prevRect = Rect.EMPTY;
			nextRect = Rect.EMPTY;
		}

		int actionsRight = panelX + PANEL_W - PAD;
		Component moreLabel = Component.translatable("extendedae_plus.screen.pending_more");
		Component skipLabel = Component.translatable("extendedae_plus.screen.pending_skip");
		Component abortLabel = Component.translatable("extendedae_plus.screen.pending_abort");
		int moreW = font.width(moreLabel) + 6;
		int skipW = font.width(skipLabel) + 6;
		int abortW = font.width(abortLabel) + 6;

		abortRect = new Rect(actionsRight - abortW, top, abortW, FOOTER_H);
		skipRect = new Rect(abortRect.x() - 3 - skipW, top, skipW, FOOTER_H);
		moreRect = new Rect(skipRect.x() - 3 - moreW, top, moreW, FOOTER_H);
		drawButton(graphics, font, moreRect, moreLabel, true, mouseX, mouseY);
		drawButton(graphics, font, skipRect, skipLabel, true, mouseX, mouseY);
		drawButton(graphics, font, abortRect, abortLabel, true, mouseX, mouseY);
	}

	private static void drawButton(GuiGraphics graphics, Font font, Rect rect, Component label,
	                               boolean enabled, int mouseX, int mouseY) {
		boolean hovered = enabled && rect.contains(mouseX, mouseY);
		if (hovered) {
			graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), COLOR_ROW_HOVER);
		}
		graphics.drawString(font, label, rect.x() + 3, rect.y() + 3,
			enabled ? COLOR_TEXT : COLOR_DIM, false);
	}

	/**
	 * 面板内的点击。返回 true 表示已消费，宿主界面不要再按树坐标解释这次点击
	 * （面板压在树上，穿透过去会顺手展开节点或跳去别的配方）。
	 */
	public static boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isOpen() || !panelRect.contains(mouseX, mouseY)) {
			return false;
		}
		if (button != 0) {
			// 右键等一律吃掉：面板区域内不该有任何操作漏到树上。
			return true;
		}

		for (int i = 0; i < ROW_RECTS.size(); i++) {
			if (!ROW_RECTS.get(i).contains(mouseX, mouseY)) {
				continue;
			}
			int idx = page * ROWS_PER_PAGE + i;
			if (idx >= 0 && idx < ROWS.size()) {
				pick(ROWS.get(idx));
			}
			return true;
		}

		if (prevRect.contains(mouseX, mouseY)) {
			changePage(-1);
			return true;
		}
		if (nextRect.contains(mouseX, mouseY)) {
			changePage(1);
			return true;
		}
		if (moreRect.contains(mouseX, mouseY)) {
			openFullScreen();
			return true;
		}
		if (skipRect.contains(mouseX, mouseY)) {
			PacketDistributor.sendToServer(new BatchPendingActionC2SPacket(BatchPendingActionC2SPacket.Action.SKIP));
			close();
			return true;
		}
		if (abortRect.contains(mouseX, mouseY)) {
			abortQueue();
			return true;
		}
		return true;
	}

	/** 面板上滚轮翻页，同时挡住宿主的缩放：在候选列表上滚不该把整棵树缩掉。 */
	public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!isOpen() || !panelRect.contains(mouseX, mouseY)) {
			return false;
		}
		if (amount != 0) {
			changePage(amount > 0 ? -1 : 1);
		}
		return true;
	}

	/** ESC：与关掉界面一样视为放弃整条队列，但不连带关掉配方树。 */
	public static boolean escapePressed() {
		if (!isOpen()) {
			return false;
		}
		abortQueue();
		return true;
	}

	private static void changePage(int delta) {
		int pages = Math.max(1, (ROWS.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		page = Math.max(0, Math.min(pages - 1, page + delta));
	}

	/**
	 * 点定一台机器：沿用单样板上传的封包（负数索引即代表 id），
	 * 服务端会把队首样板插进去并自动送来下一项。
	 */
	private static void pick(Row row) {
		PacketDistributor.sendToServer(new UploadEncodedPatternToProviderC2SPacket(row.id(), false, row.name()));
		close();
	}

	/** 切到完整选择界面处理这一项：同一份列表、同一项待选，搜索与翻页都齐全。 */
	private static void openFullScreen() {
		Minecraft mc = Minecraft.getInstance();
		var selection = pending;
		var ids = rawIds;
		var names = rawNames;
		var slots = rawSlots;
		close();
		mc.setScreen(new ProviderSelectScreen(mc.screen, ids, names, slots, selection));
	}

	/**
	 * 同名机器合并成一行：选择界面本来也这么显示，上传时同名机器互为备选。
	 * 代表 id 取空位最多的那台，和完整界面的分组口径一致。
	 */
	private static void buildRows(String query) {
		ROWS.clear();
		String key = query == null ? "" : query.trim();
		Map<String, long[]> groups = new LinkedHashMap<>();
		for (int i = 0; i < rawIds.size() && i < rawNames.size(); i++) {
			String name = rawNames.get(i).getString();
			if (!key.isEmpty() && !BatchPatternUploadUtil.providerNameMatches(name, key)) {
				continue;
			}
			int slots = i < rawSlots.size() ? Math.max(0, rawSlots.get(i)) : 0;
			long id = rawIds.get(i);
			// [代表 id, 空位总和, 台数, 代表机器的空位]
			long[] group = groups.computeIfAbsent(name, k -> new long[]{id, 0, 0, -1});
			group[1] += slots;
			group[2]++;
			if (slots > group[3]) {
				group[3] = slots;
				group[0] = id;
			}
		}
		for (Map.Entry<String, long[]> entry : groups.entrySet()) {
			long[] group = entry.getValue();
			ROWS.add(new Row(group[0], entry.getKey(), (int) group[1], (int) group[2]));
		}
		ROWS.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
	}

	/**
	 * 从客户端配方表取材料图标：只用于让玩家看清「这是哪个配方」，不参与编码。
	 * 跨模组的处理配方多半不填 {@code getIngredients()}，取不到就留空，抬头仍有产物可看。
	 */
	private static void buildInputIcons(ResourceLocation recipeId) {
		INPUT_ICONS.clear();
		hiddenInputs = 0;
		var level = Minecraft.getInstance().level;
		if (recipeId == null || level == null) {
			return;
		}
		var holder = level.getRecipeManager().byKey(recipeId);
		if (holder.isEmpty()) {
			return;
		}

		for (var ingredient : holder.get().value().getIngredients()) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}
			ItemStack[] items = ingredient.getItems();
			if (items.length == 0) {
				continue;
			}
			if (INPUT_ICONS.size() >= MAX_INPUT_ICONS) {
				hiddenInputs++;
				continue;
			}
			INPUT_ICONS.add(items[0]);
		}
	}
}
