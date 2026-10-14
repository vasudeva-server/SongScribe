# User-Facing Strings

Rules for `src/main/resources/songscribe/strings.properties`, the source of every user-facing string.

## Rules

- Never edit the generated `Strings` file; edit `strings.properties` and run `./scripts/compile.sh`.
- Write the file as UTF-8 with literal Unicode characters, never `\uNNNN`.
- Match every key to `[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*`, because an invalid key fails the build.
- Use one English word per key segment and split compound words: `thirty-second` becomes `thirty.second`.
- Use only the abbreviations `dpi`, `pdf`, `svg`, `midi`, `abc` and `px`; ask before introducing another.
- Make the first segment the group, separate groups with blank lines, and insert a new key in alphabetical position within its group.
- Reference a key through its generated constant: the key uppercased with dots to underscores.
- Pass a `Strings.*` constant to `Strings.get`, never a raw string literal.
- Search `strings.properties` for an existing key before adding one, because duplicates such as `button.cancel` and `dialog.button.cancel` already exist.
- Write the literal text `Strings.<CONSTANT>` somewhere under `src/` in the change that adds the key, because the dead-key audit greps for that exact text. Passing the key through a variable is fine while the constant appears literally somewhere. Keys with the prefixes `test.` and `month.` are exempt.
- Delete a key when the code that used it goes, because the build fails with the list of dead keys.

### Dialog prefixes

- Name the strings of an alert `alert.*` with the title `alert.title.<topic>`.
- Name the strings of a confirm `confirm.*` with the title `confirm.title.<topic>`.
- Name the strings of an input prompt `input.*` with the title `input.title.<topic>`.
- Name every string of a `BaseDialog` or `StandardDialog` window `dialog.<name>.*`, with the title `dialog.<name>.title`.
- Name the change report's headings, reasons and locations, and the Messages panel's title and empty-list text, `report.*`.

### Values

- Write a newline in a value as `\n`.
- Escape a literal leading space: `dialog.song.settings.the.first = \ the first`.
- Use HTML in a value only for a styled label, never for a tooltip. Write a tooltip with a heading as two plain-text keys, `tooltip.<topic>` and `tooltip.<topic>.title`, which `UIUtils.setToolTipText` combines.
- Call `Strings.get(key)` for literal text and `Strings.get(key, args...)` for a `MessageFormat` pattern, because only the varargs form does `MessageFormat` processing.
- Write a number inline in the pattern, as `{0,number,0.00}`, never pre-formatted in Java.
- Write a plural as a choice: `{0,choice,0#No notes|1#1 note|1<{0} notes}`.
- Use a curly apostrophe in a pattern value, because a straight `'` quotes literal text and a `{0}` inside it does not expand. If a straight apostrophe is unavoidable, double it, also inside embedded HTML attributes.
