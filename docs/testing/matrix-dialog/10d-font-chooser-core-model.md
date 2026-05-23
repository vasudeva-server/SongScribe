### 10D — Font Chooser Core & Model

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| `FontNameComparator` | `compare` delegates to `Font.getName().compareTo()` — ordering by logical name, case-sensitive | unit | none | missing | Write unit test: verify ordering of fonts whose names differ only by case, and that identical names compare as 0 | ⬜ |
| `FontFamily` | `add` accumulates fonts into a `TreeSet` ordered by `FontNameComparator`; `getStyles()` returns them in that order | unit | none | missing | Write unit test: add fonts with names in reverse order; assert `getStyles()` returns them in ascending name order | ⬜ |
| `FontFamily` | `getName()` returns the family name passed to constructor (trivial getter — no logic) | none | — | — | No test warranted | — |
| `FontFamilies` | `add(Font)` groups fonts by family (`font.getFamily()`), creating a new `FontFamily` on first encounter and appending to the existing one thereafter (dedup-by-family) | unit | none | missing | Write unit test: add two fonts with same family, one with different family; assert `size()==2` and each `FontFamily` holds correct fonts | ⬜ |
| `FontFamilies` | `get(String)` returns `@Nullable FontFamily` — null when family absent | unit | none | missing | Write unit test: assert `get` returns the correct `FontFamily` for a known name and `null` for an unknown name | ⬜ |
| `FontFamilies` | `iterator()` iterates over family values; `size()` reflects count (delegation to `TreeMap` — trivial) | none | — | — | No test warranted | — |
| `FontFamilies` | `getInstance()` singleton — holds a static `FontFamilies` built at class-load time from system fonts (not directly testable without env coupling) | none | — | — | No test warranted | — |
| `FontFamiliesFactory` | `create()` filters out fonts whose family name starts with `"."` (macOS hidden-font prefix) | unit | none | missing | Write unit test: supply a controlled font list via `mockStatic(MyFontUtils.class)` that includes dot-prefixed and normal families; assert dot families are excluded from result | ⬜ |
| `FontFamiliesFactory` | `create()` groups remaining fonts by family into `FontFamilies` | unit | none | missing | Covered by the filtering test above if it also asserts grouping; or add a dedicated grouping assertion | ⬜ |
| `FamilyListModel` | `initialize()` lazy-builds `fontFamilyNames` from `FontFamilies`, sorted ascending by natural order | unit | none | missing | Write unit test: construct model backed by a `FontFamilies` containing families in non-alphabetical order; assert `getElementAt` returns them sorted | ⬜ |
| `FamilyListModel` | `getSize()` / `getElementAt(int)` delegate to initialized names (pure Swing `ListModel` wiring after initialization) | none | — | — | No test warranted | — |
| `FamilyListModel` | `findFirst(CharSequence)` — case-insensitive substring search over family names; returns first match or `null` when none | unit | none | missing | Write unit tests: exact match, prefix match, substring match (mixed case), no match → `null` | ⬜ |
| `DefaultFontSelectionModel` | `setSelectedFont` fires `ChangeEvent` when new font differs from current | unit | none | missing | Write unit test: attach a `ChangeListener`, call `setSelectedFont` with a different font, assert listener `stateChanged` called once | ⬜ |
| `DefaultFontSelectionModel` | `setSelectedFont` fires NO event when font equals current | unit | none | missing | Write unit test: attach a `ChangeListener`, call `setSelectedFont` with the same font, assert listener never called | ⬜ |
| `DefaultFontSelectionModel` | `getSelectedFontName` / `getSelectedFontFamily` / `getSelectedFontSize` return correct values from the wrapped `Font` | unit | none | missing | Write unit test: construct model with a known font; assert all three getters return expected values | ⬜ |
| `DefaultFontSelectionModel` | `changeEvent` lazy-initialised (created on first fire, reused thereafter) — implementation detail, no external contract | none | — | — | No test warranted | — |
| `DefaultFontSelectionModel` | `addChangeListener` / `removeChangeListener` / `getChangeListeners` — standard `EventListenerList` wiring | none | — | — | No test warranted | — |
| `FontSelectionModel` | Interface — contract tested via `DefaultFontSelectionModel` (the only impl) | none | — | — | No test warranted | — |
| `FontContainer` | Interface — pure wiring contract; implemented by `FontChooser` (Swing composition) | none | — | — | No test warranted | — |
| `FontChooser` | Swing layout and listener wiring (`initPanes`, `addComponents`, `setSelectionModel`) — pure display wiring, no branching logic | none | — | — | No test warranted | — |
| `FontChooser` | `setSelectedFont` temporarily removes all three `ListSelectionListener`s before updating the model, then re-adds via `initPanes` — cross-component Swing wiring; bug only observable in the real event pipeline | e2e | none | missing | Write e2e test (requires user approval): set a font on `FontChooser`, verify no listener-triggered re-entry occurs and the pane selections reflect the new font | ⬜ |

**Notes**

All high-value behaviors in this subsystem are completely untested. No test file anywhere under `src/test` references any class in `songscribe.ui.dialog.fontchooser` or its `model` sub-package.

Key gaps by priority:

1. **`DefaultFontSelectionModel`** — the state machine (fires vs. no-op on `setSelectedFont`) is the most regress-prone logic and the easiest to unit-test with no Swing dependency beyond constructing a `Font`.
2. **`FamilyListModel.findFirst`** — case-insensitive substring search has clear edge cases (empty string, case mismatch, no match) that are trivially unit-tested with a `FontFamilies` constructed in-test (no singletons involved).
3. **`FamilyListModel` sort order** — the lazy `initialize()` sorts family names; the sort itself is cheap to verify by constructing a `FontFamilies` directly.
4. **`FontFamiliesFactory.create` dot-filter** — the `startsWith(".")` exclusion is platform-specific behaviour (macOS hidden fonts) with no test guard; should be mocked via `mockStatic(MyFontUtils.class)`.
5. **`FontNameComparator`** — the comparator drives the ordering of styles within a `FontFamily` `TreeSet`; a pure two-line method but its comparison contract (case-sensitive, by logical name) is worth pinning.

Production observation (do not fix here): `FontFamilies.INSTANCE` is initialised at class-load time via a static field calling `FontFamiliesFactory.create()` → `MyFontUtils.getAllFonts()`. This makes `FontFamilies.getInstance()` untestable in isolation and means any test that constructs `FamilyListModel` will pull real system fonts from the JVM. Tests for `FamilyListModel` must therefore construct the model with a custom `FontFamilies` instance directly (bypassing the singleton), which requires either widening `FamilyListModel.fontFamilies` to package-private or adding an injectable constructor — a testability gap.

