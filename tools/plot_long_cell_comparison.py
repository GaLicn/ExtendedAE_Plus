"""生成吞噬盘与 Omni long 盘的独立中文对比图片。"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.font_manager as font_manager
import matplotlib.pyplot as plt
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "build" / "reports" / "infinity-storage-cell-comparison.md"
OUTPUT = ROOT / "build" / "reports" / "storage-benchmark-visuals" / "long-cell-comparison"


@dataclass(frozen=True)
class LongRow:
    scenario: str
    optimized_seconds: float
    legacy_seconds: float
    omni_seconds: float
    optimized_ratio: float
    legacy_ratio: float
    optimized_legacy_ratio: float


def split_markdown_row(line: str) -> list[str]:
    content = line.strip()
    if content.startswith("|"):
        content = content[1:]
    if content.endswith("|"):
        content = content[:-1]
    return [part.strip().replace("\\|", "|") for part in content.split("|")]


def is_separator_row(line: str) -> bool:
    cells = split_markdown_row(line)
    return bool(cells) and all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells)


def markdown_tables(path: Path) -> list[tuple[list[str], list[list[str]]]]:
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    tables: list[tuple[list[str], list[list[str]]]] = []
    index = 0
    while index < len(lines) - 1:
        if not lines[index].lstrip().startswith("|") or not is_separator_row(lines[index + 1]):
            index += 1
            continue

        header = split_markdown_row(lines[index])
        rows: list[list[str]] = []
        index += 2
        while index < len(lines) and lines[index].lstrip().startswith("|"):
            row = split_markdown_row(lines[index])
            if len(row) == len(header):
                rows.append(row)
            index += 1
        tables.append((header, rows))
    return tables


def parse_duration(value: str) -> float:
    match = re.fullmatch(r"\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*(us|ms|s)\s*", value)
    if not match:
        raise ValueError(f"无法解析耗时: {value!r}")
    amount = float(match.group(1).replace(",", ""))
    return amount * {"us": 1e-6, "ms": 1e-3, "s": 1}[match.group(2)]


def parse_ratio(value: str) -> float:
    return float(value.strip().removesuffix("x"))


def parse_long_rows(path: Path) -> list[LongRow]:
    for header, rows in markdown_tables(path):
        if "Omni long 平均值" not in header:
            continue
        positions = {name: index for index, name in enumerate(header)}
        required = [
            "测试场景",
            "优化后吞噬盘平均值",
            "未优化吞噬盘平均值",
            "Omni long 平均值",
            "优化后/Omni long",
            "未优化/Omni long",
            "优化后/未优化",
        ]
        missing = [name for name in required if name not in positions]
        if missing:
            raise ValueError(f"long 表缺少列: {missing}")

        return [
            LongRow(
                scenario=row[positions["测试场景"]],
                optimized_seconds=parse_duration(row[positions["优化后吞噬盘平均值"]]),
                legacy_seconds=parse_duration(row[positions["未优化吞噬盘平均值"]]),
                omni_seconds=parse_duration(row[positions["Omni long 平均值"]]),
                optimized_ratio=parse_ratio(row[positions["优化后/Omni long"]]),
                legacy_ratio=parse_ratio(row[positions["未优化/Omni long"]]),
                optimized_legacy_ratio=parse_ratio(row[positions["优化后/未优化"]]),
            )
            for row in rows
        ]
    raise ValueError(f"找不到 long 数量级结果表: {path}")


def choose_font() -> tuple[font_manager.FontProperties, font_manager.FontProperties]:
    candidates = [
        Path(r"C:\Windows\Fonts\msyh.ttc"),
        Path(r"C:\Windows\Fonts\simhei.ttf"),
        Path(r"C:\Windows\Fonts\Deng.ttf"),
        Path(r"C:\Windows\Fonts\simsun.ttc"),
    ]
    font_path = next((path for path in candidates if path.exists()), None)
    if font_path:
        regular = font_manager.FontProperties(fname=str(font_path))
        bold = font_manager.FontProperties(fname=str(font_path), weight="bold")
    else:
        regular = font_manager.FontProperties(family="DejaVu Sans")
        bold = font_manager.FontProperties(family="DejaVu Sans", weight="bold")
    plt.rcParams["font.family"] = regular.get_name()
    plt.rcParams["axes.unicode_minus"] = False
    return regular, bold


FONT, FONT_BOLD = choose_font()


def scenario_label(value: str) -> str:
    mapping = {
        "输入 SIMULATE（long 数量）": "输入模拟\nSIMULATE",
        "输入 MODULATE（long 数量）": "输入真实\nMODULATE",
        "输出 SIMULATE（long 数量）": "输出模拟\nSIMULATE",
        "输出 MODULATE（long 数量）": "输出真实\nMODULATE",
        "AE 终端读取（long 数量）": "AE 终端读取",
    }
    return mapping.get(value, value)


def short_duration(seconds: float) -> str:
    micros = seconds * 1_000_000
    if micros < 1_000:
        return f"{micros:.2f} μs"
    return f"{micros / 1_000:.2f} ms"


def style_table(table, font_size: float = 9.5) -> None:
    table.auto_set_font_size(False)
    for (row_index, column_index), cell in table.get_celld().items():
        cell.set_edgecolor("#C9D3DD")
        cell.set_linewidth(0.75)
        cell.set_text_props(fontproperties=FONT, fontsize=font_size, color="#202124")
        if row_index == 0:
            cell.set_facecolor("#1F4E79")
            cell.set_text_props(fontproperties=FONT_BOLD, fontsize=font_size, color="white")
        elif row_index % 2 == 0:
            cell.set_facecolor("#F3F7FA")
        else:
            cell.set_facecolor("white")


def table_rows(rows: list[LongRow]) -> list[list[str]]:
    return [
        [
            scenario_label(row.scenario),
            short_duration(row.optimized_seconds),
            short_duration(row.legacy_seconds),
            short_duration(row.omni_seconds),
            f"{row.optimized_ratio:.2f}x",
            f"{row.legacy_ratio:.2f}x",
            f"{row.optimized_legacy_ratio:.2f}x",
        ]
        for row in rows
    ]


def save_table(path: Path, rows: list[LongRow]) -> None:
    figure, axis = plt.subplots(figsize=(22, 6.6), dpi=220)
    axis.axis("off")
    axis.set_title(
        "long 数量级：吞噬盘与 Omni long 盘对比表",
        fontproperties=FONT_BOLD,
        fontsize=18,
        color="#17365D",
        pad=18,
    )
    columns = [
        "测试用例",
        "优化后吞噬盘\n平均耗时",
        "未优化吞噬盘\n平均耗时",
        "Omni long 盘\n平均耗时",
        "优化后吞噬盘是\nlong 盘的倍数",
        "未优化吞噬盘是\nlong 盘的倍数",
        "优化后 /\n未优化",
    ]
    table = axis.table(
        cellText=[columns] + table_rows(rows),
        cellLoc="center",
        colWidths=[0.19, 0.13, 0.13, 0.13, 0.16, 0.16, 0.10],
        bbox=[0.015, 0.16, 0.97, 0.70],
    )
    style_table(table, 9.5)
    axis.text(
        0.015,
        0.055,
        "倍率 = Omni long 盘平均耗时 ÷ 吞噬盘平均耗时；>1 表示吞噬盘更快，<1 表示 long 盘更快。",
        transform=axis.transAxes,
        fontproperties=FONT,
        fontsize=9.5,
        color="#465A6E",
    )
    figure.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(figure)


def save_ratio_chart(path: Path, rows: list[LongRow]) -> None:
    labels = [scenario_label(row.scenario) for row in rows]
    optimized = [row.optimized_ratio for row in rows]
    legacy = [row.legacy_ratio for row in rows]
    x = np.arange(len(rows))
    width = 0.34

    figure, axis = plt.subplots(figsize=(16, 8.5), dpi=220)
    first = axis.bar(x - width / 2, optimized, width, color="#1769AA", label="优化后吞噬盘")
    second = axis.bar(x + width / 2, legacy, width, color="#A33F1F", label="未优化吞噬盘")
    axis.axhline(1, color="#333333", linewidth=1.3, linestyle="--", label="1.00x：与 long 盘相同")
    axis.set_xticks(x, labels)
    axis.set_ylabel("吞噬盘是 Omni long 盘的性能倍数", fontproperties=FONT, fontsize=11)
    axis.set_title(
        "long 数量级性能倍率（>1 表示吞噬盘更快）",
        fontproperties=FONT_BOLD,
        fontsize=17,
        color="#17365D",
    )
    axis.grid(axis="y", alpha=0.25)
    axis.legend(prop=FONT, loc="upper right")
    axis.set_ylim(bottom=0)
    for tick in axis.get_xticklabels():
        tick.set_fontproperties(FONT)
        tick.set_fontsize(10)
    for tick in axis.get_yticklabels():
        tick.set_fontproperties(FONT)
    offset = max(max(optimized + legacy) * 0.025, 0.08)
    for position, value in zip(x - width / 2, optimized):
        axis.text(position, value + offset, f"{value:.2f}x", ha="center", fontproperties=FONT, fontsize=10)
    for position, value in zip(x + width / 2, legacy):
        axis.text(position, value + offset, f"{value:.2f}x", ha="center", fontproperties=FONT, fontsize=10)
    figure.text(
        0.5,
        0.015,
        "数据集：4,096 个普通 key + 4,096 个 NBT key，共 8,192 key；每 key 数量 = Long.MAX_VALUE / 8,192 = 1,125,899,906,842,623",
        ha="center",
        fontproperties=FONT,
        fontsize=9.5,
        color="#465A6E",
    )
    figure.tight_layout(rect=[0, 0.045, 1, 1])
    figure.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(figure)


def save_latency_chart(path: Path, rows: list[LongRow]) -> None:
    labels = [scenario_label(row.scenario) for row in rows]
    x = np.arange(len(rows))
    figure, axis = plt.subplots(figsize=(16, 8.5), dpi=220)
    series = [
        ([row.optimized_seconds * 1000 for row in rows], "#1769AA", "优化后吞噬盘"),
        ([row.legacy_seconds * 1000 for row in rows], "#A33F1F", "未优化吞噬盘"),
        ([row.omni_seconds * 1000 for row in rows], "#2A9D8F", "Omni long 盘"),
    ]
    for values, color, label in series:
        axis.plot(x, values, marker="o", linewidth=2.4, markersize=7, color=color, label=label)
    axis.set_yscale("log")
    axis.set_xticks(x, labels)
    axis.set_ylabel("平均耗时（ms，对数轴）", fontproperties=FONT, fontsize=11)
    axis.set_title(
        "long 数量级平均耗时折线图",
        fontproperties=FONT_BOLD,
        fontsize=17,
        color="#17365D",
    )
    axis.grid(axis="y", which="both", alpha=0.25)
    axis.legend(prop=FONT, loc="upper right")
    for tick in axis.get_xticklabels():
        tick.set_fontproperties(FONT)
        tick.set_fontsize(10)
    for tick in axis.get_yticklabels():
        tick.set_fontproperties(FONT)
    figure.text(
        0.5,
        0.015,
        "同一批 8,192 key；2 轮预热、10 轮正式测量，图中为算术平均值",
        ha="center",
        fontproperties=FONT,
        fontsize=9.5,
        color="#465A6E",
    )
    figure.tight_layout(rect=[0, 0.045, 1, 1])
    figure.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(figure)


def save_combined(path: Path, rows: list[LongRow]) -> None:
    labels = [scenario_label(row.scenario) for row in rows]
    x = np.arange(len(rows))
    width = 0.34
    figure = plt.figure(figsize=(24, 16), dpi=180)
    grid = figure.add_gridspec(2, 1, height_ratios=[0.9, 1.25], hspace=0.25)

    table_axis = figure.add_subplot(grid[0])
    table_axis.axis("off")
    table_axis.text(
        0.5,
        0.98,
        "long 数量级：吞噬盘 vs Omni long 盘独立对比报告",
        ha="center",
        va="top",
        fontproperties=FONT_BOLD,
        fontsize=22,
        color="#17365D",
    )
    table_axis.text(
        0.5,
        0.87,
        "仅包含：优化后吞噬盘、未优化吞噬盘、Omni CREATIVE_AE_CELL_LONG；不包含 Omni BigInteger 盘",
        ha="center",
        fontproperties=FONT,
        fontsize=11,
        color="#465A6E",
    )
    table_axis.text(
        0.5,
        0.81,
        "数据集 8,192 key（4,096 普通 + 4,096 NBT）｜每 key = 1,125,899,906,842,623｜2 轮预热 + 10 轮测量",
        ha="center",
        fontproperties=FONT,
        fontsize=10.5,
        color="#465A6E",
    )
    columns = [
        "测试用例",
        "优化后吞噬盘\n耗时",
        "未优化吞噬盘\n耗时",
        "Omni long 盘\n耗时",
        "优化后吞噬盘\n是 long 盘倍数",
        "未优化吞噬盘\n是 long 盘倍数",
    ]
    compact_rows = [
        [
            scenario_label(row.scenario),
            short_duration(row.optimized_seconds),
            short_duration(row.legacy_seconds),
            short_duration(row.omni_seconds),
            f"{row.optimized_ratio:.2f}x",
            f"{row.legacy_ratio:.2f}x",
        ]
        for row in rows
    ]
    table = table_axis.table(
        cellText=[columns] + compact_rows,
        cellLoc="center",
        colWidths=[0.20, 0.14, 0.14, 0.14, 0.19, 0.19],
        bbox=[0.03, 0.10, 0.94, 0.62],
    )
    style_table(table, 10.5)

    charts = grid[1].subgridspec(1, 2, wspace=0.20)
    ratio_axis = figure.add_subplot(charts[0])
    optimized = [row.optimized_ratio for row in rows]
    legacy = [row.legacy_ratio for row in rows]
    ratio_axis.bar(x - width / 2, optimized, width, color="#1769AA", label="优化后吞噬盘")
    ratio_axis.bar(x + width / 2, legacy, width, color="#A33F1F", label="未优化吞噬盘")
    ratio_axis.axhline(1, color="#333333", linewidth=1.2, linestyle="--")
    ratio_axis.set_xticks(x, labels)
    ratio_axis.set_ylabel("吞噬盘是 long 盘的性能倍数", fontproperties=FONT)
    ratio_axis.set_title("性能倍率", fontproperties=FONT_BOLD, fontsize=15)
    ratio_axis.grid(axis="y", alpha=0.25)
    ratio_axis.legend(prop=FONT, loc="upper right")
    ratio_axis.set_ylim(bottom=0)

    latency_axis = figure.add_subplot(charts[1])
    for values, color, label in [
        ([row.optimized_seconds * 1000 for row in rows], "#1769AA", "优化后吞噬盘"),
        ([row.legacy_seconds * 1000 for row in rows], "#A33F1F", "未优化吞噬盘"),
        ([row.omni_seconds * 1000 for row in rows], "#2A9D8F", "Omni long 盘"),
    ]:
        latency_axis.plot(x, values, marker="o", linewidth=2.2, markersize=6, color=color, label=label)
    latency_axis.set_yscale("log")
    latency_axis.set_xticks(x, labels)
    latency_axis.set_ylabel("平均耗时（ms，对数轴）", fontproperties=FONT)
    latency_axis.set_title("平均耗时", fontproperties=FONT_BOLD, fontsize=15)
    latency_axis.grid(axis="y", which="both", alpha=0.25)
    latency_axis.legend(prop=FONT, loc="upper right")

    for axis in (ratio_axis, latency_axis):
        for tick in axis.get_xticklabels():
            tick.set_fontproperties(FONT)
            tick.set_fontsize(9)
        for tick in axis.get_yticklabels():
            tick.set_fontproperties(FONT)
    figure.text(
        0.5,
        0.025,
        "倍率 = Omni long 盘耗时 ÷ 吞噬盘耗时；>1 表示吞噬盘更快，<1 表示 Omni long 盘更快",
        ha="center",
        fontproperties=FONT,
        fontsize=11,
        color="#465A6E",
    )
    figure.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(figure)


def main() -> None:
    rows = parse_long_rows(SOURCE)
    if len(rows) != 5:
        raise ValueError(f"预期 5 个 long 用例，实际得到 {len(rows)} 个")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    save_table(OUTPUT / "long-cell-comparison-table.png", rows)
    save_ratio_chart(OUTPUT / "long-cell-comparison-ratio.png", rows)
    save_latency_chart(OUTPUT / "long-cell-comparison-latency.png", rows)
    save_combined(OUTPUT / "long-cell-comparison-report.png", rows)
    print(f"已生成 long 独立对比图片: {OUTPUT.resolve()}")
    for path in sorted(OUTPUT.glob("*.png")):
        print(f"{path.name}: {path.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
