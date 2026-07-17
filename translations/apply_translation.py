#!/usr/bin/env python3
"""Turn a translated ...-template.txt back into a Minecraft lang JSON.
Usage: apply_translation.py <translated.txt> <lang_code e.g. es_es>  [--check-against en_us]
Parses `key = value` lines (ignores #-comments and blanks), splits on the FIRST ' = '.
Writes src/main/resources/assets/futureshops/lang/<lang_code>.json.
Warns on: keys missing vs en_us, extra/unknown keys, values still identical to English,
and %s/§-code count drift (a common translator slip)."""
import json, sys, re, collections, pathlib

import os
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EN_US = f"{REPO}/src/main/resources/assets/futureshops/lang/en_us.json"

# A real lang key: dotted, no spaces, ascii — rejects header prose that happens to contain " = ".
KEY_SHAPE = re.compile(r'^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_$]+)+$')

def parse_template(path):
    out = collections.OrderedDict()
    for ln in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        s = ln.rstrip("\n")
        if not s.strip() or s.lstrip().startswith("#") or " = " not in s:
            continue
        k, v = s.split(" = ", 1)
        k = k.strip()
        if KEY_SHAPE.match(k):
            out[k] = v
    return out

def counts(v):
    return (len(re.findall(r'%(?:\d+\$)?[sd]', v)), len(re.findall('§.', v)))

def main():
    if len(sys.argv) < 3:
        print(__doc__); sys.exit(1)
    txt, lang = sys.argv[1], sys.argv[2]
    en = json.load(open(EN_US), object_pairs_hook=collections.OrderedDict)
    tr = parse_template(txt)

    missing = [k for k in en if k not in tr]
    extra   = [k for k in tr if k not in en]
    ph_drift, still_en = [], []
    for k, v in tr.items():
        if k in en:
            if counts(v) != counts(en[k]):
                ph_drift.append(f"{k}: en={counts(en[k])} tr={counts(v)}  ({en[k]!r} -> {v!r})")
            if v == en[k] and any(c.isalpha() for c in re.sub('§.', '', en[k])):
                still_en.append(k)

    print(f"parsed {len(tr)} lines; en_us has {len(en)} keys")
    if missing:  print(f"\n⚠ {len(missing)} keys MISSING from translation (will fall back to English at runtime):\n   " + "\n   ".join(missing[:40]) + (" ..." if len(missing)>40 else ""))
    if extra:    print(f"\n⚠ {len(extra)} UNKNOWN keys in translation (typo'd key? ignored):\n   " + "\n   ".join(extra[:40]))
    if ph_drift: print(f"\n⚠ {len(ph_drift)} lines with %s/§ COUNT DRIFT (review — may render wrong):\n   " + "\n   ".join(ph_drift[:40]))
    if still_en: print(f"\nℹ {len(still_en)} lines left in English (untranslated) — fine, they fall back cleanly.")

    # Build final: keep en_us key order; use translated value where present, else English fallback.
    final = collections.OrderedDict((k, tr.get(k, en[k])) for k in en)
    if "--write" in sys.argv:
        out = f"{REPO}/src/main/resources/assets/futureshops/lang/{lang}.json"
        with open(out, "w", encoding="utf-8") as f:
            json.dump(final, f, ensure_ascii=False, indent=2); f.write("\n")
        print(f"\n== WROTE {out} ==")
    else:
        print("\n(dry run — pass --write to create the lang json)")

if __name__ == "__main__":
    main()
