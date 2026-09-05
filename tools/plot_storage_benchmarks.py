"""从三方 StorageCell 基准报告生成中文表格和图表。"""

from __future__ import annotations

import argparse
import html
import re
from dataclasses import dataclass
from pathlib import Path

import matplotlib.font_manager as font_manager
import matplotlib.pyplot as plt
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CELL = ROOT / "build" / "reports" / "infinity-storage-cell-comparison.md"
DEFAULT_CONCURRENCY = ROOT / "build" / "reports" / "infinity-storage-concurrency.md"
DEFAULT_OUTPUT = ROOT / "build" / "reports" / "storage-benchmark-visuals"


@dataclass(frozen=True)
class CellRow:
    group: str
    dataset: str
    scenario: str
    optimized_seconds: float
    legacy_seconds: float
    omni_seconds: float
    optimized_ratio: float
    legacy_ratio: float
    optimized_legacy_ratio: float
    optimized_throughput: int
    legacy_throughput: int
    omni_throughput: int


@dataclass(frozen=True)
class ConcurrencyRow:
    group: str
    scenario: str
    before_seconds: float
    after_seconds: float
    speedup: float
    before_throughput: int
    after_throughput: int


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


def find_table(path: Path, first_header: str) -> tuple[list[str], list[list[str]]]:
    for header, rows in markdown_tables(path):
        if header and header[0] == first_header:
            return header, rows
    raise ValueError(f"找不到 {path} 中以 {first_header!r} 开头的 Markdown 表格")


def parse_duration(value: str) -> float:
    match = re.fullmatch(r"\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*(us|ms|s)\s*", value)
    if not match:
        raise ValueError(f"无法解析耗时: {value!r}")
    amount = float(match.group(1).replace(",", ""))
    return amount * {"us": 1e-6, "ms": 1e-3, "s": 1}[match.group(2)]


def parse_number(value: str) -> int:
    return int(value.replace(",", "").strip())


def parse_ratio(value: str) -> float:
    return float(value.strip().removesuffix("x"))


def parse_cell_report(path: Path) -> list[CellRow]:
    header, rows = find_table(path, "测试分组")
    positions = {name: index for index, name in enumerate(header)}
    required = [
        "测试分组",
        "数据集",
        "测试场景",
        "优化后吞噬盘平均值",
        "未优化吞噬盘平均值",
        "Omni 平均值",
        "优化后/Omni性能",
        "未优化/Omni性能",
        "优化后/未优化性能",
        "优化后次/秒",
        "未优化次/秒",
        "Omni次/秒",
    ]
    missing = [name for name in required if name not in positions]
    if missing:
        raise ValueError(f"{path} 的三方合并表缺少列: {missing}")
    indexes = [positions[name] for name in required]
    return [
        CellRow(
            group=row[indexes[0]],
            dataset=row[indexes[1]],
            scenario=row[indexes[2]],
            optimized_seconds=parse_duration(row[indexes[3]]),
            legacy_seconds=parse_duration(row[indexes[4]]),
            omni_seconds=parse_duration(row[indexes[5]]),
            optimized_ratio=parse_ratio(row[indexes[6]]),
            legacy_ratio=parse_ratio(row[indexes[7]]),
            optimized_legacy_ratio=parse_ratio(row[indexes[8]]),
            optimized_throughput=parse_number(row[indexes[9]]),
            legacy_throughput=parse_number(row[indexes[10]]),
            omni_throughput=parse_number(row[indexes[11]]),
        )
        for row in rows
    ]


def parse_concurrency_report(path: Path) -> list[ConcurrencyRow]:
    header, rows = find_table(path, "测试分组")
    positions = {name: index for index, name in enumerate(header)}
    required = [
        "测试分组",
        "测试场景",
        "优化前平均值",
        "优化后平均值",
        "性能倍率",
        "优化前 次/秒",
        "优化后 次/秒",
    ]
    missing = [name for name in required if name not in positions]
    if missing:
        raise ValueError(f"{path} 的并发表缺少列: {missing}")
    return [
        ConcurrencyRow(
            group=row[positions["测试分组"]],
            scenario=row[positions["测试场景"]],
            before_seconds=parse_duration(row[positions["优化前平均值"]]),
            after_seconds=parse_duration(row[positions["优化后平均值"]]),
            speedup=parse_ratio(row[positions["性能倍率"]]),
            before_throughput=parse_number(row[positions["优化前 次/秒"]]),
            after_throughput=parse_number(row[positions["优化后 次/秒"]]),
        )
        for row in rows
    ]


