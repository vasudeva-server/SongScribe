## User-Facing Strings

All user-facing strings must be externalized — never use string literals directly in UI code.

### Source of truth

`src/main/resources/songscribe/strings.properties` — edit this file to add, change, or remove strings. The file is UTF-8 encoded; use Unicode characters directly (e.g. `'`, `"`, `é`) rather than `\uNNNN` escapes.

- Keys use lowercase dot-separated segments where **each segment is a single English word** (or a recognized abbreviation/acronym such as `dpi`, `pdf`, `svg`, `midi`, `abc`, `gpl`, `px`). Never mash multiple words into one segment.

```properties
# Good
dialog.composition.settings.reset.to.defaults = Reset to defaults
action.key.signature.change = Key Signature Change...

# Bad — words mashed into segments
dialog.compositionsettings.resettodefaults = Reset to defaults
action.keysignaturechange = Key Signature Change...
```
- Strings are alphabetized within groups (by prefix: `action.*`, `confirm.*`, `dialog.*`, `error.*`, etc.)
- Template strings use `java.text.MessageFormat` syntax: `error.line.width.range=Line width must be between {0} and {1} {2}.`
- Apostrophes in template strings must be doubled: `Can''t open {0}`

### Generated class

A Maven plugin compiles `strings.properties` into `target/generated-sources/songscribe/Strings.java` during the build. **Do not edit `Strings.java` directly** — changes will be overwritten.

The generated class provides:
- A `public static final String` constant per key, named by uppercasing and replacing dots with underscores:
  `error.file.open` → `Strings.ERROR_FILE_OPEN`
- Two lookup methods:

```java
Strings.get(String key)                  // plain string
Strings.get(String key, Object... args)  // MessageFormat template with arguments
```

### Usage

```java
// Plain string
Dialogs.showErrorMessage(parent, Strings.get(Strings.DIALOG_TITLE_FILE_ERROR), Strings.get(Strings.ERROR_FILE_OPEN));

// Template string with arguments
Dialogs.showErrorMessage(parent, Strings.get(Strings.DIALOG_TITLE_LINE_WIDTH_ERROR),
    Strings.get(Strings.ERROR_LINE_WIDTH_RANGE, minWidth, maxWidth, unit));
```

### Removing strings

When removing code that references a `Strings.*` constant, check whether any other references to that constant remain in the codebase. If none remain, **remove the corresponding key from `strings.properties`** as well. Dead keys accumulate quickly and make the file hard to maintain.

### Curly quotes in strings.properties

The `Edit` tool cannot reliably write curly (typographic) quotes and apostrophes (‘’“”). When a string value contains these characters, use `python3` via `Bash` to write or modify the line, using the characters directly:

```bash
python3 -c "
line = 'some.key = It’s a “smart” quote'
# append or use file manipulation to insert the line
"
```

### Adding a new string

1. Add the key/value to `strings.properties` in the correct alphabetical position within its group.
2. Run `./scripts/compile.sh` — the Maven plugin regenerates `Strings.java` automatically.
3. Reference the new constant via `Strings.YOUR_NEW_KEY`.
