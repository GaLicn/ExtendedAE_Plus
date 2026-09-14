"""从 Trinity 三方基准报告生成中文表格、倍率图和耗时图。"""

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
SOURCE = ROOT / "build" / "reports" / "trinity-storage-comparison" / "trinity-storage-comparison.md"
OUTPUT = ROOT / "build" / "reports" / "trinity-storage-comparison"


@dataclass(frozen=True)
class Row:
    group: str
    scenario: str
    operations: int
    devourer_seconds: float
    omni_seconds: float
    trinity_seconds: float
    devourer_omni_ratio: float
    devourer_trinity_ratio: float
    omni_trinity_ratio: float
    result_keys: int

    @property
    def label(self) -> str:
        return f"{self.group}\n{self.scenario}"


def split_row(line: str) -> list[str]:
    content = line.strip()
    if content.startswith("|"):
        content = content[1:]
    if content.endswith("|"):
        content = content[:-1]
    return [part.strip().replace("\\|", "|") for part in content.split("|")]


def is_separator(line: str) -> bool:
    cells = split_row(line)
    return bool(cells) and all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells)


def find_combined_table(path: Path) -> tuple[list[str], list[list[str]]]:
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    for index in range(len(lines) - 1):
        if lines[index].startswith("| 分组 |") and is_separator(lines[index + 1]):
            header = split_row(lines[index])
            rows: list[list[str]] = []
            for line in lines[index + 2 :]:
                if not line.startswith("|"):
                    break
                row = split_row(line)
                if len(row) == len(header):
                    rows.append(row)
            return header, rows
    raise ValueError(f"找不到总合并对比表: {path}")


def parse_duration(value: str) -> float:
    match = re.fullmatch(r"([0-9][0-9,]*(?:\.[0-9]+)?)\s*(us|ms|s)", value)
    if not match:
        raise ValueError(f"无法解析耗时: {value!r}")
    amount = float(match.group(1).replace(",", ""))
    return amount * {"us": 1e-6, "ms": 1e-3, "s": 1}[match.group(2)]


def parse_number(value: str) -> int:
    return int(value.replace(",", "").strip())


def parse_ratio(value: str) -> float:
    return float(value.removesuffix("x"))


def parse_rows(path: Path) -> list[Row]:
    header, values = find_combined_table(path)
    positions = {name: index for index, name in enumerate(header)}
    required = [
        "分组", "用例", "操作数", "吞噬盘平均", "Omni BigInteger 平均", "DE Trinity 平均",
        "吞噬盘/Omni", "吞噬盘/DE", "Omni/DE", "结果 key",
    ]
    missing = [name for name in required if name not in positions]
    if missing:
        raise ValueError(f"报告缺少列: {missing}")

    return [
        Row(
            group=value[positions["分组"]],
            scenario=value[positions["用例"]],
            operations=parse_number(value[positions["操作数"]]),
            devourer_seconds=parse_duration(value[positions["吞噬盘平均"]]),
            omni_seconds=parse_duration(value[positions["Omni BigInteger 平均"]]),
            trinity_seconds=parse_duration(value[positions["DE Trinity 平均"]]),
            devourer_omni_ratio=parse_ratio(value[positions["吞噬盘/Omni"]]),
            devourer_trinity_ratio=parse_ratio(value[positions["吞噬盘/DE"]]),
            omni_trinity_ratio=parse_ratio(value[positions["Omni/DE"]]),
            result_keys=parse_number(value[positions["结果 key"]]),
        )
        for value in values
    ]


