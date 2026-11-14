"""Shared dynamic path discovery helpers for repository tests (no hardcoded local paths)."""
from __future__ import annotations

import os
import re
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]


def _java_major_from_release(home: Path) -> int:
    release_file = home / "release"
    if release_file.is_file():
        text = release_file.read_text(encoding="utf-8", errors="ignore")
        m = re.search(r'^JAVA_VERSION="1\.(\d+)', text, re.MULTILINE)
        if m:
            return int(m.group(1))
        m = re.search(r'^JAVA_VERSION="(\d+)', text, re.MULTILINE)
        if m:
            return int(m.group(1))
    return 0


def find_jdk_tool(
    tool_name: str,
    major_versions: tuple[int, ...],
    env_vars: tuple[str, ...] = (),
) -> Path:
    def _valid_home(home: Path) -> Path | None:
        tool = home / "bin" / tool_name
        javac = home / "bin" / "javac.exe"
        if tool.is_file() and javac.is_file():
            if not major_versions or _java_major_from_release(home) in major_versions:
                return tool
        return None

    for var in env_vars:
        val = os.environ.get(var, "").strip()
        if val:
            found = _valid_home(Path(val))
            if found:
                return found

    search_roots = []
    for env_key, suffix in (
        ("ProgramFiles", None),
        ("ProgramFiles(x86)", None),
        ("LOCALAPPDATA", "Programs"),
        ("USERPROFILE", ".jdks"),
    ):
        base = os.environ.get(env_key, "").strip()
        if base:
            p = Path(base) / suffix if suffix else Path(base)
            if p.is_dir():
                search_roots.append(p)

    vendors = ("Eclipse Adoptium", "Java", "Microsoft", "Zulu", "BellSoft", "Amazon Corretto", "Temurin", "")
    matches: list[tuple[int, str, Path]] = []
    for root in search_roots:
        for vendor in vendors:
            base = root / vendor if vendor else root
            if not base.is_dir():
                continue
            for child in base.iterdir():
                if not child.is_dir():
                    continue
                found = _valid_home(child)
                if found:
                    major = _java_major_from_release(child)
                    rank = major_versions.index(major) if major_versions else 0
                    matches.append((rank, child.name, found))

    if matches:
        matches.sort(key=lambda item: (item[0], tuple(-ord(c) for c in item[1])))
        return matches[0][2]

    java_home = os.environ.get("JAVA_HOME", "").strip()
    if java_home:
        found = _valid_home(Path(java_home))
        if found:
            return found

    which_path = shutil.which(tool_name)
    if which_path:
        tool = Path(which_path).resolve()
        found = _valid_home(tool.parent.parent)
        if found:
            return found

    raise FileNotFoundError(f"JDK tool {tool_name} for Java {major_versions} not found via env/standard dirs/PATH")


def find_workspace_dir(relative_parts: tuple[str, ...], env_var: str) -> Path:
    override = os.environ.get(env_var, "").strip()
    if override:
        return Path(override)
    cur = REPO_ROOT
    for _ in range(5):
        candidate = cur.joinpath(*relative_parts)
        if candidate.exists():
            return candidate
        if cur.parent == cur:
            break
        cur = cur.parent
    return REPO_ROOT.parents[1].joinpath(*relative_parts)


def find_sample_html() -> Path:
    return find_workspace_dir(
        ("IPED_testes_chat_real", "Exportados", "arquivos", "F", "A", "FAF3132301A706022E1C3EAD6AE0800B.html"),
        "CHAT_CAPTURE_SAMPLE_HTML",
    )


def find_sample_capture() -> Path:
    override = os.environ.get("CHAT_CAPTURE_SAMPLE_DIR", "").strip()
    if override:
        return Path(override)
    base = find_workspace_dir(("IPED_testes_chat_real", "teste_chat_extraido"), "CHAT_CAPTURE_SAMPLE_ROOT")
    if base.is_dir():
        candidates = sorted((d for d in base.iterdir() if d.is_dir() and (d / "messages.json").is_file()), key=lambda p: p.name)
        for d in candidates:
            txt = (d / "messages.json").read_text(encoding="utf-8", errors="ignore")
            if all(mid in txt for mid in ("243188", "243189", "243190")):
                return d
        if candidates:
            return candidates[0]
    return base / "captura-chat-sample"


def find_iped_jjs() -> Path:
    override = os.environ.get("IPED_JJS", "").strip()
    if override:
        return Path(override)
    iped_home = os.environ.get("IPED_HOME", "").strip()
    if iped_home:
        candidate = Path(iped_home) / "jre" / "bin" / "jjs.exe"
        if candidate.is_file():
            return candidate
    return find_workspace_dir(("iped-4.3.1", "jre", "bin", "jjs.exe"), "IPED_JJS")
