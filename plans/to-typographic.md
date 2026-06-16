# Typographic substitutes (issue #436)

## Context

SongScribe should apply **typographic substitution** — smart (curly) quotes/apostrophes,
smart dashes (`--` → em dash `—`), and ellipsis (`...` → `…`) — to the song's descriptive
text: **title, subtitle, underlyrics, translation, footnotes, and per-note lyrics**, in two
contexts:

1. **During entry** — title/subtitle (in `SongSettingsDialog`) and per-note lyrics (in `LyricEditor`).
2. **When loading a document** — all six fields, read from a `.mssw` file.

(Per-note lyrics are beyond the literal text of issue #436, which lists only the five descriptive
fields; they are included here because they are the same kind of user-typed song text and share the
same normalization need.)

Nothing in the codebase does this today (`StringUtils` has no smart-quote method; `SongSettingsDialog`
only wires preview updaters, not quote conversion).

### Why no quote-direction disambiguation is needed

The only genuinely ambiguous case for smart quotes is a leading single quote: is `'` an opening
quote or an elision apostrophe (`'tis`, `'90s`, `rock 'n' roll`)? A corpus check of all existing
songs found **none** of these patterns: no leading-apostrophe elisions, no `rock 'n' roll`, no
decade contractions. The single quotes that do occur are ordinary contractions/possessives
(`don't`, `God's`) and genuine quotations (`'word'`). The issue's context-aware regex handles those
correctly on its own, so **no balancing pass, word list, or elision pre-pass is required** — the
basic contextual conversion is 100% correct for this corpus. (Garbage-in/garbage-out is accepted
for malformed input.)

### Accepted limitation: always-on dash/ellipsis conversion (GIGO)

Conversion is **always on** (no preference) and runs on every construction, including every load.
That means a body field (underlyrics/translation/footnotes) containing a literal `--`, `...`, or a
URL with `--` is silently rewritten on every load with no opt-out. This is **accepted as
garbage-in/garbage-out** — the same posture as the quote handling above. No corpus pre-check and no
guard is added for this; it is documented here as a known, accepted behavior.

### Design decision (confirmed with user)

The codebase already has a **centralized, idempotent normalization pipeline** with an explicit
"preview == render" parity guarantee. Rather than bolt on separate hooks, add one typographic step
to that pipeline and route **all five fields** through a single seam — `SongMetadata.processText`.

Two policies differ only in whether the pref-gated short-A replacement (`ă/Ă → a/A`) applies:

| Field | Routed via | short-A |
| --- | --- | --- |
| title / subtitle (via `normalizeTitle`) | `processText(text, true)` | yes (unconditional) |
| underLyrics (`setUnderLyrics` + `applyUnderLyrics`) | `processText(text, true)` | yes (unconditional) |
| translation (`setTranslatedLyrics` + `applyTranslatedLyrics`) | `processText(text, false)` | no |
| footnotes (`setFootnotes` + `applyFootnotes`) | `processText(text, false)` | no |
| per-note lyrics (`Lyric` record compact constructor) | `processText(text, true)` | yes (unconditional) |

> The `STRIP_SHORT_A` pref is being removed, so short-A stripping is no longer pref-gated — the
> `stripShortA` parameter alone decides it (see §2). This is a behavior change for title/subtitle/
> underlyrics (previously only stripped when the pref was on) and requires updating the existing
> pref-based tests.

short-A is **Bengali-romanization-oriented**; applying it to a translation (which may be Romanian,
Vietnamese, etc.) would corrupt legitimate `ă` characters. Translations and footnotes therefore pass
`stripShortA = false`. The typographic substitution itself applies to all five fields unconditionally.

```
StringUtils.toTypographic(text)        ← pure substitution: quotes, -{2,}→—, .{3,}→…
                                          (no trim, no short-A, isEmpty guard)

SongMetadata.processText(text, stripShortA)
        = toTypographic(text.trim())
          → if (stripShortA && contains ă/Ă) replace ă→a, Ă→A
          → return

   title / subtitle ──normalizeTitle──┐
   underLyrics ───────────────────────┤── processText(…, true)   typographic + short-A
   per-note lyrics (new Lyric ctor) ──┘
                                       │
   translation (set + apply) ─────────┐
   footnotes   (set + apply) ─────────┴── processText(…, false)  typographic only
```

