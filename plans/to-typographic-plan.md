# Sub-plan: Typographic substitutes (issue #436)
**Type:** Sub-plan  
**Parent:** [to-typographic.md](./to-typographic.md) → design source  
**Created:** 2026-06-16  
**Status:** Complete  
**BlockedBy:** —

<!-- The full design, rationale, verbatim regex/code, and corpus analysis live in
to-typographic.md. This file is the phased implementation breakdown of that design.
Read to-typographic.md §1/§2/§3 for the exact code to transcribe. -->

* * *
## Purpose
Apply typographic substitution — smart (curly) quotes/apostrophes, `--` → em dash (`—`), and `...` → ellipsis (`…`) — to all user-typed song text: title, subtitle, underlyrics, translation, footnotes, and per-note lyrics. Conversion is **always on** (no preference) and runs both during entry and on every document load, routed through the existing centralized normalization pipeline (`SongMetadata.processText`).

**Delivers:**

- A new pure-substitution method `StringUtils.toTypographic(String)`.
  
- A `stripShortA` parameter on `SongMetadata.processText`, replacing the `STRIP_SHORT_A` pref gate, so short-A stripping (`ă/Ă → a/A`) is decided by the caller, not a pref.
  
- Routing of all six fields through `processText` with the correct `stripShortA` flag.
  
- Updated existing pref-based tests and new tests covering the seam.
  

**Explicitly does NOT:**

- Remove the `PrefsKey.STRIP_SHORT_A` enum entry, its `Prefs.java` mapping, `defaults.json`, or any related UI. Only the _gating read_ inside `processText` is removed (see Flagged uncertainty 1).
  
- Add any quote-direction disambiguation / elision handling (corpus check found none needed — see to-typographic.md "Why no disambiguation").
  
- Add a preference, a footnote/translation preview, or the issue's `applyToTextComponent` overload.
  
## Implementation Approach
One pure function (`toTypographic`) does the substitution; one pipeline seam (`processText`) trims, calls `toTypographic`, then optionally strips short-A; every field's setter/apply/constructor routes through that seam. Because `toTypographic` and `processText` are idempotent, running them on every construction (including every load and the 7 `Line.java` lyric reconstructions) is harmless.
### Decomposition rationale
The conceptual design (regex, idempotency, multiline, GIGO posture) is fully resolved in to-typographic.md and the code is given verbatim, so almost every phase is a **mechanical transcription** suited to Sonnet. Phases are split by file and by compile/test boundary:

- Production code is split into source-only phases (gated by `./scripts/compile.sh`, which compiles `src/main` only) so they stay green even while existing tests are temporarily stale.
  
- Test work is isolated into its own phases (gated by `./scripts/test.sh`): one phase to repair the existing pref-based tests broken by the behavior change, and separate phases for the new tests grouped by subsystem.
  
### Key code touchpoints
- `src/main/java/songscribe/util/StringUtils.java` — add patterns + `toTypographic` (mirror existing `DIACRITICS_PATTERN` / `MULTIPLE_SPACES_PATTERN` precompiled-field convention). Verbatim code: to-typographic.md §1.
  
- `src/main/java/songscribe/dom/SongMetadata.java` — `processText` at line 132, `normalizeTitle` caller at line 122, class-header pipeline Javadoc lines ~37–52, method Javadoc lines ~125–131. Drop the `Prefs.getBoolean(PrefsKey.STRIP_SHORT_A)` read at line 133. Verbatim code: to-typographic.md §2.
  
- `src/main/java/songscribe/dom/Song.java` — `setUnderLyrics` (~~676) +~~ `applyUnderLyrics` ~~(~~1363) add `true`; `setTranslatedLyrics`/`applyTranslatedLyrics` and `setFootnotes`/`applyFootnotes` replace bare `text.trim()` with `processText(text, false)`.
  
- `src/main/java/songscribe/dom/Lyric.java` — compact constructor (~line 54), normalize `text` at the **top**, before carrier/syllabic validation. Verbatim code: to-typographic.md §3.
  
- Tests: `StringUtilsTest`, `SongMetadataTest`, `SongTextProcessingTest`, `SongSetterMutationTest`, `LyricTest`, `SongLoadingTest`.
  

