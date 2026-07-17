# FutureShops translations

Everything a player needs to translate the mod, and how to turn their work into a
usable language file.

## For the owner — the round-trip

1. **Send** `futureshops-translation-template.txt` to the translator. It contains
   **every** user-facing string in the mod (772 lines), grouped into readable
   sections, with a 6-rule how-to header. As of v2.3 there are no hardcoded
   English strings left — translating this file localizes the whole mod, including
   in-game chat messages (each client sees them in its own language).

2. The translator edits the text to the right of each ` = `, keeps the keys,
   `%s`/`§` codes and symbols, and sends the file back.

3. **Apply** it:

   ```
   python3 translations/apply_translation.py  <their_file.txt>  <lang_code>  --write
   # example — European Spanish:
   python3 translations/apply_translation.py  es_es.txt  es_es  --write
   ```

   This writes `src/main/resources/assets/futureshops/lang/<lang_code>.json`.
   It also prints warnings for missing keys, unknown/typo'd keys, untranslated
   lines (all harmless — they fall back to English), and any line where the
   `%s`/`§` count drifted from English (worth a quick look — that can render wrong).

4. Rebuild (`./gradlew build`). Done — players with the game set to that language
   automatically get the translation; nothing else to configure.

## Language codes

Use Minecraft's code for the target language, e.g. `es_es` (Spain), `es_mx`
(Mexico), `de_de`, `fr_fr`, `pt_br`, `ru_ru`, `zh_cn`, `ja_jp`. The file must be
named exactly `<code>.json` (lowercase).

## Regenerating the template

If new strings are ever added to `en_us.json`, regenerate the template so it
stays complete — ask the mod's maintainer (or re-run the generator used to build
it). The template is derived 1:1 from `en_us.json`, so it round-trips losslessly.
