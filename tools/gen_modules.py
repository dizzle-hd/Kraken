#!/usr/bin/env python3
"""Reads every module from the Java sources and writes site/modules.json for the website.

Per module: name, category, in-game description, settings (type, default, range, modes, text) and
presets (name, ban risk, note). Run from anywhere: python3 tools/gen_modules.py
The website workflow runs it before every deploy, so the page always matches the code.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/dev/nautilus"
OUT = ROOT / "site/modules.json"

STRING = r'"((?:[^"\\]|\\.)*)"'


def unescape(s):
    return bytes(s, "utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8") if "\\" in s else s


def call_args(text, start):
    """Splits the arguments of the call whose '(' is at text[start] (top-level commas only)."""
    depth, i, args, cur, in_str = 0, start, [], [], False
    while i < len(text):
        c = text[i]
        if in_str:
            cur.append(c)
            if c == "\\":
                cur.append(text[i + 1]); i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True; cur.append(c)
        elif c in "([{":
            depth += 1
            if depth > 1: cur.append(c)
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                args.append("".join(cur).strip())
                return args, i
            cur.append(c)
        elif c == "," and depth == 1:
            args.append("".join(cur).strip()); cur = []
        else:
            cur.append(c)
        i += 1
    raise ValueError("unbalanced call")


def literal(a):
    a = a.strip()
    m = re.fullmatch(STRING, a)
    if m: return unescape(m.group(1))
    if a in ("true", "false"): return a == "true"
    if re.fullmatch(r"-?\d+(\.\d+)?[fFdD]?", a): return float(a.rstrip("fFdD"))
    if a in ("LOW", "MEDIUM", "HIGH"): return ["LOW", "MEDIUM", "HIGH"].index(a)
    return None


def num(v):
    return int(v) if isinstance(v, float) and v.is_integer() else v


def parse_class(path):
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"//[^\n]*", "", text)  # line comments (no '//' inside the strings we read)
    info = {"extends": None, "super": None, "settings": [], "presets": []}
    m = re.search(r"class\s+\w+\s+extends\s+(\w+)", text)
    if m: info["extends"] = m.group(1)

    for m in re.finditer(r"new\s+(?:[\w.]+\.)?(Bool|Number|Mode|Key)Setting\s*\(", text):
        if not re.search(r"add\(\s*$", text[max(0, m.start() - 12):m.start()]): continue
        kind = m.group(1)
        args, _ = call_args(text, m.end() - 1)
        vals = [literal(a) for a in args]
        s = {"name": vals[0], "desc": vals[1], "type": kind.lower()}
        if kind == "Bool": s["default"] = vals[2]
        elif kind == "Number": s.update(default=num(vals[2]), min=num(vals[3]), max=num(vals[4]), step=num(vals[5]))
        elif kind == "Mode": s.update(default=vals[2], modes=vals[3:])
        rest = text[_ + 1:_ + 200]
        if re.match(r"\s*\.visibleWhen", rest): s["conditional"] = True
        info["settings"].append(s)

    m = re.search(r"\bsuper\s*\(", text)
    if m:
        args, _ = call_args(text, m.end() - 1)
        info["super"] = args

    for m in re.finditer(r"\bpreset\s*\(", text):
        args, _ = call_args(text, m.end() - 1)
        vals = [literal(a) for a in args]
        if not isinstance(vals[0], str): continue
        pairs = {vals[i]: (num(vals[i + 1]) if isinstance(vals[i + 1], float) else vals[i + 1])
                 for i in range(3, len(vals) - 1, 2)}
        info["presets"].append({"name": vals[0], "risk": vals[1], "note": vals[2], "values": pairs})
    return info


def main():
    cat_text = (SRC / "module/Category.java").read_text(encoding="utf-8")
    categories = [{"id": m.group(1), "name": m.group(2)} for m in re.finditer(r"^\s*(\w+)\(" + STRING, cat_text, re.M)]

    classes = {p.stem: p for p in (SRC / "module").rglob("*.java")}
    mm = (SRC / "module/ModuleManager.java").read_text(encoding="utf-8")
    order = re.findall(r"register\(new\s+(?:[\w.]+\.)?(\w+)\(\)\)", mm)

    modules = []
    for cls in order:
        info = parse_class(classes[cls])
        settings, presets = list(info["settings"]), list(info["presets"])
        sup, chain = info["super"], info
        while chain["extends"] and chain["extends"] != "Module" and chain["extends"] in classes:
            parent = parse_class(classes[chain["extends"]])
            settings = parent["settings"] + settings
            presets = presets or parent["presets"]
            if sup is None or literal(sup[0]) is None: sup = sup or parent["super"]
            chain = parent
        name, desc = literal(sup[0]), literal(sup[1])
        cat = next((a.split(".")[1] for a in sup if a.startswith("Category.")), None)
        if cat is None:  # category given in the base class
            cat = next((a.split(".")[1] for a in (chain["super"] or []) if a.startswith("Category.")), None)
        modules.append({"id": cls, "name": name, "category": cat, "description": desc,
                        "settings": settings, "presets": presets})

    OUT.write_text(json.dumps({"categories": categories, "modules": modules}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(modules)} modules -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
