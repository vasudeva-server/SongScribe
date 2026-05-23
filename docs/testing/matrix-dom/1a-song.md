### 1A. `Song`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| Song | Default ctor: one line w/ FINAL_DOUBLE_BARLINE, not modified, defaults (attribution, key, tempo) | unit | `SongDefaultsTest` (6 methods) | adequate | keep | — |
| Song | `getEffectiveTempo()` returns `new Tempo(120, CROTCHET)` when tempo null | unit | `SongDefaultsTest.testEffectiveTempoFallbackWhenTempoIsNull` | adequate | keep | — |
| Song | `getTempo()` `@Nullable` — can be null after `setTempo(null)` | unit | `SongDefaultsTest.testEffectiveTempoFallbackWhenTempoIsNull` | adequate | keep | — |
| Song | `getTempoAt(line, note)` walks backward to most-recent tempo change | unit | — | missing | write unit test: multi-line per-note `TempoChangeAttachment`; assert result + fallback to getEffectiveTempo | ✅ |
| Song | `hasAnyTempoChange()` true iff any element carries a `TempoChangeAttachment` | unit | — | missing | write unit test (false empty, true after attach) | ✅ |
| Song | `clearTempoIfOrphaned` — clears song tempo when element is first-of-first-line or no per-note changes remain | unit | — | missing | write unit tests for all 3 branches | ✅ |
| Song | `normalizeTitle` — strip LF, collapse spaces, short-ă replacement | unit | — | missing | write unit test asserting all 3 transformations | ✅ |
| Song | `processText` — conditional short-ă strip + always trim | unit | — | missing | write unit test w/ prefs mock, both branches | ✅ |
| Song | `setTitle` normalizes before mutating (no-op if normalized==stored) | unit | `SongSetterMutationTest.testSetTitle*` | adequate | keep (normalized-equals-stored no-op branch still untested — optional case) | — |
| Song | `setPlace/Year/Attribution/Number/Footnotes/BanglaLyrics/TranslatedLyrics` — trim then compare/store | unit | `SongSetterMutationTest` (all pairs) | adequate | keep | — |
| Song | `setUnderLyrics` delegates to `processText` | unit | `SongSetterMutationTest.testSetUnderLyricsPostsMutation` | inadequate | asserts only the mutation record, not the processText transformation; strengthen or add processText test | ✅ |
| Song | `setMonth/Day` — primitive idempotence | unit | `SongSetterMutationTest` | adequate | keep | — |
| Song | `setTempo/DefaultKeyAccidentalCount/DefaultKeyType/UnofficialTranslation` — mutation + no-op idempotence | unit | `SongSetterMutationTest` | adequate | keep | — |
| Song | `mutateMetadata` early-return uses `Objects.equals` (null/null) | unit | `SongSetterMutationTest.testSetTempoSameValuePostsNothing` | adequate | keep | — |
| Song | `setTopPaddingSs(_, true)` — sticky `userSetTopPadding` flag (OR-accumulate) | unit | `SongSetterMutationTest.testSetTopPaddingSsPostsMutation` (only `false`) | missing | write test: `(x,true)` then `(x,false)` → flag stays true | ✅ |
| Song | `setTopPaddingSs` always runs apply block (posts even when value unchanged if setByUser differs) | unit | — | missing | write test: `(same,false)` then `(same,true)` → mutation posted, flag true | ✅ |
| Song | `setAttributionStartYSs/RowHeightAdjustmentSs/LineWidthSs` — no-op idempotence | unit | `SongSetterMutationTest` | adequate | keep | — |
| Song | `getLyricsText` — assemble syllabified text (extend `_`, compound `--`, BEGIN/MIDDLE `-`, SINGLE/END space, line `\n`) | unit | — | missing | write unit test asserting each branch | ✅ |
| Song | `loadFrom(SongData)` — apply all scalars atomically, clear lines, mark not-modified, attach initial tempo | unit | `SongLoadingTest.testLoadingLegacySongDoesNotDirtyDocument` | inadequate | only checks isModified; write test asserting each field mapping from a crafted SongData | ✅ |
| Song | `applyLineDefaults` — default key when count=0/type null; tempo-change Y per first-vs-other line | unit | — | missing | write unit tests for all 4 cases | ✅ |
| Song | `isEmpty()` — no lines→false; all empty→true; any non-empty→false | unit | — | missing | write unit test per variant | ✅ |
| Song | `getLineWidthPx()` delegates to `ScaleContext.ssToRoundedPx` | none | — | none | trivial delegation | — |
| Song | `addLine(i, line)` validates `line.getSong()==this`, throws IAE for foreign line | unit | — | missing | write test asserting IAE for foreign line | ✅ |
| Song | `addLine/removeLine` terminal-invariant maintenance (4 FINAL branches) | unit | `SongLineMaintenanceTest` | adequate (FINAL) | REPEAT_RIGHT carry-over untested → see next row | — |
| Song | `terminalTypeToInstall` — carry outgoing REPEAT_RIGHT to new last line; promote interior REPEAT_RIGHT | unit | — | missing | write unit tests for both REPEAT_RIGHT paths | ✅ |
| Song | `maintainTerminalOnLastLineChange` coalesces element mutations into one bracket | unit | `SongLineMaintenanceTest` | adequate | keep | — |
| Song | `isAutoMaintainedTerminal` — true only last-of-last-line + valid terminal | unit | `LineMutationTest.SelectabilityPredicate` | adequate | keep | — |
| Song | `isInteractable` — false for auto-maintained terminal | unit | `LineMutationTest.SelectabilityPredicate`, `HorizontalAdjustmentTest.SnapToEndSkipped` | adequate | keep | — |
| Song | `currentTerminalType()` — type at last position; throws on empty last line | unit | `FinalBarlineActionEnablementTest`, `BarlineMenuTest` | adequate (happy) | error path (empty last line) untested → add test | — |
| Song | `canReplaceTerminal` — valid terminal AND differs from current | unit | `FinalBarlineActionEnablementTest` (indirect) | inadequate | write direct predicate test (3 cases) | ⬜ |
| Song | `replaceTerminal(type)` — no-op same; replace; throws IAE for non-terminal | unit | `HorizontalAdjustmentTest`, `FinalBarlineActionEnablementTest`, `BarlineMenuTest` | missing (error path) | write test: `replaceTerminal(non-terminal)` throws IAE | ⬜ |
| Song | `newTerminalElement(type)` — throws IAE for non-terminal | unit | — | missing | write test asserting IAE | ⬜ |
| Song | `withModification` — depth balanced on exception; no notify on empty body; body runs once | unit | `SongBracketTest.WithModificationLifecycle` | adequate | keep | — |
| Song | `applyChange` — throws outside bracket; accumulates inside | unit | `SongBracketTest` | adequate | keep | — |
| Song | nested brackets fire single `SongDidChangeNotification` at outermost close | unit | `SongBracketTest.NestedBrackets` | adequate | keep | — |
| Song | `endModification` posts only when accumulated != null | unit | `SongBracketTest.testEmptyBodyDoesNotPostNotification` | adequate | keep | — |
| Song | `withoutMutationTracking` — nested suspend/resume via depth counter | unit | — | missing | write nested-call test | ⬜ |
| Song | `endSuspendMutationTracking` — throws ISE without matching begin | unit | — | missing | write test asserting ISE | ⬜ |
| Song | `documentWasSaved(@Handler)` — sets modified=false | unit | — | missing | write handler test | ⬜ |
| Song | `tempoDidChange(@Handler)` — skip all-null; init when null; clone-before-mutate; emit `MetadataChange(TEMPO)` | unit | — | missing | write 4 handler tests | ⬜ |
| Song | `keySignatureDidChange(@Handler)` — song-level propagates to matching lines only; per-line changes one line | unit | — | missing | write both-branch tests | ⬜ |
| Song | `layoutDidChange(@Handler)` — dispatch to setters for non-null fields; setByUser from `topPaddingSetByUser` | unit | — | missing | write dispatch test | ⬜ |
| Song | `metadataDidChange(@Handler)` — coalesce field mutations into one notification | unit | `SongMetadataDialogFlowTest` | adequate | keep | — |
| Song | modified flag true after first real mutation | unit | `SongLineMaintenanceTest.ModifiedFlag` | adequate | keep | — |
| Song | `postWithModification` ≡ `withModification(post(message))` | none | — | none | trivial delegation | — |
| Song | `Song(SongData)` loading ctor — subscribes, no default line | unit | `SongLoadingTest` (fixture) | inadequate | covered by loadFrom improvement above | ⬜ |
| Song | `newParsingStub()` — stub skips default-ctor setup | none | — | none | internal factory; behavior only via loadFrom | — |

**1A notes (quality concerns):** `getTempoAt` (backward walk across line/note boundaries) and the three `@Handler` methods (`tempoDidChange`, `keySignatureDidChange` with its propagation loop, `layoutDidChange`) have **zero** coverage — the highest-risk gaps. `SongLineMaintenanceTest` never uses `REPEAT_RIGHT` as the outgoing terminal, so both non-default `terminalTypeToInstall` paths are untested. `loadFrom` is only exercised via fixture round-trip, not by a direct field-mapping unit test. `SongDefaultsTest.testDefaultTempo`'s `isNotNull()` is substantive (it reads the tempo's fields afterward) — not a defect.

