#!/usr/bin/env python3
"""
find_unused_css.py — Trova e rimuove selettori CSS non utilizzati in un progetto JavaFX.

Uso:
  python find_unused_css.py [ROOT_DIR] [opzioni]

Argomenti:
  ROOT_DIR           Radice del progetto (default: directory corrente)
  --remove           Rimuove fisicamente i blocchi CSS inutilizzati (chiede conferma)
  --no-confirm       Rimuove senza chiedere conferma (ATTENZIONE)
  --no-backup        Non crea file .bak prima di modificare
  --css-dir PATH     Cerca i CSS solo in questa directory
  --src-dir PATH     Cerca i sorgenti (.java/.fxml) solo in questa directory
  --show-duplicates  Mostra anche i selettori duplicati nello stesso file
  --dry-run          Analizza senza mai modificare i file
  --verbose          Mostra anche le classi riconosciute come usate

Esempi:
  python find_unused_css.py .
  python find_unused_css.py /path/to/project --show-duplicates
  python find_unused_css.py . --remove --css-dir src/main/resources/css
  python find_unused_css.py . --remove --no-confirm --no-backup   # CI/CD use
"""

import os
import re
import sys
import argparse
from pathlib import Path
from collections import defaultdict


# ═══════════════════════════════════════════════════════════════════
#  REGEX
# ═══════════════════════════════════════════════════════════════════

# Selettori di classe CSS: .my-class, .modal-btn-primary, ecc.
CSS_CLASS_RE = re.compile(r'\.([a-zA-Z][a-zA-Z0-9_-]*)')

# Java: getStyleClass().add("name") / addAll("a", "b") / setAll("a")
JAVA_STYLECLASS_RE = re.compile(
    r'getStyleClass\s*\(\s*\)\s*\.\s*(?:add|addAll|setAll|set)\s*\(([^)]+)\)'
)
# Java: ObservableList<String> aggiornato con set(index, "class")
JAVA_STYLECLASS_SET_RE = re.compile(
    r'getStyleClass\s*\(\s*\)\s*\.\s*remove\s*\(\s*"([^"]+)"\s*\)'
)
# Java: pseudoClassStateChanged(PseudoClass.getPseudoClass("name"), ...)
JAVA_PSEUDOCLASS_RE = re.compile(
    r'getPseudoClass\s*\(\s*"([^"]+)"\s*\)'
)
# Qualsiasi stringa in kebab-case che sembra un nome di classe CSS
# (cattura falsi positivi, ma meglio essere conservativi)
JAVA_STRING_RE = re.compile(r'"([a-z][a-z0-9]*(?:-[a-z0-9]+)+)"')

# FXML: styleClass="class-a class-b"
FXML_STYLECLASS_RE = re.compile(r'styleClass\s*=\s*"([^"]+)"')
# FXML: <stylesheets> — skip
# FXML: attributi come id="myId" — skip

# Selector di tipo (non classi): .root, .button, .label — da NON considerare "unused"
# perché spesso matchano widget JavaFX built-in
BUILTIN_TYPES = {
    'root', 'button', 'label', 'text', 'text-field', 'text-area',
    'scroll-pane', 'scroll-bar', 'track', 'thumb', 'increment-button',
    'decrement-button', 'increment-arrow', 'decrement-arrow',
    'viewport', 'corner', 'bar', 'progress', 'check-box', 'radio-button',
    'combo-box', 'list-view', 'list-cell', 'table-view', 'table-cell',
    'table-column', 'tree-view', 'slider', 'split-pane', 'tab-pane', 'tab',
    'tool-bar', 'menu-bar', 'menu', 'menu-item', 'separator', 'tooltip',
    'dialog-pane', 'header-panel', 'content', 'button-bar',
    'percentage', 'progress-color', 'prompt-text', 'placeholder',
}


# ═══════════════════════════════════════════════════════════════════
#  PARSING CSS — blocchi strutturali
# ═══════════════════════════════════════════════════════════════════