Confirmed choices: **always on** (no preference); a single public method named `toTypographic` that
does smart quotes, `--` → em dash, **and** `...` → ellipsis; **no third-party library** (the
`lib/smartquotes-1.0.jar` referenced by an earlier draft is **not present** in the repo, isn't wired
into the Gradle build, and is not used).

## Implementation

### 1. `StringUtils.toTypographic(String)` — new public method

File: `src/main/java/songscribe/util/StringUtils.java`

Add `public static String toTypographic(String text)` that performs **pure typographic substitution
only** — it does **not** trim (trim stays in `processText`) and does **not** touch short-A:

- Returns the input unchanged when empty — `isEmpty()` fast-return only, **no null check**. The call
  sites pass non-null (`processText` trims first; existing setters already called bare `.trim()`),
  and the `StringUtils` convention (`capitalizeSentence`, `toKebabCase`) is an `isEmpty()`-only guard.
- Converts straight double quotes to curly quotes using the issue's context-aware regex (opening
  after space/start/punctuation; closing before space/end/punctuation; fallback for any remaining),
  using `“ ”` (U+201C/U+201D).
- Converts straight single quotes/apostrophes using the issue's context-aware regex (opening `‘`
  after space/start/punctuation; closing/apostrophe `’` after a letter, before space/end/punctuation,
  and for the `s/t/ll/ve/re` contraction lookaheads; fallback `’` for any remaining), using
  `‘ ’` (U+2018/U+2019). Contractions (`don't` → `don’t`) and possessives (`God's` → `God’s`) fall
  out of these rules; genuine quotations (`'word'` → `‘word’`) work too. No leading-apostrophe
  special-casing — see "Why no disambiguation" above; add a brief code comment recording that
  rationale.
- Converts runs of two-or-more hyphens to a single em dash: `-{2,}` → `—` (U+2014). **Greedy
  collapse** — `--`, `---`, `----` all become one `—`. A single hyphen is preserved.
- Converts runs of three-or-more dots to a single ellipsis: `\.{3,}` → `…` (U+2026). **Greedy
  collapse** — `...`, `....` all become one `…`. A spaced `. . .` is preserved (not matched).
- Is **idempotent**: only straight quotes and runs of straight `-`/`.` are matched, so already-curly
  text, existing em dashes, and existing ellipses pass through unchanged (required — the pipeline
  runs on every construction; idempotency is a load-path invariant).
- Is **multiline-aware**: underlyrics/translation/footnotes are multi-line, so compile the quote
  patterns with `Pattern.MULTILINE` (or include `\n` in the lookbehind/lookahead classes) so a quote
  opening an interior line is treated as opening.

Follow the file's existing convention: precompiled `static final Pattern` fields per substitution
(like `DIACRITICS_PATTERN`, `MULTIPLE_SPACES_PATTERN`) with `matcher(...).replaceAll(...)` in the
method body, rather than inline `replaceAll` on raw strings.

The regex is adapted from the snippet in issue #436 (`toSmartQuotes`), with four changes: precompiled
fields instead of chained `replaceAll`, `Pattern.MULTILINE` so an interior-line-opening quote is
treated as opening, greedy `-{2,}`/`\.{3,}` for the new em-dash/ellipsis steps, and an `isEmpty()`
guard in place of the issue's null check.

