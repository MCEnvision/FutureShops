#!/usr/bin/env python3
"""Regenerate futureshops-translation-template.txt 1:1 from en_us.json.
Run from the repo root:  python3 translations/generate_template.py
The template round-trips losslessly via translations/apply_translation.py."""
import json, collections, os

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EN_US = f"{REPO}/src/main/resources/assets/futureshops/lang/en_us.json"
OUT = f"{REPO}/translations/futureshops-translation-template.txt"

en = json.load(open(EN_US), object_pairs_hook=collections.OrderedDict)

def section(key):
    parts = key.split('.')
    if key.startswith(('item.', 'block.', 'itemGroup.')):
        return "ITEMS, BLOCKS & CREATIVE TAB"
    if key.startswith('tooltip.'):
        return "ITEM TOOLTIPS"
    if key.startswith('key.'):
        return "KEYBINDS"
    if key.startswith('command.'):
        area = parts[2] if len(parts) > 2 else 'misc'
        return "COMMAND MESSAGES — general" if area in ('error', 'player_only') else f"COMMAND MESSAGES — /{area}"
    if key.startswith('gui.'):
        area = parts[2] if len(parts) > 2 else 'misc'
        pretty = {
            'shop': 'Main shop screen', 'shop_main': 'Main shop screen', 'detail': 'Item detail screen',
            'item_detail': 'Item detail screen', 'barter': 'Barter screen', 'cart': 'Cart screen',
            'history': 'Transaction history', 'baltop': 'Leaderboard', 'balance': 'Balance / profile',
            'local': 'Nearby / local shops', 'department': 'Department picker',
            'player_shop': 'Player shop (results & status)', 'player_shop_block': 'Player shop block screen',
            'player_shop_barter': 'Player shop barter', 'player_shop_cart': 'Player shop cart',
            'player_shop_sell': 'Player shop sell', 'trade_mode': 'Trade-mode labels',
            'promo_editor': 'Promo editor', 'franchise': 'Franchise', 'settlement': 'Settlement history',
            'status': 'Status messages',
        }.get(area, area.replace('_', ' ').title())
        return f"GUI — {pretty}"
    return "OTHER"

groups = collections.OrderedDict()
for k, v in en.items():
    groups.setdefault(section(k), []).append((k, v))

order = ["ITEMS, BLOCKS & CREATIVE TAB", "ITEM TOOLTIPS", "KEYBINDS"]
order += sorted(s for s in groups if s.startswith("GUI —"))
order += sorted(s for s in groups if s.startswith("COMMAND"))
order += [s for s in groups if s not in order]

HEADER = """\
================================================================================
 FutureShops — TRANSLATION FILE  (game language: English, en_us)
================================================================================

Thank you for translating FutureShops! Please read these 6 rules, then translate.

HOW TO TRANSLATE
  1. Each line below looks like:      some.key.here   ->   English text
     Translate ONLY the text to the RIGHT of the first " = ".
  2. NEVER change the left side (the key) — that is how the game finds the text.
  3. Keep every %s, %1$s, %2$s EXACTLY as-is. In the game these are replaced by
     numbers/names/prices, in the same order they appear. Do not remove or reorder
     them unless your language truly needs a different order (then use %1$s / %2$s).
  4. Keep every § code EXACTLY (e.g. §a §c §7 §l §o §r). These are COLOR/STYLE
     codes, not letters — §a means green, §c red, §7 gray, §l bold, §r reset.
     Translate the words around them, keep the § codes in place.
  5. Keep symbols / emoji as-is (× ∞ ⚒ ⚑ 👑 🛒 → ← etc.) unless they contain words.
  6. Lines starting with #  are section comments — ignore them, don't translate.

WHEN DONE
  - Save the file and send the WHOLE file back. Keep the line order.
  - If you're unsure about a line, leave it in English — that's fine.
  - The mod owner will turn this into a proper language file (e.g. es_es.json).

TIP: to preview in-game, this same text can be saved as a resource pack at
     assets/futureshops/lang/<your_lang>.json  (JSON format) — but you don't
     have to; sending this .txt back is enough.
================================================================================

"""

lines = [HEADER]
total = 0
for sec in order:
    if sec not in groups:
        continue
    lines.append(f"\n# ============================================================\n# {sec}\n# ============================================================\n")
    for k, v in groups[sec]:
        lines.append(f"{k} = {v}")
        total += 1

open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print(f"wrote {OUT}  ({total} strings, {len(order)} sections)")