def parse_css_blocks(css_text: str) -> list[dict]:
    """
    Estrae i blocchi CSS come lista di dizionari:
      {
        'selectors':     list[str]   — nomi di classe usati nel selettore
        'raw_selector':  str         — selettore grezzo (es: ".modal-btn:hover")
        'body':          str         — contenuto tra { }
        'start':         int         — offset inizio nel testo originale
        'end':           int         — offset fine (incluso '}')
        'line':          int         — numero riga di inizio
        'is_nested':     bool        — True se è dentro un @-rule (es: @keyframes)
      }
    """
    blocks = []
    text = css_text
    length = len(text)

    # Mappa offset → numero riga
    line_map = _build_line_map(text)

    pos = 0
    last_block_end = 0

    while pos < length:
        # Saltiamo i commenti
        if text[pos:pos+2] == '/*':
            end = text.find('*/', pos + 2)
            pos = end + 2 if end != -1 else length
            continue

        # Troviamo il prossimo '{'
        open_brace = _find_next_char(text, '{', pos)
        if open_brace == -1:
            break

        # Testo selettore: dalla fine dell'ultimo blocco a '{'
        selector_raw = text[last_block_end:open_brace].strip()
        # Rimuove commenti inline dal selettore
        selector_raw = re.sub(r'/\*.*?\*/', '', selector_raw, flags=re.DOTALL).strip()

        # Trova la chiusura bilanciata '}'
        depth = 1
        i = open_brace + 1
        while i < length and depth > 0:
            if text[i] == '{':
                depth += 1
            elif text[i] == '}':
                depth -= 1
            elif text[i:i+2] == '/*':
                end_c = text.find('*/', i + 2)
                i = end_c + 2 if end_c != -1 else length
                continue
            i += 1
        close_brace = i - 1

        body = text[open_brace + 1:close_brace].strip()

        # Controlla se è un @-rule (es: @keyframes, @media)
        is_nested = bool(re.search(r'@\w+', selector_raw))

        # Estrae nomi di classe dal selettore
        clean_sel = re.sub(r'/\*.*?\*/', '', selector_raw).strip()
        class_names = [
            m.group(1) for m in CSS_CLASS_RE.finditer(clean_sel)
            if m.group(1) not in BUILTIN_TYPES
        ]

        if clean_sel and not is_nested:
            line_num = _offset_to_line(line_map, last_block_end)
            blocks.append({
                'selectors':    class_names,
                'raw_selector': clean_sel,
                'body':         body,
                'start':        last_block_end,
                'end':          close_brace,
                'line':         line_num,
                'is_nested':    False,
            })
        elif is_nested:
            # Blocco @-rule: segniamo come "da non toccare"
            line_num = _offset_to_line(line_map, last_block_end)
            blocks.append({
                'selectors':    [],
                'raw_selector': clean_sel,
                'body':         body,
                'start':        last_block_end,
                'end':          close_brace,
                'line':         line_num,
                'is_nested':    True,
            })

        last_block_end = close_brace + 1
        pos = close_brace + 1

    return blocks


def _build_line_map(text: str) -> list[int]:
    """Ritorna lista dove line_map[i] = offset inizio riga i (0-based)."""
    lm = [0]
    for idx, ch in enumerate(text):
        if ch == '\n':
            lm.append(idx + 1)
    return lm


def _offset_to_line(line_map: list[int], offset: int) -> int:
    lo, hi = 0, len(line_map) - 1
    while lo < hi:
        mid = (lo + hi + 1) // 2
        if line_map[mid] <= offset:
            lo = mid
        else:
            hi = mid - 1
    return lo + 1  # 1-based


def _find_next_char(text: str, char: str, start: int) -> int:
    idx = text.find(char, start)
    return idx


# ═══════════════════════════════════════════════════════════════════
#  ESTRAZIONE CLASSI USATE DAI SORGENTI
# ═══════════════════════════════════════════════════════════════════

def extract_classes_from_java(src: str) -> set[str]:
    classes: set[str] = set()

    # getStyleClass().add/addAll/setAll(...)
    for m in JAVA_STYLECLASS_RE.finditer(src):
        for cls in re.findall(r'"([^"]+)"', m.group(1)):
            classes.update(cls.split())

    # .remove("class") — anche i rimossi sono "usati"
    for m in JAVA_STYLECLASS_SET_RE.finditer(src):
        classes.update(m.group(1).split())

    # getPseudoClass("name")
    for m in JAVA_PSEUDOCLASS_RE.finditer(src):
        classes.add(m.group(1))

    # Tutte le stringhe kebab-case (conservativo)
    for m in JAVA_STRING_RE.finditer(src):
        classes.add(m.group(1))

    return classes