```java
// -- Typographic substitution (issue #436) --

// Smart double quotes. Opening after whitespace / line start / a quote;
// closing before whitespace / line end / closing punctuation; fallback = closing.
//   "hi" → “hi”
private static final Pattern OPENING_DOUBLE_QUOTE_PATTERN =
    Pattern.compile("(?<=\\s|^|\")\"", Pattern.MULTILINE);
private static final Pattern CLOSING_DOUBLE_QUOTE_PATTERN =
    Pattern.compile("\"(?=\\s|$|[.,!?;:\")])", Pattern.MULTILINE);

// Smart single quotes / apostrophes. Opening after whitespace / line start / a
// quote; closing/apostrophe before whitespace / line end / closing punctuation
// or an s/t/ll/ve/re contraction; fallback = apostrophe.
//   'hello' → ‘hello’    don't → don’t    God's → God’s
private static final Pattern OPENING_SINGLE_QUOTE_PATTERN =
    Pattern.compile("(?<=\\s|^|\")'", Pattern.MULTILINE);
private static final Pattern CLOSING_SINGLE_QUOTE_PATTERN =
    Pattern.compile("'(?=\\s|$|[.,!?;:\")]|s\\b|t\\b|ll\\b|ve\\b|re\\b)", Pattern.MULTILINE);

// Greedy collapse: two-or-more hyphens → one em dash; three-or-more dots → one ellipsis.
//   -- / --- / ---- → —      ... / .... → …      (single "-" and spaced ". . ." untouched)
private static final Pattern EM_DASH_PATTERN = Pattern.compile("-{2,}");
private static final Pattern ELLIPSIS_PATTERN = Pattern.compile("\\.{3,}");

private static final String OPENING_DOUBLE_QUOTE = "“";  // U+201C
private static final String CLOSING_DOUBLE_QUOTE = "”";  // U+201D
private static final String OPENING_SINGLE_QUOTE = "‘";  // U+2018
private static final String CLOSING_SINGLE_QUOTE = "’";  // U+2019
private static final String EM_DASH = "—";               // U+2014
private static final String ELLIPSIS = "…";              // U+2026

/**
 * Applies typographic substitution: straight quotes → curly, {@code --} runs →
 * em dash, {@code ...} runs → ellipsis. Pure substitution only — does not trim.
 * Idempotent: only straight quotes and runs of straight {@code -}/{@code .} are
 * matched, so already-curly text and existing em dashes/ellipses pass through.
 */
public static String toTypographic(String text) {
    if (text.isEmpty()) {
        return text;
    }

    // No leading-apostrophe (elision) disambiguation: a corpus check found no
    // 'tis / '90s / rock 'n' roll, so the contextual rules below are exact for
    // this corpus; GIGO accepted for malformed input. (see plan §"Why no disambiguation")
    var result = OPENING_DOUBLE_QUOTE_PATTERN.matcher(text).replaceAll(OPENING_DOUBLE_QUOTE);
    result = CLOSING_DOUBLE_QUOTE_PATTERN.matcher(result).replaceAll(CLOSING_DOUBLE_QUOTE);
    result = result.replace("\"", CLOSING_DOUBLE_QUOTE);  // fallback

    result = OPENING_SINGLE_QUOTE_PATTERN.matcher(result).replaceAll(OPENING_SINGLE_QUOTE);
    result = CLOSING_SINGLE_QUOTE_PATTERN.matcher(result).replaceAll(CLOSING_SINGLE_QUOTE);
    result = result.replace("'", CLOSING_SINGLE_QUOTE);   // fallback

    result = EM_DASH_PATTERN.matcher(result).replaceAll(EM_DASH);
    result = ELLIPSIS_PATTERN.matcher(result).replaceAll(ELLIPSIS);

    return result;
}
```