def choose_font() -> tuple[font_manager.FontProperties, font_manager.FontProperties]:
    candidates = [
        Path(r"C:\Windows\Fonts\Deng.ttf"),
        Path(r"C:\Windows\Fonts\simhei.ttf"),
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


def short_duration(seconds: float) -> str:
    if seconds < 1e-3:
        return f"{seconds * 1e6:.2f} us"
    if seconds < 1:
        return f"{seconds * 1e3:.2f} ms"
    return f"{seconds:.2f} s"


def table_image(rows: list[Row]) -> None:
    headers = [
        "分组", "测试用例", "操作数", "吞噬盘平均", "Omni BigInteger平均", "DE Trinity平均",
        "吞噬盘/Omni", "吞噬盘/DE", "Omni/DE", "结果key",
    ]
    values = [
        [
            row.group,
            row.scenario,
            f"{row.operations:,}",
            short_duration(row.devourer_seconds),
            short_duration(row.omni_seconds),
            short_duration(row.trinity_seconds),
            f"{row.devourer_omni_ratio:.2f}x",
            f"{row.devourer_trinity_ratio:.2f}x",
            f"{row.omni_trinity_ratio:.2f}x",
            f"{row.result_keys:,}",
        ]
        for row in rows
    ]

    figure, axis = plt.subplots(figsize=(22, max(10, 0.42 * len(values) + 2.5)), dpi=180)
    axis.axis("off")
    table = axis.table(cellText=values, colLabels=headers, loc="center", cellLoc="center")
    table.auto_set_font_size(False)
    table.set_fontsize(10)
    table.scale(1, 1.7)
    widths = [0.095, 0.21, 0.07, 0.095, 0.11, 0.095, 0.085, 0.085, 0.07, 0.07]
    for (row_index, column_index), cell in table.get_celld().items():
        cell.set_width(widths[column_index])
        cell.set_edgecolor("#CBD5E1")
        cell.set_linewidth(0.7)
        cell.set_text_props(fontproperties=FONT, fontsize=10, color="#17202A")
        if row_index == 0:
            cell.set_facecolor("#145A72")
            cell.set_text_props(fontproperties=FONT_BOLD, fontsize=10, color="white")
        else:
            cell.set_facecolor("#F3F7F9" if row_index % 2 == 0 else "white")
            if column_index in (6, 7):
                cell.set_text_props(fontproperties=FONT_BOLD, fontsize=10, color="#B45309")

    axis.set_title(
        "吞噬盘、Omni BigInteger 盘与 DE Trinity 三方性能对比\n"
        "倍率含义：吞噬盘/方案 = 方案耗时 ÷ 吞噬盘耗时，数值越大表示吞噬盘越快",
        fontproperties=FONT_BOLD, fontsize=17, color="#123B4A", pad=20,
    )
    figure.tight_layout()
    figure.savefig(OUTPUT / "trinity-storage-comparison-table.png", bbox_inches="tight", facecolor="white")
    plt.close(figure)


def ratio_image(rows: list[Row]) -> None:
    labels = [row.label for row in rows]
    y = np.arange(len(rows))
    height = 0.36
    figure, axis = plt.subplots(figsize=(15, max(10, 0.43 * len(rows) + 2)), dpi=180)
    axis.barh(y - height / 2, [row.devourer_omni_ratio for row in rows], height,
              label="吞噬盘相对 Omni", color="#167D8D")
    axis.barh(y + height / 2, [row.devourer_trinity_ratio for row in rows], height,
              label="吞噬盘相对 DE Trinity", color="#E28A2B")
    axis.axvline(1, color="#52606D", linewidth=1.2, linestyle="--")
    axis.set_yticks(y, labels, fontproperties=FONT, fontsize=9)
    axis.invert_yaxis()
    axis.set_xlabel("性能倍数（吞噬盘为对方的多少倍）", fontproperties=FONT, fontsize=11)
    axis.set_title("吞噬盘相对 Omni / DE Trinity 的性能倍数\n越靠右表示吞噬盘越快",
                   fontproperties=FONT_BOLD, fontsize=17, color="#123B4A")
    axis.legend(prop=FONT, loc="lower right", frameon=False)
    axis.grid(axis="x", color="#D7E1E5", linewidth=0.8)
    axis.set_axisbelow(True)
    for index, row in enumerate(rows):
        axis.text(row.devourer_omni_ratio + 0.08, index - height / 2,
                  f"{row.devourer_omni_ratio:.2f}x", va="center", fontproperties=FONT, fontsize=8)
        axis.text(row.devourer_trinity_ratio + 0.08, index + height / 2,
                  f"{row.devourer_trinity_ratio:.2f}x", va="center", fontproperties=FONT, fontsize=8)
    figure.tight_layout()
    figure.savefig(OUTPUT / "trinity-storage-comparison-ratio.png", bbox_inches="tight", facecolor="white")
    plt.close(figure)


def latency_image(rows: list[Row]) -> None:
    labels = [row.label for row in rows]
    y = np.arange(len(rows))
    height = 0.24
    figure, axis = plt.subplots(figsize=(15, max(10, 0.43 * len(rows) + 2)), dpi=180)
    axis.barh(y - height, [row.devourer_seconds for row in rows], height,
              label="吞噬盘", color="#167D8D")
    axis.barh(y, [row.omni_seconds for row in rows], height,
              label="Omni BigInteger", color="#6B8E23")
    axis.barh(y + height, [row.trinity_seconds for row in rows], height,
              label="DE Trinity", color="#E28A2B")
    axis.set_xscale("log")
    axis.set_yticks(y, labels, fontproperties=FONT, fontsize=9)
    axis.invert_yaxis()
    axis.set_xlabel("平均耗时（对数坐标，越短越好）", fontproperties=FONT, fontsize=11)
    axis.set_title("三方平均耗时对比（10 次正式测量平均）",
                   fontproperties=FONT_BOLD, fontsize=17, color="#123B4A")
    axis.legend(prop=FONT, loc="lower right", frameon=False)
    axis.grid(axis="x", which="both", color="#D7E1E5", linewidth=0.8)
    axis.set_axisbelow(True)
    figure.tight_layout()
    figure.savefig(OUTPUT / "trinity-storage-comparison-latency.png", bbox_inches="tight", facecolor="white")
    plt.close(figure)


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"报告不存在: {SOURCE}")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    rows = parse_rows(SOURCE)
    table_image(rows)
    ratio_image(rows)
    latency_image(rows)
    print(f"已生成 {len(rows)} 条用例的三方对比图片: {OUTPUT}")


if __name__ == "__main__":
    main()