def extract_classes_from_fxml(src: str) -> set[str]:
    classes: set[str] = set()
    for m in FXML_STYLECLASS_RE.finditer(src):
        classes.update(m.group(1).split())
    return classes


def collect_used_classes(src_files: list[Path], verbose: bool = False) -> set[str]:
    used: set[str] = set()
    for f in src_files:
        try:
            text = f.read_text(encoding='utf-8', errors='ignore')
        except Exception as e:
            print(f"  ⚠️  Impossibile leggere {f}: {e}")
            continue
        if f.suffix.lower() == '.fxml':
            found = extract_classes_from_fxml(text)
        else:
            found = extract_classes_from_java(text)
        if verbose and found:
            print(f"  [{f.name}] → {len(found)} classi")
        used |= found
    return used


# ═══════════════════════════════════════════════════════════════════
#  ANALISI DUPLICATI
# ═══════════════════════════════════════════════════════════════════

def find_duplicates(blocks: list[dict]) -> dict[str, list[int]]:
    """Ritorna i selettori grezzi definiti più volte, con i numeri di riga."""
    seen: dict[str, list[int]] = defaultdict(list)
    for b in blocks:
        if not b['is_nested']:
            # Normalizziamo: strip spazi multipli
            norm = re.sub(r'\s+', ' ', b['raw_selector']).strip()
            seen[norm].append(b['line'])
    return {k: v for k, v in seen.items() if len(v) > 1}


# ═══════════════════════════════════════════════════════════════════
#  RIMOZIONE
# ═══════════════════════════════════════════════════════════════════

def remove_unused_blocks(
    css_path: Path,
    unused_blocks: list[dict],
    backup: bool = True,
) -> None:
    """Riscrive il file CSS senza i blocchi inutilizzati."""
    text = css_path.read_text(encoding='utf-8', errors='ignore')

    if backup:
        bak = css_path.with_suffix('.css.bak')
        bak.write_text(text, encoding='utf-8')
        print(f"     📦 Backup → {bak.name}")

    # Rimuovi dal fondo per preservare gli offset
    sorted_blocks = sorted(unused_blocks, key=lambda b: b['start'], reverse=True)
    for block in sorted_blocks:
        text = text[:block['start']] + text[block['end'] + 1:]

    # Pulizia: riduci righe vuote consecutive a massimo 2
    text = re.sub(r'\n{3,}', '\n\n', text)

    css_path.write_text(text, encoding='utf-8')


# ═══════════════════════════════════════════════════════════════════
#  RACCOLTA FILE
# ═══════════════════════════════════════════════════════════════════

def collect_css_files(root: Path, css_dir: Path | None) -> list[Path]:
    base = css_dir or root
    # Esclude backup e versioni "-clean" generate da tool
    return [
        p for p in sorted(base.rglob('*.css'))
        if '.bak' not in p.name and '-clean' not in p.stem
    ]


def collect_src_files(root: Path, src_dir: Path | None) -> list[Path]:
    base = src_dir or root
    files = []
    for ext in ('*.java', '*.fxml', '*.kt', '*.groovy'):
        files.extend(sorted(base.rglob(ext)))
    return files


# ═══════════════════════════════════════════════════════════════════
#  MAIN
# ═══════════════════════════════════════════════════════════════════