> Line numbers may have drifted. Locate symbols with serena `jet_brains_find_symbol` before editing rather than trusting the numbers above.
### Flagged uncertainties (resolve during implementation)
1. **Scope of "STRIP_SHORT_A pref removal."** Grep confirms the only _gating read_ is `SongMetadata.java:133`; `Prefs.java`/`PrefsKey.java` merely declare the key, and a separate `AUTO_SAVE_AFTER_STRIP_SHORT_A` key exists. **Recommended resolution:** remove only the read inside `processText`; leave the enum/pref infrastructure intact. After Phase 2, run `rg STRIP_SHORT_A src/main` to confirm nothing else reads it for gating and that nothing fails to compile. Full pref deletion, if wanted, is a separate follow-up.
  
2. **Song.java translation/footnote line numbers** (`setTranslatedLyrics` ~685, `applyTranslatedLyrics` ~1370, `setFootnotes` ~696, `applyFootnotes` ~1374). Verify each via serena before editing; confirm they currently call bare `text.trim()`.
  
3. **Multiline opening quote.** The given quote patterns use `(?<=\s|^|")` with `Pattern.MULTILINE` so `^` matches an interior line start. Phase 5's `"a\n\"hi\""` test verifies this; if it fails, the `MULTILINE` flag or a `\n` in the lookbehind class is missing.
  
## Dependencies
- **Internal:** Phase 2 depends on Phase 1 (calls `toTypographic`). Phases 3, 4, 6 depend on Phase 2 (new `processText` signature / behavior change). Phase 7 depends on Phases 2 and 3. Phase 5 depends only on Phase 1.
  
- **External:** none. `lib/smartquotes-1.0.jar` is not present and no build change is needed.
  
- **Must not regress:** the "preview == render" parity guarantee (title/subtitle in `SongSettingsDialog`); legacy `--` compound-marker parsing in `LegacyLyricsImporter` (the marker is consumed before `new Lyric(...)`, so the `--`→`—` rule cannot corrupt it — Phase 7 asserts this); carrier-lyric blank-ness (`processText("") == ""`).
  
