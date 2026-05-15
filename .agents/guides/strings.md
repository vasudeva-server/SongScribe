## User-Facing Strings

Source of truth: `src/main/resources/songscribe/strings.properties` — a Java `.properties` file, UTF-8, with literal Unicode characters (never `\uNNNN`).

The Gradle build runs `scripts/generate-strings.groovy`, which validates every key and generates `build/generated-sources/songscribe/Strings.java` — one `public static final String` constant per key. This runs as part of `./scripts/compile.sh`. Never edit the generated file (it lives under `build/`).

### Keys

Each key is validated against `[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*`; an invalid key fails the build. In practice:

- Dot-separated segments. Each segment is lowercase letters/digits and **must start with a letter**.
- One English word per segment — split compound words: `thirty-second` → `action.duration.thirty.second`; `staff space` → `staff.space`.
- Accepted abbreviations: `dpi`, `pdf`, `svg`, `midi`, `abc`, `px`. Ask before introducing others.
- First segment is the group. Groups are separated by blank lines and keys are alphabetized within each group — insert a new key in sorted position, not at the end of the file.
- Constant name = key uppercased, dots → underscores: `error.file.open` → `Strings.ERROR_FILE_OPEN`.

### Dialog prefix taxonomy

| Prefix | For | Title key |
|--------|-----|-----------|
| `alert.*` | `OptionDialogs.showInfoMessage` / `showWarningMessage` / `showErrorMessage` | `alert.title.<topic>` |
| `confirm.*` | `showConfirmDialog`, `showOptionDialog` | `confirm.title.<topic>` |
| `input.*` | `showInputDialog` | `input.title.<topic>` |
| `dialog.*` | `BaseDialog` / `StandardDialog` windows only | `dialog.<topic>.title` |

`alert` / `confirm` / `input` carry only a title plus a message, so `title` sits second:

```properties
alert.conversion.complete       = Conversion complete!
alert.title.conversion.complete = Conversion Complete
```

`dialog.*` namespaces *every* string in a complex dialog (labels, sections, tabs, buttons), so the window title is just one leaf — `title` sits last:

```properties
dialog.song.settings.title     = Song settings
dialog.song.settings.tab.fonts = Fonts
dialog.song.settings.day       = Day:
```

### Property values

This is a Java `.properties` file:

- Newlines in a value: `\n` — e.g. `song.default.attribution = Words and Music\nby Sri Chinmoy`.
- A literal leading space must be escaped: `dialog.song.settings.the.first = \ the first`.
- HTML is allowed and rendered by Swing — used for multi-line tooltips and styled labels: `<html><strong>Tuplet</strong><br>Create or remove tuplet…</html>`.

### MessageFormat

A value is a MessageFormat pattern **only** when read through the varargs `get`. Match the call form to the value:

- `Strings.get(key)` — value is literal text, returned untouched.
- `Strings.get(key, args...)` — value is a MessageFormat pattern.

Pattern syntax:

- Numeric inline: `{0,number,0.00}` — never pre-format the number in Java.
- Choice: `{0,choice,0#No notes|1#1 note|1<{0} notes}`.

**Apostrophe trap.** In a MessageFormat pattern a straight ASCII apostrophe `'` is a metacharacter: it quotes literal text, so a `{0}` inside it silently will not expand. Two fixes, both used in this file:

- Use a curly apostrophe `’` — not special to MessageFormat. This is the real reason values use curly quotes/apostrophes throughout, not just typography.
- If a straight apostrophe is unavoidable, double it: `test.template.apostrophe = Can''t find {0}`. Doubling also applies inside embedded HTML attributes: `style=''color: {0}''`.

This trap only bites the varargs form; `Strings.get(key)` does no MessageFormat processing.

### Writing curly quotes/apostrophes (Claude Code limitation)

The `Edit` / `Write` tools cannot emit curly characters (“ ” ‘ ’). This matters **only when the new or changed value itself contains them** — editing an ASCII-only line works normally even though the file is full of curly characters elsewhere.

When a value needs curly characters, do a *targeted* edit via `Bash` + python3 — modify only the relevant lines, do not retype the whole file:

```bash
python3 - <<'EOF'
path = 'src/main/resources/songscribe/strings.properties'
lines = open(path, encoding='utf-8').read().splitlines(keepends=True)
for i, l in enumerate(lines):
    if l.startswith('confirm.song.empty.title '):
        lines[i] = 'confirm.song.empty.title = The song must have a title. Use “Untitled” or continue editing?\n'
open(path, 'w', encoding='utf-8').write(''.join(lines))
EOF
```

### API

```java
Strings.get(Strings.ERROR_FILE_OPEN)                        // plain
Strings.get(Strings.CONFIRM_SAVE_MODIFIED, song.getTitle()) // MessageFormat
```

Always pass a generated `Strings.*` constant, never a raw string literal.

### Adding and removing keys

- **Before adding**, search `strings.properties` for an existing key — duplicates like `button.cancel` vs `dialog.button.cancel` already exist; don't add more.
- **After adding**, reference the constant — as the literal text `Strings.<CONSTANT>` — somewhere under `src/` in the same change. The build's dead-key audit greps for that exact pattern and fails otherwise. Passing the key through a variable is fine as long as the constant appears literally somewhere (see `EndingConfirms`). Exempt prefixes, allowed to be unreferenced: `test.`, `month.`.
- **After removing** code that used `Strings.*` constants, delete the corresponding keys. `./scripts/compile.sh` fails with the list of dead keys if you forget.
