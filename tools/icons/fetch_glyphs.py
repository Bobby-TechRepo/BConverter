#!/usr/bin/env python3
"""Download icons from the Iconify 'glyphs' set (Goran Spasojevic, MIT) and write them as Android VectorDrawables.

Usage (from the project root, needs internet and Python 3.8+, no extra packages):
    python tools/icons/fetch_glyphs.py                 # fetch everything in tools/icons/icons.json
    python tools/icons/fetch_glyphs.py --search arrow  # list Glyphs names matching a word
    python tools/icons/fetch_glyphs.py --svg-dir DIR   # convert DIR/<glyphs-name>.svg files offline instead of fetching
Outline variants are preferred: for each candidate name "<name>-outline" is tried before "<name>".
"""
import argparse, json, math, os, re, sys, urllib.request, urllib.parse
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
API = "https://api.iconify.design"
PREFIX = "glyphs"


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "bconverter-icons"}), timeout=30) as r:
        return r.read().decode("utf-8")


def num(v, default=0.0):
    if v is None:
        return default
    m = re.match(r"\s*(-?[\d.]+(?:e-?\d+)?)", v)
    return float(m.group(1)) if m else default


def f(x):
    s = ("%.4f" % x).rstrip("0").rstrip(".")
    return s if s not in ("", "-0") else "0"


def shape_to_path(tag, a):
    if tag == "path":
        return a.get("d")
    if tag == "line":
        return "M%s,%sL%s,%s" % (f(num(a.get("x1"))), f(num(a.get("y1"))), f(num(a.get("x2"))), f(num(a.get("y2"))))
    if tag in ("polyline", "polygon"):
        pts = [float(x) for x in re.findall(r"-?[\d.]+(?:e-?\d+)?", a.get("points", ""))]
        if len(pts) < 4:
            return None
        d = "M" + "L".join("%s,%s" % (f(pts[i]), f(pts[i + 1])) for i in range(0, len(pts) - 1, 2))
        return d + ("Z" if tag == "polygon" else "")
    if tag in ("circle", "ellipse"):
        cx, cy = num(a.get("cx")), num(a.get("cy"))
        rx = num(a.get("r")) if tag == "circle" else num(a.get("rx"))
        ry = rx if tag == "circle" else num(a.get("ry"))
        return "M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 %s,0Z" % (f(cx - rx), f(cy), f(rx), f(ry), f(2 * rx), f(rx), f(ry), f(-2 * rx))
    if tag == "rect":
        x, y, w, h = num(a.get("x")), num(a.get("y")), num(a.get("width")), num(a.get("height"))
        rx = num(a.get("rx"), num(a.get("ry")))
        ry = num(a.get("ry"), rx)
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0 or ry <= 0:
            return "M%s,%sh%sv%sh%sZ" % (f(x), f(y), f(w), f(h), f(-w))
        return ("M%s,%sh%sa%s,%s 0 0,1 %s,%sv%sa%s,%s 0 0,1 %s,%sh%sa%s,%s 0 0,1 %s,%sv%sa%s,%s 0 0,1 %s,%sZ" % (
            f(x + rx), f(y), f(w - 2 * rx), f(rx), f(ry), f(rx), f(ry), f(h - 2 * ry), f(rx), f(ry), f(-rx), f(ry),
            f(-(w - 2 * rx)), f(rx), f(ry), f(-rx), f(-ry), f(-(h - 2 * ry)), f(rx), f(ry), f(rx), f(-ry)))
    return None


def color(value, opacity):
    """SVG paint -> '#AARRGGBB' (black stands in for currentColor; the app tints it) or None for none/unset."""
    if value is None or value == "none":
        return None
    rgb = "000000"
    v = value.strip()
    if re.fullmatch(r"#[0-9a-fA-F]{6}", v):
        rgb = v[1:]
    elif re.fullmatch(r"#[0-9a-fA-F]{3}", v):
        rgb = "".join(c * 2 for c in v[1:])
    alpha = max(0.0, min(1.0, opacity))
    return "#%02X%s" % (round(alpha * 255), rgb.upper())


INHERITED = ["fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-opacity", "stroke-opacity", "fill-rule", "stroke-miterlimit"]


def style_of(el):
    s = dict(el.attrib)
    for part in s.get("style", "").split(";"):
        if ":" in part:
            k, v = part.split(":", 1)
            s[k.strip()] = v.strip()
    return s


