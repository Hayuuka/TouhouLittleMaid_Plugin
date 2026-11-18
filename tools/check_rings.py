#!/usr/bin/env python3
"""三环纪律扫描（blueprint 0.4 / 0.11）。

环1 core.domain：只准 import JDK 与本项目包，出现任何 Bukkit/Paper/Citizens/BM/CE 即违规。
环2 其余包（除 platform.impl）：不得出现 Citizens / BetterModel / CraftEngine 的类引用。
环3 platform.impl：唯一允许直接对接三方 API 的落点。

用法：python tools/check_rings.py [源码根目录]
退出码：0 通过，1 有违规。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RING1_PACKAGE = "dev.touhoulittlemaid.core.domain"
RING3_PACKAGE = "dev.touhoulittlemaid.platform.impl"

RING1_ALLOWED_PREFIXES = ("java.", "dev.touhoulittlemaid.")
PLATFORM_MARKERS = ("org.bukkit", "io.papermc", "net.citizensnpcs", "kr.toxicity", "net.momirealms")

IMPORT_RE = re.compile(r"^\s*import\s+(static\s+)?([\w.]+)\s*;", re.MULTILINE)
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)


def package_of(text: str) -> str:
    match = PACKAGE_RE.search(text)
    return match.group(1) if match else ""


def scan_file(path: Path, root: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    package = package_of(text)
    rel = path.relative_to(root).as_posix()
    problems: list[str] = []

    if package == RING1_PACKAGE or package.startswith(RING1_PACKAGE + "."):
        for match in IMPORT_RE.finditer(text):
            imported = match.group(2)
            if not imported.startswith(RING1_ALLOWED_PREFIXES):
                problems.append(f"{rel}: 环1 不得 import {imported}")
        for marker in PLATFORM_MARKERS:
            if marker in text:
                problems.append(f"{rel}: 环1 出现了平台类引用 {marker}")
        return problems

    if package == RING3_PACKAGE or package.startswith(RING3_PACKAGE + "."):
        return problems

    imports = [m.group(2) for m in IMPORT_RE.finditer(text)]
    for marker in ("net.citizensnpcs", "kr.toxicity", "net.momirealms"):
        for imported in imports:
            if imported.startswith(marker):
                problems.append(f"{rel}: 环2 不得 import {imported}")
        if re.search(rf"\b{re.escape(marker)}\.", text):
            problems.append(f"{rel}: 环2 出现了平台类的全限定名 {marker}.*")
    return problems


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
    if not root.exists():
        print(f"源码目录不存在: {root}")
        return 1

    files = sorted(root.rglob("*.java"))
    violations: list[str] = []
    for path in files:
        violations.extend(scan_file(path, root))

    if violations:
        print(f"三环扫描失败（{len(violations)} 处）：")
        for violation in violations:
            print("  " + violation)
        return 1

    print(f"三环扫描通过：{len(files)} 个 Java 文件，无越界引用。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