The replacement strings are literal Unicode characters (no `$`/`\`), so `Matcher.replaceAll` needs no
escaping. The two `.replace(...)` fallbacks are literal-string replacements (not regex), matching the
issue's structure. After one pass no straight `"`/`'` and no 2+ `-`/3+ `.` runs remain, which is what
makes the method idempotent.

Do **not** add the issue's `applyToTextComponent` overload — entry conversion goes through the
normalization pipeline (on commit), not by mutating the live text component.

### 2. Add a `stripShortA` parameter to `processText` and call `toTypographic`

File: `src/main/java/songscribe/dom/SongMetadata.java`

Change `processText(String text)` (line 132) to `processText(String text, boolean stripShortA)` and
restructure it into a **single linear pipeline** so the typographic step always runs and the trim is
never skipped (the current short-A branch returns without trimming — a latent bug this fixes):

```java
static String processText(String text, boolean stripShortA) {
    var result = StringUtils.toTypographic(text.trim());

    if (stripShortA && SHORT_A_PATTERN.matcher(result).find()) {
        result = result.replace("ă", "a").replace("Ă", "A");
    }

    return result;
}
```

- **The `STRIP_SHORT_A` pref gate is dropped** — the pref is being removed, so `stripShortA` (the
  parameter) is now the **sole** gate: `true` always strips short-A, `false` never does. The old
  `Prefs.getBoolean(PrefsKey.STRIP_SHORT_A)` call goes away. The param name now reads literally.
- **Behavior change + test fallout:** previously short-A stripping for title/subtitle/underlyrics
  only happened when the user enabled the pref; it is now unconditional for those fields. The existing
  pref-based tests must be updated or removed — they set the pref to drive the branch:
  `SongMetadataTest.java` (lines ~136–159, 169, 279, 515), `SongTextProcessingTest.java`
  (lines ~48–115), `SongSetterMutationTest.java` (lines ~254–260). Their `Prefs.put/reset(STRIP_SHORT_A)`
  setup becomes meaningless and should be replaced with direct assertions on the new unconditional
  behavior.
- Typographic substitution is unconditional (applies regardless of `stripShortA`).
- Update both existing in-class callers:
  - `normalizeTitle` (line 122) → `processText(StringUtils.collapseMultipleSpaces(StringUtils.stripLinefeeds(text)), true)`
- Update the class-header normalization-pipeline Javadoc (lines 37–52, the `processText = …` line) and
  the `processText` Javadoc (lines 125–131) to describe trim + typographic + `stripShortA`-gated
  short-A and the new `stripShortA` parameter (and to drop the `STRIP_SHORT_A` pref reference).

File: `src/main/java/songscribe/dom/Song.java`

Route the under-lyrics, translation, and footnote setters/apply methods through `processText` with
the appropriate `stripShortA` flag:

- `setUnderLyrics` (line 675) and `applyUnderLyrics` (line 1362): `SongMetadata.processText(text, true)`
  (these already call `processText(text)` today — just add the `true` argument).
- `setTranslatedLyrics` (line 685) and `applyTranslatedLyrics` (line 1370): replace bare `text.trim()`
  with `SongMetadata.processText(text, false)`.
- `setFootnotes` (line 696) and `applyFootnotes` (line 1374): replace bare `text.trim()`
  with `SongMetadata.processText(text, false)`.

`processText` trims internally, so the explicit `.trim()` in the translation/footnote setters is
removed (folded into `processText`).

### 3. Normalize per-note lyric text in the `Lyric` record constructor

File: `src/main/java/songscribe/dom/Lyric.java`

Per-note lyric text is stored as the `text` component of the `Lyric` record. **Every** construction
path converges on the compact constructor (`Lyric.java:54`):

```
ENTRY     LyricEditor.commitInner → StaffElement.setLyricForVerse → new Lyric(...)
LEGACY    LegacyLyricsImporter.importLine → new Lyric(1, word, ...)   [line 206; bypasses setLyricForVerse]
EDIT/FIX  Line.java syllable/extend reconstructions → new Lyric(...)  [7 sites]
MODERN    StaffElementIO parses <note><lyric> → new Lyric(number, text, ...)  [StaffElementIO.java:540]
                                         ▼
                          new Lyric(verse, text, extend, syllabic, compound)   ← single seam
```

`setLyricForVerse` is **bypassed** by the legacy importer, the modern `StaffElementIO` loader, and the
`Line.java` reconstructions — all of which call `new Lyric(...)` directly — so it is **not** a
sufficient seam. Apply normalization in the compact constructor instead:

```java
public Lyric {
    text = SongMetadata.processText(text, true);   // typographic + short-A; idempotent

    var isCarrier = extend == Extend.STOP || extend == Extend.CONTINUE;
    // ... existing carrier/syllabic validation unchanged ...
}
```

- `stripShortA = true` — per-note lyrics are romanized song text like underlyrics.
- **Idempotent**, so the 7 `Line.java` reconstructions (which pass an already-normalized `text()`)
  re-run it harmlessly; no double-conversion.
- **No `--` compound-marker hazard.** `LegacyLyricsImporter` consumes `--` as a delimiter *before*
  `new Lyric(1, word, …)` (line 206 builds from the post-split `word`), so no marker `--` survives into
  a `Lyric.text`; the `--`→`—` rule cannot corrupt compound parsing.
- **Carrier lyrics** have `text = ""`; `processText("")` returns `""`, and normalization never changes
  blank-ness, so the carrier/syllabic validation below it is unaffected. Normalize at the **top** of the
  constructor, before the validation.
- Adds a 6th caller to `SongMetadata.processText` (same `songscribe.dom` package → package-private call
  is fine). Since `processText` no longer reads `Prefs`, it is safe to call during deserialization.

### Note on preview parity

The "preview == render" parity guarantee carries **only title and subtitle** through the
`SongSettingsDialog` preview construction (`SongSettingsDialog.java:1154`) — underlyrics, translation,
and footnotes are **not** fields of `SongMetadata` and are edited/loaded elsewhere, so they do not
appear in that dialog's preview. Their typographic conversion happens on their own setters and on
load. This is by design; no footnote/translation preview is added.

### Files touched

- `src/main/java/songscribe/util/StringUtils.java` — new `toTypographic` + patterns
- `src/main/java/songscribe/dom/SongMetadata.java` — `processText(text, stripShortA)` + Javadoc
- `src/main/java/songscribe/dom/Song.java` — underlyrics/translation/footnote setters + apply methods
- `src/main/java/songscribe/dom/Lyric.java` — normalize `text` in the compact constructor

No changes in `SongIO`/`SongLoader`/`ScoreView`: the descriptive fields load through the same
`SongMetadata` constructor + `applyUnderLyrics`/`applyTranslatedLyrics`/`applyFootnotes`, and per-note
lyrics normalize in the `Lyric` constructor regardless of which load path builds them — all now
normalize typographically. `lib/smartquotes-1.0.jar` is not present and needs no build change.

## Tests

- `src/test/java/songscribe/util/StringUtilsTest.java` — unit tests for `toTypographic`:
  - opening/closing double quotes (`"hi"` → `“hi”`), single-quoted phrase (`'hello'` → `‘hello’`),
    contraction (`don't` → `don’t`), possessive (`God's` → `God’s`).
  - **Greedy multi-run cases**: `--`/`---`/`----` each → a single `—`; `...`/`....` each → a single
    `…`. **Negatives**: a single `-` is preserved; `. . .` (spaced) is unchanged.
  - **Multiline-opening case**: a quote at the start of an interior line, e.g. `"a\n\"hi\""`, asserts
    the interior-line-opening quote becomes an opening curly (verifies the `MULTILINE` flag).
  - empty string returns unchanged.
  - **Idempotency on every case**: for each input above, assert
    `toTypographic(toTypographic(x)).equals(toTypographic(x))` (already-curly text, existing em dash,
    and existing ellipsis pass through unchanged).
- `src/test/java/songscribe/dom/SongMetadataTest.java`:
  - title/subtitle are typographically normalized by the constructor, and idempotent.
  - **short-A × typographic interaction**: assert a title is trimmed **and** typographically
    substituted **and** has `ă→a` applied unconditionally (covers the formerly-missing trim in the old
    short-A branch and the new linear pipeline). No `Prefs.put(STRIP_SHORT_A, …)` setup — the pref gate
    is gone.
  - **Existing pref-based tests** that drove the short-A branch via `Prefs.put/reset(STRIP_SHORT_A)`
    (`SongMetadataTest`, `SongTextProcessingTest`, `SongSetterMutationTest`) must be updated to assert
    the new unconditional behavior, since the pref no longer affects the outcome.
- `src/test/java/songscribe/dom/Song*` (setter/entry path):
  - **Setter path** for translation and footnotes: `setTranslatedLyrics`/`setFootnotes` convert
    typographically (entry path, not just load) and do **not** apply short-A (`stripShortA = false`),
    even on input containing `ă/Ă`.
- `src/test/java/songscribe/dom/LyricTest.java` (per-note lyrics):
  - **Constructor seam**: `new Lyric(1, "don't", Extend.NONE, Syllabic.SINGLE, false)` yields
    `text() == "don’t"`; a syllable with `ă` is short-A stripped (`stripShortA = true`); idempotent on a
    second construction from the result.
  - **Carrier lyric**: `new Lyric(verse, "", Extend.CONTINUE, null, false)` still constructs with
    `text() == ""` (normalization of empty is a no-op; carrier validation unaffected).
  - **No `--` corruption from legacy import**: an integration-style assertion that
    `LegacyLyricsImporter.importLegacyLyrics` over a block using `--` compound markers still produces the
    correct compound syllables (the marker is consumed before `Lyric` construction).
- `src/test/java/songscribe/dom/SongLoadingTest.java` (and/or `SongIOTest`) — load a song whose
  title/underlyrics/translation/footnotes **and per-note lyrics** contain straight quotes and `--`,
  assert the loaded `Song` returns curly/em-dash text for all six fields; assert translation/footnotes
  are **not** short-A stripped on load, while per-note lyrics **are**.

## Verification

1. Compile: `./scripts/compile.sh` (must print SUCCESS; fix any errors before proceeding).
2. Unit tests: `./scripts/test.sh unit StringUtilsTest SongMetadataTest LyricTest SongLoadingTest`
   (after a successful compile).
3. Manual smoke check: `./scripts/run.sh`, open the Song Settings dialog, type a title like
   `It's a "test"... -- really` and confirm the preview shows `It’s a “test”… — really`; type a
   per-note lyric syllable containing a straight apostrophe (e.g. `don't`) and confirm it commits as
   `don’t`; save, reopen, confirm it persists; load an older `.mssw` with straight quotes/`--` in
   underlyrics, translation, footnotes, and per-note lyrics and confirm they render with typographic
   substitutes.