def convert(svg_text, auto_mirrored=False, size_dp=24):
    root = ET.fromstring(svg_text)
    root_tag = root.tag.split("}")[-1]
    assert root_tag == "svg", "not an svg"
    vb = [float(x) for x in re.split(r"[ ,]+", root.get("viewBox", "").strip())] if root.get("viewBox") else [0, 0, num(root.get("width"), 24), num(root.get("height"), 24)]
    paths = []

    def walk(el, inherited, group_opacity):
        for child in el:
            tag = child.tag.split("}")[-1]
            st = style_of(child)
            cur = dict(inherited)
            for k in INHERITED:
                if k in st:
                    cur[k] = st[k]
            op = group_opacity * num(st.get("opacity"), 1.0)
            if tag in ("g", "svg"):
                walk(child, cur, op)
                continue
            d = shape_to_path(tag, st)
            if not d:
                continue
            fill = color(cur.get("fill", "#000000"), num(cur.get("fill-opacity"), 1.0) * op)
            stroke = color(cur.get("stroke"), num(cur.get("stroke-opacity"), 1.0) * op)
            if fill is None and stroke is None:
                continue
            attrs = ['android:pathData="%s"' % d.replace('"', "")]
            if fill:
                attrs.append('android:fillColor="%s"' % fill)
                if cur.get("fill-rule") == "evenodd":
                    attrs.append('android:fillType="evenOdd"')
            if stroke:
                attrs.append('android:strokeColor="%s"' % stroke)
                attrs.append('android:strokeWidth="%s"' % f(num(cur.get("stroke-width"), 1.0)))
                attrs.append('android:strokeLineCap="%s"' % {"round": "round", "square": "square"}.get(cur.get("stroke-linecap"), "butt"))
                attrs.append('android:strokeLineJoin="%s"' % {"round": "round", "bevel": "bevel"}.get(cur.get("stroke-linejoin"), "miter"))
            paths.append("    <path " + " ".join(attrs) + " />")

    base = {k: root.get(k) for k in INHERITED if root.get(k) is not None}
    walk(root, base, num(root.get("opacity"), 1.0))
    if not paths:
        raise ValueError("no drawable shapes found")
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated by tools/icons/fetch_glyphs.py from the Iconify 'glyphs' set (MIT). Do not edit by hand. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%ddp" android:height="%ddp" android:viewportWidth="%s" android:viewportHeight="%s"%s>\n'
            "%s\n</vector>\n") % (size_dp, size_dp, f(vb[2]), f(vb[3]), '\n    android:autoMirrored="true"' if auto_mirrored else "", "\n".join(paths))


def candidates(names):
    """A name may carry Iconify transform options after '?', e.g. "chevron?flip=horizontal" or "arrow?rotate=90deg"."""
    for n in names:
        base, sep, opts = n.partition("?")
        if not base.endswith("-outline"):
            yield base + "-outline" + sep + opts
        yield n


def write_preview(used):
    """tools/icons/preview.html: every chosen icon at a glance, so wrong directions are easy to spot (fix with ?flip=horizontal / ?rotate=90deg in icons.json)."""
    cells = []
    for target, cand in used:
        name, sep, opts = cand.partition("?")
        cells.append('<figure><img width="48" height="48" src="%s/%s/%s.svg%s"><figcaption>%s<br><small>%s</small></figcaption></figure>' % (API, PREFIX, name, sep + opts, target, cand))
    html = ('<!doctype html><meta charset="utf-8"><title>Glyphs preview</title>'
            "<style>body{font:14px sans-serif;display:flex;flex-wrap:wrap;gap:12px;padding:16px}figure{margin:0;width:150px;text-align:center}</style>" + "".join(cells))
    open(os.path.join(os.path.dirname(__file__), "preview.html"), "w", encoding="utf-8").write(html)
    print("Wrote tools/icons/preview.html - open it to check arrow directions.")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--search")
    ap.add_argument("--svg-dir")
    ap.add_argument("--config", default=os.path.join(os.path.dirname(__file__), "icons.json"))
    args = ap.parse_args()
    if args.search:
        data = json.loads(get("%s/search?query=%s&prefix=%s&limit=64" % (API, urllib.parse.quote(args.search), PREFIX)))
        print("\n".join(data.get("icons", [])) or "no matches")
        return 0
    cfg = json.load(open(args.config, encoding="utf-8"))
    os.makedirs(OUT, exist_ok=True)
    missing = []
    used = []
    for target, names in cfg["icons"].items():
        done = False
        for cand in candidates(names):
            try:
                if args.svg_dir:
                    p = os.path.join(args.svg_dir, cand.partition("?")[0] + ".svg")
                    if not os.path.exists(p):
                        continue
                    svg = open(p, encoding="utf-8").read()
                else:
                    name, sep, opts = cand.partition("?")
                    svg = get("%s/%s/%s.svg%s" % (API, PREFIX, name, sep + opts))
                xml = convert(svg, target in cfg.get("autoMirrored", []))
            except Exception as e:  # 404, parse problems: try the next candidate
                continue
            open(os.path.join(OUT, target + ".xml"), "w", encoding="utf-8", newline="\n").write(xml)
            print("%-22s <- glyphs:%s" % (target, cand))
            used.append((target, cand))
            done = True
            break
        if not done:
            missing.append(target)
    write_preview(used)
    for m in missing:
        print("NOT FOUND: %s (tried %s). Use --search <word> to find a name, then edit icons.json." % (m, ", ".join(candidates(cfg["icons"][m]))))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