def choose_font() -> tuple[font_manager.FontProperties, font_manager.FontProperties]:
    windows_fonts = Path(r"C:\Windows\Fonts")
    candidates = [
        windows_fonts / "simhei.ttf",
        windows_fonts / "Deng.ttf",
        windows_fonts / "STSONG.TTF",
    ]
    font_path = next((path for path in candidates if path.exists()), None)
    regular = (font_manager.FontProperties(fname=str(font_path))
               if font_path else font_manager.FontProperties(family="DejaVu Sans"))
    if regular.get_file():
        bold = font_manager.FontProperties(fname=regular.get_file(), weight="bold")
    else:
        bold = font_manager.FontProperties(family="DejaVu Sans", weight="bold")
    plt.rcParams["font.family"] = regular.get_name()
    plt.rcParams["axes.unicode_minus"] = False
    return regular, bold


FONT, FONT_BOLD = choose_font()


def compact_scenario(row: CellRow) -> str:
    if row.group == "标准数据集":
        mapping = {
            "输入 SIMULATE": "输入模拟",
            "输入 MODULATE": "输入真实",
            "输出 SIMULATE": "输出模拟",
            "输出 MODULATE": "输出真实",
            "AE 终端读取": "终端读取",
        }
        return mapping.get(row.scenario, row.scenario)
    if row.group == "百万 key":
        return row.scenario.replace("百万 key ", "百万\n")
    prefix = "单盘共享" if row.group == "单盘共享" else "多盘独立"
    operation = "输入" if "输入" in row.scenario else "输出"
    quantity = "BigInteger" if "BigInteger" in row.scenario else "long"
    return f"{prefix}\n{operation} ({quantity})"


def group_title(group: str) -> str:
    return {
        "标准数据集": "标准数据集（8,192 key）",
        "百万 key": "百万 key",
        "单盘共享": "单盘共享并发",
        "多盘独立": "多盘独立并发",
    }.get(group, group)


def format_ms(seconds: float) -> str:
    milliseconds = seconds * 1000
    if milliseconds >= 100:
        return f"{milliseconds:.1f}"
    if milliseconds >= 1:
        return f"{milliseconds:.2f}"
    return f"{milliseconds:.3f}"