## Plan
| Phase | Description | Status | Recommended model |
| --- | --- | --- | --- |
| 1   | [StringUtils.toTypographic](#-phase-1-stringutilstotypographic) | ✅ Complete | Sonnet 4.6, low |
| 2   | [SongMetadata and Song routing](#-phase-2-songmetadata-and-song-routing) | ✅ Complete | Sonnet 4.6, low |
| 3   | [Lyric constructor normalization](#-phase-3-lyric-constructor-normalization) | ✅ Complete | Sonnet 4.6, low |
| 4   | [Update existing pref-based tests](#-phase-4-update-existing-pref-based-tests) | ✅ Complete | Sonnet 4.6, medium |
| 5   | [New toTypographic unit tests](#-phase-5-new-totypographic-unit-tests) | ✅ Complete | Sonnet 4.6, low |
| 6   | [New metadata and setter tests](#-phase-6-new-metadata-and-setter-tests) | ✅ Complete | Sonnet 4.6, medium |
| 7   | [New lyric and load tests](#-phase-7-new-lyric-and-load-tests) | ✅ Complete | Sonnet 4.6, medium |

* * *
## ✅ Phase 1: StringUtils.toTypographic
**Status:** Complete  
**BlockedBy:** —  
**Recommended model/effort:** Sonnet 4.6, low — pure transcription of verbatim code into the file's existing precompiled-pattern convention; no design decisions.
### Tasks
1. In `src/main/java/songscribe/util/StringUtils.java`, add the six replacement-string constants (`OPENING/CLOSING_DOUBLE_QUOTE`, `OPENING/CLOSING_SINGLE_QUOTE`, `EM_DASH`, `ELLIPSIS`) and the six precompiled `static final Pattern` fields exactly as in to-typographic.md §1, matching the placement/style of `DIACRITICS_PATTERN` and `MULTIPLE_SPACES_PATTERN`.
  
2. Add `public static String toTypographic(String text)` with the verbatim body from to-typographic.md §1 (`isEmpty()` guard only — no null check; double-quote pass + fallback, single-quote pass + fallback, em-dash, ellipsis).
  
3. Add the brief code comment recording the "no leading-apostrophe disambiguation" rationale (corpus check), per the design.
  
4. Confirm `java.util.regex.Pattern` is already imported (it is used by existing patterns); add the import only if missing.
  
5. Gate: run `./scripts/compile.sh` and confirm **SUCCESS**.
  

* * *
## ✅ Phase 2: SongMetadata and Song routing
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Sonnet 4.6, low — mechanical signature change + routing, all code given verbatim; `./scripts/compile.sh` (main only) stays green even though existing tests go temporarily stale (repaired in Phase 4).
### Tasks
1. In `SongMetadata.java`, change `processText(String text)` (line ~132) to `processText(String text, boolean stripShortA)` and replace the body with the linear pipeline from to-typographic.md §2 (`toTypographic(text.trim())`, then `stripShortA && SHORT_A_PATTERN.find()` gate). **Delete** the `Prefs.getBoolean(PrefsKey.STRIP_SHORT_A)` read (line ~133); the parameter is now the sole gate.
  
2. Update the `normalizeTitle` caller (line ~122) to pass `true`: `processText(StringUtils.collapseMultipleSpaces(StringUtils.stripLinefeeds(text)), true)`.
  
3. Update the SongMetadata Javadoc — class-header pipeline lines (~~37–52) and the~~ `processText` ~~method Javadoc (~~125–131) — to describe trim + typographic + `stripShortA`-gated short-A and the new parameter; drop all `STRIP_SHORT_A` pref references.
  
4. In `Song.java`, add the `true` argument to `setUnderLyrics` (~~676) and~~ `applyUnderLyrics` ~~(~~1363): `SongMetadata.processText(text, true)`.
  
5. In `Song.java`, replace bare `text.trim()` with `SongMetadata.processText(text, false)` in `setTranslatedLyrics`, `applyTranslatedLyrics`, `setFootnotes`, and `applyFootnotes` (verify locations per Flagged uncertainty 2; `processText` trims internally so the explicit `.trim()` is removed).
  
6. Gate: run `./scripts/compile.sh` (**SUCCESS**), then `rg STRIP_SHORT_A src/main` to confirm no remaining gating read (Flagged uncertainty 1).
  

* * *
## ✅ Phase 3: Lyric constructor normalization
**Status:** Complete  
**BlockedBy:** 2  
**Recommended model/effort:** Sonnet 4.6, low — two-line insertion in the compact constructor, code given verbatim.
### Tasks
1. In `src/main/java/songscribe/dom/Lyric.java`, at the **top** of the compact constructor (~line 54), before the existing carrier/syllabic validation, add `text = SongMetadata.processText(text, true);` per to-typographic.md §3.
  
2. Add the explanatory comment (typographic + short-A; idempotent so the 7 `Line.java` reconstructions re-run it harmlessly; carrier lyrics with `text = ""` are unaffected).
  
3. Gate: run `./scripts/compile.sh` and confirm **SUCCESS**.
  

* * *
## ✅ Phase 4: Update existing pref-based tests
**Status:** Complete  
**BlockedBy:** 2  
**Recommended model/effort:** Sonnet 4.6, medium — mechanical but requires reading each existing assertion and rewriting it for the now-unconditional behavior; the `Prefs.put/reset(STRIP_SHORT_A)` setup becomes meaningless.
### Tasks
1. `SongMetadataTest.java`: remove the `Prefs.put/reset(PrefsKey.STRIP_SHORT_A)` setup (lines ~~51, 136, 145, 155, 169, 279, 515) and rewrite the affected assertions (~~136–159, 168–169, 279, 509–523) to expect **unconditional** short-A stripping for title/subtitle, plus the now-always-applied trim.
  
2. `SongTextProcessingTest.java`: remove the pref toggling (lines ~~48–49, 68–69, 90–115) and rewrite the three-branch~~ `processText` ~~tests to assert the single unconditional path (trim + typographic + short-A); update the explanatory comments (~~34, 59–64, 87).
  
3. `SongSetterMutationTest.java`: update the `setUnderLyrics` short-A comment and assertion (~254–260) to reflect that short-A stripping is now unconditional.
  
4. Sweep these three files for any remaining `STRIP_SHORT_A` references or stale Javadoc describing the old pref-gated behavior; update or remove them.
  
5. Gate: `./scripts/compile.sh` (**SUCCESS**), then `./scripts/test.sh unit SongMetadataTest SongTextProcessingTest SongSetterMutationTest` — all green.
  

* * *
## ✅ Phase 5: New toTypographic unit tests
**Status:** Complete  
**BlockedBy:** 1  
**Recommended model/effort:** Sonnet 4.6, low — straightforward assertion writing against a pure function; may run in parallel with Phases 2–4.
### Tasks
1. In `src/test/java/songscribe/util/StringUtilsTest.java`, add quote cases: `"hi"` → `“hi”`, `'hello'` → `‘hello’`, `don't` → `don’t`, `God's` → `God’s`.
  
2. Add greedy dash/ellipsis cases (`--`/`---`/`----` → single `—`; `...`/`....` → single `…`) and the **negatives**: a single `-` is preserved, spaced `. . .` is unchanged.
  
3. Add the multiline-opening case (`"a\n\"hi\""` → interior-line quote becomes opening curly) verifying the `MULTILINE` flag (Flagged uncertainty 3), and the empty-string pass-through.
  
4. Add idempotency assertions for every case: `toTypographic(toTypographic(x)).equals(toTypographic(x))` (already-curly text, existing em dash, existing ellipsis unchanged).
  
5. Gate: `./scripts/compile.sh` (**SUCCESS**), then `./scripts/test.sh unit StringUtilsTest` — green.
  

* * *
## ✅ Phase 6: New metadata and setter tests
**Status:** Complete  
**BlockedBy:** 2  
**Recommended model/effort:** Sonnet 4.6, medium — exercises the entry path through `SongMetadata` and the translation/footnote setters; asserts the short-A asymmetry.
### Tasks
1. `SongMetadataTest.java`: assert title/subtitle are typographically normalized by the constructor and idempotent.
  
2. `SongMetadataTest.java`: assert the short-A × typographic interaction — a title is trimmed **and** typographically substituted **and** has `ă→a` applied unconditionally (no `Prefs.put(STRIP_SHORT_A, …)` setup; covers the formerly-missing trim in the old short-A branch).
  
3. Setter-path tests (in `SongMetadataTest` or `SongSetterMutationTest`, matching the existing home of setter tests): `setTranslatedLyrics`/`setFootnotes` convert typographically on entry and do **not** apply short-A even for input containing `ă/Ă` (`stripShortA = false`).
  
4. Gate: `./scripts/compile.sh` (**SUCCESS**), then `./scripts/test.sh unit SongMetadataTest SongSetterMutationTest` — green.
  

* * *
## ✅ Phase 7: New lyric and load tests
**Status:** Complete  
**BlockedBy:** 2, 3  
**Recommended model/effort:** Sonnet 4.6, medium — the legacy-import `--` integration assertion and the six-field load assertion need care to set up real fixtures.
### Tasks
1. `LyricTest.java`: constructor seam — `new Lyric(1, "don't", Extend.NONE, Syllabic.SINGLE, false)` yields `text() == "don’t"`; a syllable containing `ă` is short-A stripped; idempotent on a second construction from the result.
  
2. `LyricTest.java`: carrier lyric — `new Lyric(verse, "", Extend.CONTINUE, null, false)` still constructs with `text() == ""` (empty normalization is a no-op; carrier validation unaffected).
  
3. `LyricTest.java`: legacy import — assert `LegacyLyricsImporter.importLegacyLyrics` over a block using `--` compound markers still produces the correct compound syllables (marker consumed before `Lyric` construction; the `--`→`—` rule must not corrupt it).
  
4. `SongLoadingTest.java`: load a song whose title, underlyrics, translation, footnotes, **and** per-note lyrics contain straight quotes and `--`; assert all six fields load with curly/em-dash text; assert translation/footnotes are **not** short-A stripped on load while per-note lyrics **are**.
  
5. Gate: `./scripts/compile.sh` (**SUCCESS**), then `./scripts/test.sh unit LyricTest SongLoadingTest` — green.
  

* * *
## Verification (whole sub-plan)
1. **Compile:** `./scripts/compile.sh` prints **SUCCESS**.
  
2. **Unit tests:**`./scripts/test.sh unit StringUtilsTest SongMetadataTest SongTextProcessingTest SongSetterMutationTest LyricTest SongLoadingTest` — all green.
  
3. **No stale pref gating:** `rg STRIP_SHORT_A src/main` shows no gating read inside `processText` (only declarations, per Flagged uncertainty 1).
  
4. **Manual smoke check** (`./scripts/run.sh`):
  
  - Song Settings dialog: type `It's a "test"... -- really`; preview shows `It’s a “test”… — really`.
    
  - Per-note lyric: type a syllable `don't`; it commits as `don’t`.
    
  - Save, reopen; confirm the substitutes persist.
    
  - Load an older `.mssw` with straight quotes/`--` in underlyrics, translation, footnotes, and per-note lyrics; confirm all render with typographic substitutes.
