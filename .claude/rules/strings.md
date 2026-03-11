## User-Facing Strings

All user-facing strings must be externalized — never use string literals directly in UI code.

### Source of truth

`src/main/resources/songscribe/strings.properties` — edit this file to add, change, or remove strings.

- Keys are lowercase alphanumeric segments separated by dots: `menu.file`, `error.file.open`
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

### Adding a new string

1. Add the key/value to `strings.properties` in the correct alphabetical position within its group.
2. Run `./scripts/compile.sh` — the Maven plugin regenerates `Strings.java` automatically.
3. Reference the new constant via `Strings.YOUR_NEW_KEY`.
