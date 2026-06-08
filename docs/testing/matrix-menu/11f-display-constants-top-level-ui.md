### 11F — Display & Constants (top-level `ui`)

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| `KeySignatureDisplay` | `tonicFor`: returns correct tonic string for each SHARPS key (0–7) | unit | none | missing | Test all 8 SHARPS entries against `SHARP_TONICS` table | ✅ |
| `KeySignatureDisplay` | `tonicFor`: returns correct tonic string for each FLATS key (0–7) | unit | none | missing | Test all 8 FLATS entries against `FLAT_TONICS` table | ✅ |
| `KeySignatureDisplay` | `suffixFor`: returns empty string when `KeyType.NONE` or count == 0 | unit | none | missing | Verify both `NONE`-type and zero-count paths return `""` | ✅ |
| `KeySignatureDisplay` | `suffixFor`: returns non-empty suffix containing count for SHARPS | unit | none | missing | Check suffix for SHARPS count > 0 contains the count and right plural form | ✅ |
| `KeySignatureDisplay` | `suffixFor`: returns non-empty suffix containing count for FLATS | unit | none | missing | Check suffix for FLATS count > 0 contains the count and right plural form | ✅ |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for FLATS count < 2, true for count >= 2 | unit | none | missing | Boundary at `MIN_FLAT_COUNT_WITH_ACCIDENTAL` = 2 | ✅ |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for SHARPS count < 6, true for count >= 6 | unit | none | missing | Boundary at `MIN_SHARP_COUNT_WITH_ACCIDENTAL` = 6 | ✅ |
| `KeySignatureDisplay` | `tonicHasAccidental`: returns false for `KeyType.NONE` regardless of count | unit | none | missing | NONE branch must return false even with a nonzero count | ✅ |
| `KeySignatureDisplay` | `getDisplayName` with count == 0 / NONE type: returns `AttributedString` over empty string | unit | none | missing | Empty-string guard path (lines 61–63) | ✅ |
| `KeySignatureDisplay` | `getDisplayName` with a key that has NO tonic accidental: applies single label font only | unit | none | missing | E.g. SHARPS/1 (G major) — no secondary font attribute ranges | ✅ |
| `KeySignatureDisplay` | `getDisplayName` with a key that HAS a tonic accidental: applies letter-gap tracking + glyph font at correct indices | unit | none | missing | E.g. FLATS/3 (E♭ major) — verify font attribute ranges on the correct character positions | ✅ |
| `Constants` | All fields are pure compile-time string/value constants (no logic) | none | none | none | Pure constants holder — no testable behavior | — |
| `Control` | `MOUSE.getDescription()` returns the string for `ACTION_CONTROL_MOUSE` | unit | none | missing | Needs `installFlatLafDefaults`; assert description is non-blank and matches Strings key | ⬜ |
| `Control` | `KEYBOARD.getDescription()` returns the string for `ACTION_CONTROL_KEYBOARD` | unit | none | missing | Parallel to MOUSE case | ⬜ |
| `Mode` | `isAdjustmentMode()` returns true for `ADJUSTMENT` and `VERTICAL_ADJUSTMENT` | unit | none | missing | Both adjustment variants must satisfy predicate | ⬜ |
| `Mode` | `isAdjustmentMode()` returns false for `SELECT` and `EDIT` | unit | none | missing | Non-adjustment variants must not satisfy predicate | ⬜ |
| `FlatLafProps` | `get`: throws `RuntimeError` when key is absent from UIManager | unit | none | missing | Set up a mock UIManager or install FlatLaf without the key; assert exit is called | ⬜ |
| `FlatLafProps` | `get`: returns typed value when key is present | unit | none | missing | Install a known property; assert returned value equals expected with correct type | ⬜ |

**Notes:**

`KeySignatureDisplay` is the highest-risk gap. It contains two parallel lookup tables (`FLAT_TONICS`, `SHARP_TONICS`), two threshold constants (`MIN_FLAT_COUNT_WITH_ACCIDENTAL` = 2, `MIN_SHARP_COUNT_WITH_ACCIDENTAL` = 6), and `AttributedString` font-attribute range logic — all pure computation with zero test coverage. An off-by-one in either threshold or a wrong glyph index in the accidental-font assignment would be invisible until the key-signature picker renders incorrectly on screen. `tonicFor` and `tonicHasAccidental` are `private` static methods, but they are fully exercisable through the public `getDisplayName` method — the private helpers are the real test targets, accessed indirectly. The `getDisplayName` tests that inspect `AttributedString` attribute ranges will need `installFlatLafDefaults()` (from `UnitTest`) because the method calls `MyFontUtils.getUIFont("Label.font")` and `RenderingUtils.getMusicFont()`.

`Mode.isAdjustmentMode()` is used in at least four production call sites across `LineComponent`, `ModeCycleButton`, `UIAction`, and `CycleModeAction`, yet has no direct unit test. The logic is a two-constant OR (`this == ADJUSTMENT || this == VERTICAL_ADJUSTMENT`) and is trivially testable; omitting a test means the method could silently be broken by an enum refactor that renames or adds values. `Control.getDescription()` likewise dispatches a `switch` over two constants to `Strings.get()`; a straightforward two-case test suffices.

`Constants` is a pure string-constants holder (`none`). `FlatLafProps` contains a single method with real logic — a null guard and a typed unchecked cast — which warrants two unit tests. The class is referenced across 66 production call sites, so silent misbehavior (wrong null-check path, or a cast exception from a wrong witness) would be broadly impactful. The missing-key throw path in particular is untested. `FlatLafProps` is not a constants holder in the rubric sense: it has a method body with branching, so `none` would be wrong.

**Tally:** 18 rows — 0 adequate · 17 missing · 0 inadequate · 0 wrong-level · 1 none · 0 redundant.

**Dead code:** `Constants.ACCELERATOR_KEYS` and `Constants.SONG_SCRIBE_JAR` have zero references outside their own definition file in both `src/main` and `src/test`.

**Production observations:** `Constants.NON_BREAKING_HYPHEN` is assigned `Character.toString('­')`, which is U+00AD SOFT HYPHEN — a zero-width formatting character that browsers and many renderers treat as invisible. The true NON-BREAKING HYPHEN is U+2011. This naming/value mismatch may cause ABC export (`ExportABCAction`) to silently fail to replace what it believes are non-breaking hyphens in lyric syllables, since any lyrics actually containing U+2011 would not match the constant. Whether lyrics in practice ever contain U+00AD vs U+2011 determines the real-world impact.

