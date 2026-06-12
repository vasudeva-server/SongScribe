# Plan: FlatLafKeys → FlatLafKey enum
## Context
`FlatLafKeys` is a generated class of `public static final String` constants. Any `String` can be passed to the `FlatLafProps` getters today, so type errors are caught only at runtime. Converting to an enum enforces a valid key at compile time and removes an entire class of possible runtime failures.

* * *
## Changes
### 1. Template — `scripts/templates/FlatLafKeys.java.template` → `FlatLafKey.java.template`
Replace the class template with an enum template:

```java
// This is an auto generated code. DO NOT MODIFY!
package songscribe.ui;

/**
 * Enum constants for custom SongScribe.* keys defined in FlatLaf.properties.
 * Generated from {@code src/main/resources/songscribe/FlatLaf.properties}.
 */
public enum FlatLafKey {

{{CONSTANTS}}

    private final String key;

    FlatLafKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
```
### 2. Generator — `scripts/generate-flatlaf-keys.groovy`
- Output file: `FlatLafKeys.java` → `FlatLafKey.java` — update the `OUT_FILE` definition and the freshness-check / "Generated …" log lines that name the file.
  
- Template reference: `FlatLafKeys.java.template` → `FlatLafKey.java.template`
  
- Constant format: `public static final String CONST = "val";` → `CONST("val"),` with `;` after last entry:
  
  ```groovy
  def entries = constantToKey.toSorted().collect { constant, key -> "    ${constant}(\"${key}\")" }
  def constants = entries.join(',\n') + ';'
  ```
  
- Audit — update **both** `FlatLafKeys` references, not just the regex, or the lookup never matches, every key is reported dead, and the generator throws:
  
  - the match regex (`-o 'FlatLafKeys\\.[A-Z][A-Z0-9_]+'`) → `FlatLafKey\\.[A-Z][A-Z0-9_]+`
    
  - the `deadKeys` lookup string `"FlatLafKeys.${constant}"` → `"FlatLafKey.${constant}"`
    
- Delete stale generated class: at the **top of the script, before the freshness-check early return**, delete any pre-existing `FlatLafKeys.java` from `OUT_DIR`. It must precede the early return — otherwise an up-to-date run skips generation and leaves the stale file (and its dead class) behind:
  
  ```groovy
  def STALE_FILE = new File(OUT_DIR, 'FlatLafKeys.java')
  if (STALE_FILE.exists()) { STALE_FILE.delete() }
  ```
  
- Delete the old template file `FlatLafKeys.java.template`
  
### 3. `FlatLafProps.java`
- Private `get(String key, Class<T> type)` → `get(FlatLafKey key, Class<T> type)`; use `key.key()` when calling `UIManager.get(...)` and in error messages
  
- All 11 public typed getters: parameter type `String key` → `FlatLafKey key`
  
- Javadoc: `{@link FlatLafKeys}` → `{@link FlatLafKey}`
  
### 4. `UIUtils.java` — `spacingBorder`
`spacingBorder(String flatLafKey)` → `spacingBorder(FlatLafKey flatLafKey)`; passes `flatLafKey` directly to `FlatLafProps.getInsets`.
### 5. `BaseDialog.java`
- `getContentPaddingKey()` return type: `@Nullable String` → `@Nullable FlatLafKey`
  
- `Tab(String paddingKey)` → `Tab(FlatLafKey paddingKey)`
  
- `this(hasButtons() ? FlatLafKeys.DIALOG_STD_BUTTONS_PADDING : ...)` → same with `FlatLafKey.`
  
- All other `FlatLafKeys.XXX` references → `FlatLafKey.XXX`
  
- Import: `FlatLafKeys` → `FlatLafKey`
  
### 6. Call-site files (20 files) — mechanical rename
Every `FlatLafKeys.XXX` → `FlatLafKey.XXX` and `import songscribe.ui.FlatLafKeys` → `import songscribe.ui.FlatLafKey`.

Files in `src/main/`: `SplashWindow.java`, `LyricEditor.java`, `ScorePanel.java`, `ScoreView.java`, `ReportBugDialog.java`, `AboutDialog.java`, `BeatChangeDialog.java`, `WhatsNewDialog.java`, `AnnotationDialog.java`, `HelpDialog.java`, `StandardDialog.java`, `TempoSection.java`, `SongSettingsDialog.java`, `HTMLDialog.java`, `FontDialog.java`, `FontChooser.java`, `PreferencesDialog.java`

Files in `src/test/`: `BaseDialogTabsTest.java`, `BaseDialogCounterTest.java`, `StandardDialogTest.java`
### 7. `FlatLafPropsTest.java`
Both of these tests are already broken on the current branch — they are stale leftovers from before the typed-getter refactor (`af90c98f`), when `get` was a public single-argument method. `get` is now `private static <T> T get(String, Class<T>)`, so the single-arg calls no longer resolve (the compiler reports an arity mismatch; the method is also private and unreachable from the test). This change replaces them with the public typed getters:

- `testGetReturnsTypedValueWhenKeyPresent` — replace `FlatLafProps.get(FlatLafKeys.DIALOG_COMPONENT_VERTICAL_GAP)` with `FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_GAP)`
  
- `testGetThrowsWhenKeyIsAbsent` — **drop this test**. It feeds a non-existent string key to assert a runtime throw; after the enum change a `FlatLafKey` cannot be constructed for an invalid key, so the compile-time guarantee makes the runtime test moot.
  

* * *
## Execution order
Make **all** edits in Changes 1–7 before compiling, and compile only once at the end. Generation runs as part of `compile.sh`, and its audit step couples the generator to the call sites: if the call sites are renamed to `FlatLafKey` while the generator's audit still looks for `FlatLafKeys` (or vice-versa), the audit finds zero references and aborts with "dead key" errors. There is no valid intermediate state to compile, so do **not** run `compile.sh` between steps — the order among the edits themselves does not matter, only edits-first / compile-last.

The stale-file deletion folded into Change 2 removes the leftover `build/generated-sources/songscribe/ui/FlatLafKeys.java` so it cannot survive the rename as a dead class.

* * *
## Verification
```
./scripts/compile.sh   # must succeed (regenerates FlatLafKey.java, no FlatLafKeys refs left)
./scripts/test.sh unit FlatLafPropsTest BaseDialogTabsTest BaseDialogCounterTest StandardDialogTest
```
