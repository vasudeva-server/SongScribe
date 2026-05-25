### 1C. element/note core — `StaffElement`, `LineElement`, `NoteBounds`, `AccidentalBounds`, `Beam`, `Tie`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| StaffElement | `setLyricForVerse` truth-table (replace/remove/carrier/throw) | unit | `StaffElementTest` (9) | adequate | keep | — |
| StaffElement | `getLyricForVerse`/`getMainLyric`/`getLyrics` lookup + null | unit | `StaffElementCopyConstructorTest`, `LyricEditorTest` | adequate | keep | — |
| StaffElement | `isEligibleForLyric` — non-rest always; rest only if non-blank lyric for verse | unit | — | missing | write test (3 cases) | ⬜ |
| StaffElement | `getLedgerLineCount` boundary math | unit | `NoteAreaBuilderTest` (3, in ui/renderer) | adequate | keep (correct level, misfiled package) | — |
| StaffElement | `hasLedgerLines` delegates to count>0 | unit | — | missing | write test | ⬜ |
| StaffElement | `getPitch`/`calculatePitch` — MIDI pitch from staff pos + accidental + octave | unit | `GlissandoRendererTest` (3, relative only) | inadequate | add absolute MIDI-value assertions for known notes + accidental table | ✅ |
| StaffElement | `getPitchIndex` — staff pos → 0–6 w/ octave wrap | unit | — | missing | write test for sp=0…±8 | ✅ |
| StaffElement | `findLastAccidental` — inherit from same-position predecessor, else key sig | unit | `TiePitchValidationTest` (fixture, via canToggleTie) | wrong-level | add direct unit test on a 2-note line | ⬜ |
| StaffElement | `getDefaultDurationWithDots` — `DOTTED_DURATION[dotCount]` for 0/1/2 | unit | — | missing | write test (base, 1.5×, 1.75×) | ✅ |
| StaffElement | `getDuration` — fermata extends 1.5× | unit | — | missing | write test w/ and w/o FermataAttachment | ✅ |
| StaffElement | `findMidiDurationOverride` — first articulation's % override else -1 | unit | — | missing | write test (none→-1, staccato→%) | ✅ |
| StaffElement | `setAccidental(null)` clears `isAccidentalInParentheses` | unit | `AccidentalInParensActionTest`, `StaffElementCopyConstructorTest` | inadequate | add: set accidental+parens, `setAccidental(null)`, assert parens false | ⬜ |
| StaffElement | `setAccidentalInParentheses` no-ops when accidental null | unit | `AccidentalInParensActionTest` (indirect) | inadequate | add direct null-accidental test | ⬜ |
| StaffElement | copy ctor `(ElementType, StaffElement)` — 4 note/rest combos + deep-copy isolation | unit | `StaffElementCopyConstructorTest` (6) | adequate | keep | — |
| StaffElement | clone ctor `(StaffElement)` — full-field deep copy | unit | `StaffElementCopyConstructorTest.testCloneCopyConstructorDeepCopiesLyrics` | inadequate | only lyrics isolation checked; add articulation + attachment isolation tests | ⬜ |
| StaffElement | `addArticulation`/`removeArticulation` — wire owner/parent/line, maintain children | unit | `ParentLinePropagationTest` (attachments only) | inadequate | add `removeArticulation` owner-unset + child-removal test | ⬜ |
| StaffElement | `clearArticulations` — unset owner each, remove children | unit | — | missing | write test | ⬜ |
| StaffElement | `clearAttachments` — unset owner each, remove children | unit | — | missing | write test | ⬜ |
| StaffElement | `hasArticulation(type)` | unit | — | missing | write test | ⬜ |
| StaffElement | `setLine` propagates to all attachments + articulations | unit | — | missing | write test | ⬜ |
| LineElement | `getMarginBounds` — origin−margins, size+margins | unit | — | missing | write test | ⬜ |
| LineElement | `collapsedVerticalMarginWith` — CSS max-collapse | unit | — | missing | write test (a>b, a<b, a==b) | ⬜ |
| LineElement | `collapsedHorizontalMarginWith` — CSS max-collapse | unit | — | missing | write test (3 cases) | ⬜ |
| LineElement | `addChild` — set parentElement + parentLine | unit | `ParentLinePropagationTest` (indirect) | adequate | keep | — |
| LineElement | `removeChild` — clear parentElement; ignore non-child | unit | `ParentLinePropagationTest` (indirect) | inadequate | ignore-non-child path untested; add test | ⬜ |
| LineElement | `clearChildren` — clear each parentElement; empty list | unit | — | missing | write test | ⬜ |
| LineElement | `setMarginSs(d)` — uniform all four | unit | — | missing | write test | ⬜ |
| LineElement | `setMarginSs(t,r,b,l)` — CSS shorthand | unit | — | missing | write test | ⬜ |
| NoteBounds | `headOnly` factory — all three bounds equal head bounds | unit | — | missing | write test | ⬜ |
| NoteBounds | `withStem` factory — articulations bounds == stem bounds | unit | — | missing | write test | ⬜ |
| NoteBounds | `getStemSideBounds` stem-up → upper half | unit | — | missing | write test (known geometry) | ⬜ |
| NoteBounds | `getStemSideBounds` stem-down → lower half | unit | — | missing | write test | ⬜ |
| NoteBounds | `getOppositeFromStemBounds` stem-up → lower half | unit | — | missing | write test | ⬜ |
| NoteBounds | `getOppositeFromStemBounds` stem-down → upper half | unit | — | missing | write test | ⬜ |
| NoteBounds | `translate(dx,dy)` — new instance shifted, stemUp preserved | unit | — | missing | write test | ⬜ |
| NoteBounds | `getCenterX`/`getCenterY` — from head bounds (not full) | unit | — | missing | write test (distinct head vs full) | ⬜ |
| NoteBounds | `getTop`/`getBottom`/`getAttachmentTopY`/`getAttachmentBottomY` — from articulations bounds | unit | — | missing | write test | ⬜ |
| AccidentalBounds | pure data record | none | — | none | trivial record | — |
| Beam | `getSpanWidthSs` — `max(1.0, end−anchor)` clamp | unit | — | missing | write test (3 branches) | ⬜ |
| Beam | `getContentHeightSs`/`getContentWidthPx`/`getContentHeightPx` → 0 sentinels | none | — | none | trivial constants | — |
| Tie | `getContentHeightSs` → `TIE_ARC_HEIGHT_SS` | unit | `TieTest.testContentHeightSsMatchesStylesheetConstant` | adequate | keep | — |
| Tie | `getContentHeightPx` → ssToPx of constant | unit | `TieTest.testContentHeightPxIsToPixelsOfSs` | adequate | keep | — |
| Tie | `getSpanWidthSs` — `max(1.0, end−anchor)` clamp | unit | — | missing | write test (3 branches) | ⬜ |
| Tie | `isAbove` — anchor `isUpper()`→true; stem-up→false; null anchor→false | unit | — | missing | write test (3 cases) | ⬜ |
| Tie | creation/removal/persistence round-trip | unit | `TieToggleTest` (2) | adequate | keep | — |

**1C notes (quality concerns):** `getPitch`/`calculatePitch` tested only with **relative** equality (`GlissandoRendererTest`) — a systematic octave/pitch-table offset would pass; absolute MIDI assertions needed. `getLedgerLineCount` adequately tested but the test lives in `ui/renderer` though the logic is pure `dom`. `NoteBounds` stem-side/opposite geometry and `Beam`/`Tie` `getSpanWidthSs` clamp are entirely unasserted. `TieTest` (despite the name) covers only the height constant, not `getSpanWidthSs`/`isAbove`. `StaffElementCopyConstructorTest.testGetMainLyricReturnsFirstLyric` is a mild name-mismatch (contract is "verse-1", test data only has verse-1).