def main() -> None:
    parser = argparse.ArgumentParser(
        description='Trova/rimuove selettori CSS non utilizzati in progetti JavaFX',
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('root', nargs='?', default='.', help='Radice del progetto (default: .)')
    parser.add_argument('--remove',          action='store_true', help='Rimuove i blocchi inutilizzati')
    parser.add_argument('--no-confirm',      action='store_true', help='Rimuove senza chiedere conferma')
    parser.add_argument('--no-backup',       action='store_true', help='Non crea file .bak prima di modificare')
    parser.add_argument('--css-dir',         metavar='PATH',      help='Directory CSS specifica')
    parser.add_argument('--src-dir',         metavar='PATH',      help='Directory sorgente specifica')
    parser.add_argument('--show-duplicates', action='store_true', help='Mostra selettori duplicati')
    parser.add_argument('--dry-run',         action='store_true', help='Solo analisi, nessuna modifica')
    parser.add_argument('--verbose',         action='store_true', help='Mostra dettagli extra')
    args = parser.parse_args()

    root    = Path(args.root).resolve()
    css_dir = Path(args.css_dir).resolve() if args.css_dir else None
    src_dir = Path(args.src_dir).resolve() if args.src_dir else None

    W  = 62
    SEP = '═' * W

    print(f'\n{SEP}')
    print(f'  🔍  find_unused_css.py  —  JavaFX / SceneBuilder')
    print(SEP)
    print(f'  Root    : {root}')
    if css_dir: print(f'  CSS dir : {css_dir}')
    if src_dir: print(f'  Src dir : {src_dir}')
    print()

    # ── 1. Raccogli file ──────────────────────────────────────────
    css_files = collect_css_files(root, css_dir)
    src_files = collect_src_files(root, src_dir)

    if not css_files:
        print('  ⚠️  Nessun file CSS trovato. Controlla il path.')
        sys.exit(1)

    print(f'  📄 File CSS      : {len(css_files)}')
    for f in css_files:
        try:    rel = f.relative_to(root)
        except: rel = f
        print(f'     • {rel}')

    print(f'  ☕ File sorgente : {len(src_files)}')
    print()

    # ── 2. Classi usate nel codice ────────────────────────────────
    print('  ⚙️   Scansione sorgenti...')
    used_classes = collect_used_classes(src_files, verbose=args.verbose)
    print(f'  ✅  Classi referenziate nel codice: {len(used_classes)}')
    print()

    # ── 3. Analisi CSS ────────────────────────────────────────────
    total_unused    = 0
    total_blocks    = 0
    report: list[dict] = []

    for css_path in css_files:
        try:    rel = css_path.relative_to(root)
        except: rel = css_path

        text   = css_path.read_text(encoding='utf-8', errors='ignore')
        blocks = parse_css_blocks(text)
        total_blocks += len(blocks)

        # Filtra blocchi inutilizzati (non-nested, con almeno 1 classe, nessuna usata)
        unused = [
            b for b in blocks
            if not b['is_nested']
            and b['selectors']
            and not any(cls in used_classes for cls in b['selectors'])
        ]

        duplicates = find_duplicates(blocks) if args.show_duplicates else {}

        print(f'  {"─"*58}')
        print(f'  📝  {rel}')
        print(f'      Blocchi totali      : {len(blocks)}')
        print(f'      Blocchi inutilizzati: {len(unused)}')
        if duplicates:
            print(f'      Selettori duplicati : {len(duplicates)}')

        if unused:
            print()
            for b in unused:
                print(f'      ✗  {b["raw_selector"]}  (riga {b["line"]})')
            total_unused += len(unused)

        if duplicates:
            print()
            for sel, lines in sorted(duplicates.items()):
                line_list = ', '.join(map(str, lines))
                print(f'      ⚠  Duplicato: {sel!r}')
                print(f'         → righe: {line_list}')

        report.append({
            'path':       css_path,
            'unused':     unused,
            'duplicates': duplicates,
        })

    print(f'\n  {SEP}')
    pct = (total_unused / total_blocks * 100) if total_blocks else 0
    print(f'  📊  Blocchi totali    : {total_blocks}')
    print(f'  📊  Inutilizzati      : {total_unused}  ({pct:.1f}%)')
    print()

    if total_unused == 0:
        print('  🎉  Nessun CSS inutilizzato trovato!')
        return

    # ── 4. Rimozione ─────────────────────────────────────────────
    if args.dry_run:
        print('  ℹ️   Dry-run attivo: nessun file modificato.')
        return

    if not args.remove:
        print('  ℹ️   Usa --remove per eliminare i blocchi inutilizzati.')
        return

    # Conferma
    if not args.no_confirm:
        print(f'  ❓  Rimuovere {total_unused} blocchi da {len(css_files)} file? [s/N] ', end='')
        ans = input().strip().lower()
        if ans not in ('s', 'si', 'y', 'yes'):
            print('  ↩   Annullato.')
            return

    print(f'\n  🗑️   Rimozione in corso...\n')
    for entry in report:
        if not entry['unused']:
            continue
        try:    rel = entry['path'].relative_to(root)
        except: rel = entry['path']

        print(f'  → {rel}')
        remove_unused_blocks(
            entry['path'],
            entry['unused'],
            backup=not args.no_backup,
        )
        print(f'     Rimossi {len(entry["unused"])} blocchi. ✅')

    if not args.no_backup:
        print('\n  💾  I file originali sono stati salvati con estensione .bak')
    print('\n  ✅  Completato.')


if __name__ == '__main__':
    main()