def save_table(
    path: Path,
    title: str,
    columns: list[str],
    rows: list[list[str]],
    widths: list[float],
    note: str,
) -> None:
    fig_height = max(5.5, 1.25 + len(rows) * 0.42)
    fig, axis = plt.subplots(figsize=(22, fig_height), dpi=180)
    axis.axis("off")
    axis.set_title(title, fontproperties=FONT_BOLD, fontsize=16, pad=18)
    table = axis.table(
        cellText=[columns] + rows,
        cellLoc="center",
        colWidths=widths,
        bbox=[0, 0.06, 1, 0.88],
    )
    table.auto_set_font_size(False)
    for (row_index, column_index), cell in table.get_celld().items():
        cell.set_edgecolor("#D0D7DE")
        cell.set_linewidth(0.7)
        cell.set_text_props(fontproperties=FONT, fontsize=8.0, color="#202124")
        if row_index == 0:
            cell.set_facecolor("#1F4E79")
            cell.set_text_props(fontproperties=FONT_BOLD, fontsize=8.2, color="white")
        elif row_index % 2 == 0:
            cell.set_facecolor("#F5F8FB")
        else:
            cell.set_facecolor("white")
    axis.text(0, 0.01, note, transform=axis.transAxes, fontproperties=FONT,
              fontsize=8.5, color="#4A5568", va="bottom")
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def plot_cell_latency(path: Path, rows: list[CellRow]) -> None:
    groups = ["标准数据集", "百万 key", "单盘共享", "多盘独立"]
    fig, axes = plt.subplots(2, 2, figsize=(21, 12), dpi=180)
    colors = ["#1769AA", "#A33F1F", "#2A9D8F"]
    labels = ["优化后吞噬盘", "未优化吞噬盘", "Omni 无限盘"]

    for axis, group in zip(axes.flat, groups):
        selected = [row for row in rows if row.group == group]
        x = np.arange(len(selected))
        values = [
            [row.optimized_seconds * 1000 for row in selected],
            [row.legacy_seconds * 1000 for row in selected],
            [row.omni_seconds * 1000 for row in selected],
        ]
        for value, color, label in zip(values, colors, labels):
            axis.plot(x, value, marker="o", linewidth=2.2, markersize=5,
                      color=color, label=label)
        axis.set_yscale("log")
        axis.set_xticks(x, [compact_scenario(row) for row in selected])
        axis.set_ylabel("平均耗时（ms，对数轴）", fontproperties=FONT)
        axis.set_title(group_title(group), fontproperties=FONT_BOLD, fontsize=12)
        axis.grid(axis="y", which="both", alpha=0.25)
        axis.tick_params(axis="x", labelsize=8)
        for tick in axis.get_xticklabels():
            tick.set_fontproperties(FONT)
        for tick in axis.get_yticklabels():
            tick.set_fontproperties(FONT)

    handles, legend_labels = axes[0, 0].get_legend_handles_labels()
    fig.legend(handles, legend_labels, loc="upper center", ncol=3, prop=FONT,
               bbox_to_anchor=(0.5, 0.995))
    fig.suptitle("三方 StorageCell 平均耗时对比（折线图；纵轴为对数刻度）",
                 fontproperties=FONT_BOLD, fontsize=17, y=1.035)
    fig.tight_layout(rect=[0, 0, 1, 0.96])
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def plot_cell_bars(path: Path, rows: list[CellRow]) -> None:
    groups = ["标准数据集", "百万 key", "单盘共享", "多盘独立"]
    fig, axes = plt.subplots(2, 2, figsize=(21, 12), dpi=180)
    colors = ["#1769AA", "#A33F1F", "#2A9D8F"]
    labels = ["优化后吞噬盘", "未优化吞噬盘", "Omni 无限盘"]

    for axis, group in zip(axes.flat, groups):
        selected = [row for row in rows if row.group == group]
        x = np.arange(len(selected))
        width = 0.25
        values = [
            [row.optimized_seconds * 1000 for row in selected],
            [row.legacy_seconds * 1000 for row in selected],
            [row.omni_seconds * 1000 for row in selected],
        ]
        for offset, value, color, label in zip(np.arange(3) * width, values, colors, labels):
            axis.bar(x + offset - width, value, width, color=color, label=label)
        axis.set_yscale("log")
        axis.set_xticks(x, [compact_scenario(row) for row in selected])
        axis.set_ylabel("平均耗时（ms，对数轴）", fontproperties=FONT)
        axis.set_title(group_title(group), fontproperties=FONT_BOLD, fontsize=12)
        axis.grid(axis="y", which="both", alpha=0.25)
        axis.tick_params(axis="x", labelsize=8)
        for tick in axis.get_xticklabels():
            tick.set_fontproperties(FONT)

    handles, legend_labels = axes[0, 0].get_legend_handles_labels()
    fig.legend(handles, legend_labels, loc="upper center", ncol=3, prop=FONT,
               bbox_to_anchor=(0.5, 0.995))
    fig.suptitle("三方 StorageCell 平均耗时对比（分组柱状图；纵轴为对数刻度）",
                 fontproperties=FONT_BOLD, fontsize=17, y=1.035)
    fig.tight_layout(rect=[0, 0, 1, 0.96])
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def plot_cell_ratios(path: Path, rows: list[CellRow]) -> None:
    labels = [compact_scenario(row) for row in rows]
    optimized = [row.optimized_ratio for row in rows]
    legacy = [row.legacy_ratio for row in rows]
    x = np.arange(len(rows))
    width = 0.37
    fig, axis = plt.subplots(figsize=(22, 8.5), dpi=180)
    first = axis.bar(x - width / 2, optimized, width, color="#1769AA",
                     label="优化后吞噬盘 / Omni")
    second = axis.bar(x + width / 2, legacy, width, color="#A33F1F",
                      label="未优化吞噬盘 / Omni")
    axis.axhline(1, color="#333333", linewidth=1.3, linestyle="--")
    axis.set_xticks(x, labels)
    axis.set_ylabel("吞噬盘是 Omni 性能的倍数", fontproperties=FONT)
    axis.set_title(">1 表示吞噬盘更快；<1 表示 Omni 更快",
                   fontproperties=FONT_BOLD, fontsize=15)
    axis.legend([first, second], ["优化后吞噬盘 / Omni", "未优化吞噬盘 / Omni"],
                prop=FONT, loc="upper left")
    axis.grid(axis="y", alpha=0.25)
    axis.tick_params(axis="x", labelsize=8)
    for tick in axis.get_xticklabels():
        tick.set_fontproperties(FONT)
    for tick in axis.get_yticklabels():
        tick.set_fontproperties(FONT)
    label_offset = max(max(optimized + legacy) * 0.02, 0.08)
    for position, value in zip(x - width / 2, optimized):
        axis.text(position, value + label_offset, f"{value:.2f}x", ha="center",
                  va="bottom", fontproperties=FONT, fontsize=7.5)
    for position, value in zip(x + width / 2, legacy):
        axis.text(position, value + label_offset, f"{value:.2f}x", ha="center",
                  va="bottom", fontproperties=FONT, fontsize=7.5)
    fig.tight_layout()
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def compact_concurrency_scenario(group: str, scenario: str) -> str:
    operation = "输入" if "输入" in scenario else "输出"
    quantity = "BigInteger" if "BigInteger" in scenario else "long"
    prefix = "单盘共享" if group == "单盘共享" else "多盘独立"
    return f"{prefix}\n{operation} ({quantity})"


def plot_concurrency(path: Path, rows: list[ConcurrencyRow]) -> None:
    groups = ["单盘共享", "多盘独立"]
    fig, axes = plt.subplots(1, 2, figsize=(20, 7), dpi=180)
    for axis, group in zip(axes, groups):
        selected = [row for row in rows if row.group == group]
        labels = [compact_concurrency_scenario(group, row.scenario) for row in selected]
        x = np.arange(len(selected))
        width = 0.37
        before = [row.before_seconds * 1000 for row in selected]
        after = [row.after_seconds * 1000 for row in selected]
        axis.bar(x - width / 2, before, width, color="#A33F1F", label="优化前")
        axis.bar(x + width / 2, after, width, color="#2A9D8F", label="优化后")
        axis.set_yscale("log")
        axis.set_xticks(x, labels)
        axis.set_ylabel("平均耗时（ms，对数轴）", fontproperties=FONT)
        axis.set_title(f"FastUtil：{group}", fontproperties=FONT_BOLD, fontsize=13)
        axis.grid(axis="y", which="both", alpha=0.25)
        axis.tick_params(axis="x", labelsize=8)
        for tick in axis.get_xticklabels():
            tick.set_fontproperties(FONT)
    handles, legend_labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, legend_labels, loc="upper center", ncol=2, prop=FONT,
               bbox_to_anchor=(0.5, 1.02))
    fig.suptitle("FastUtil 并发优化前后平均耗时对比",
                 fontproperties=FONT_BOLD, fontsize=17, y=1.075)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def plot_concurrency_speedup(path: Path, rows: list[ConcurrencyRow]) -> None:
    labels = [f"{row.group}\n{compact_concurrency_scenario(row.group, row.scenario).split(chr(10))[-1]}"
              for row in rows]
    values = [row.speedup for row in rows]
    y = np.arange(len(labels))
    fig, axis = plt.subplots(figsize=(15, 9), dpi=180)
    bars = axis.barh(y, values,
                     color=["#2A9D8F" if value >= 1 else "#E9A227" for value in values])
    axis.axvline(1, color="#333333", linewidth=1.2, linestyle="--")
    axis.set_yticks(y, labels)
    axis.invert_yaxis()
    axis.set_xlabel("优化前耗时 / 优化后耗时", fontproperties=FONT)
    axis.set_title("FastUtil 并发优化倍率（>1 表示优化后更快）",
                   fontproperties=FONT_BOLD, fontsize=15)
    axis.grid(axis="x", alpha=0.25)
    for tick in axis.get_yticklabels():
        tick.set_fontproperties(FONT)
    for bar, value in zip(bars, values):
        axis.text(value + 0.15, bar.get_y() + bar.get_height() / 2, f"{value:.2f}x",
                  va="center", fontproperties=FONT, fontsize=9)
    fig.tight_layout()
    fig.savefig(path, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def build_html(output: Path, cell: Path, concurrency: Path) -> None:
    source_links = "<br>".join(
        html.escape(str(path.resolve())) for path in (cell, concurrency)
    )
    html_content = f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>吞噬盘性能可视化报告</title>
<style>
body {{ font-family: Arial, "Microsoft YaHei", sans-serif; margin: 32px; color: #202124; }}
h1, h2 {{ color: #1F4E79; }}
p {{ line-height: 1.6; }}
img {{ display: block; max-width: 100%; margin: 18px 0 36px; border: 1px solid #D0D7DE; }}
code {{ background: #F5F8FB; padding: 2px 5px; }}
</style>
</head>
<body>
<h1>吞噬盘性能可视化报告</h1>
<p>存储盘图表只比较三方真实 StorageCell：优化后吞噬盘、未优化吞噬盘和 Omni 无限盘。倍率统一表示“吞噬盘是 Omni 性能的多少倍”，大于 1 表示吞噬盘更快。</p>
<p>FastUtil 并发报告是吞噬盘自身优化前后对比，不与 Omni 三方报告混合。耗时图使用对数纵轴，以同时容纳 us、ms 和百万 key 场景。</p>
<h2>三方存储盘汇总表</h2>
<img src="cell-comparison-table.png" alt="三方存储盘汇总表">
<h2>三方平均耗时折线图</h2>
<img src="cell-latency-line.png" alt="三方平均耗时折线图">
<h2>三方平均耗时柱状图</h2>
<img src="cell-latency-bars.png" alt="三方平均耗时柱状图">
<h2>吞噬盘相对 Omni 性能倍率</h2>
<img src="cell-ratio.png" alt="吞噬盘相对 Omni 性能倍率">
<h2>FastUtil 并发汇总表</h2>
<img src="fastutil-concurrency-table.png" alt="FastUtil 并发汇总表">
<h2>FastUtil 并发耗时</h2>
<img src="fastutil-concurrency-bars.png" alt="FastUtil 并发耗时">
<h2>FastUtil 并发优化倍率</h2>
<img src="fastutil-concurrency-speedup.png" alt="FastUtil 并发优化倍率">
<h2>数据来源</h2>
<p>{source_links}</p>
</body>
</html>
"""
    (output / "index.html").write_text(html_content, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="从三方吞噬盘基准 Markdown 报告生成中文图表")
    parser.add_argument("--cell", type=Path, default=DEFAULT_CELL)
    parser.add_argument("--concurrency", type=Path, default=DEFAULT_CONCURRENCY)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    output = args.output if args.output.is_absolute() else ROOT / args.output
    output.mkdir(parents=True, exist_ok=True)
    rows = parse_cell_report(args.cell)
    concurrency = parse_concurrency_report(args.concurrency)

    cell_table_rows = [
        [
            group_title(row.group),
            compact_scenario(row).replace("\n", " "),
            format_ms(row.optimized_seconds),
            format_ms(row.legacy_seconds),
            format_ms(row.omni_seconds),
            f"{row.optimized_ratio:.2f}x",
            f"{row.legacy_ratio:.2f}x",
            f"{row.optimized_legacy_ratio:.2f}x",
        ]
        for row in rows
    ]
    save_table(
        output / "cell-comparison-table.png",
        "优化后吞噬盘、未优化吞噬盘与 Omni 最终对比表",
        [
            "测试分组",
            "测试场景",
            "优化后吞噬盘\n耗时(ms)",
            "未优化吞噬盘\n耗时(ms)",
            "Omni 无限盘\n耗时(ms)",
            "优化后吞噬盘\n/ Omni",
            "未优化吞噬盘\n/ Omni",
            "优化后 / 未优化\n性能",
        ],
        cell_table_rows,
        [0.13, 0.18, 0.11, 0.11, 0.11, 0.12, 0.12, 0.12],
        "耗时统一换算为毫秒；倍率 = Omni 耗时 / 吞噬盘耗时，大于 1 表示吞噬盘更快。所有三方数据来自同一份报告、同一组样本和同一测量轮次。",
    )

    fastutil_table_rows = [
        [
            row.group,
            row.scenario,
            f"{row.before_seconds * 1000:.2f}",
            f"{row.after_seconds * 1000:.2f}",
            f"{row.speedup:.2f}x",
            f"{row.before_throughput:,}",
            f"{row.after_throughput:,}",
        ]
        for row in concurrency
    ]
    save_table(
        output / "fastutil-concurrency-table.png",
        "FastUtil 并发优化前后最终对比表",
        ["测试分组", "测试场景", "优化前\n耗时(ms)", "优化后\n耗时(ms)",
         "性能倍率", "优化前\n次/秒", "优化后\n次/秒"],
        fastutil_table_rows,
        [0.13, 0.34, 0.11, 0.11, 0.1, 0.105, 0.105],
        "耗时统一换算为毫秒；性能倍率 = 优化前耗时 / 优化后耗时，大于 1 表示优化后更快。",
    )

    plot_cell_latency(output / "cell-latency-line.png", rows)
    plot_cell_bars(output / "cell-latency-bars.png", rows)
    plot_cell_ratios(output / "cell-ratio.png", rows)
    plot_concurrency(output / "fastutil-concurrency-bars.png", concurrency)
    plot_concurrency_speedup(output / "fastutil-concurrency-speedup.png", concurrency)
    build_html(output, args.cell, args.concurrency)

    print(f"已生成可视化目录: {output.resolve()}")
    for file in sorted(output.iterdir()):
        print(file.name)


if __name__ == "__main__":
    main()
