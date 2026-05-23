### 9A — Renderer infrastructure + note-area geometry

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| ElementRenderer | Strategy interface — no logic | none | — | none | — | — |
| ElementFrame | `hasOverrideElementX()` — NaN vs. finite | unit | `testHasOverrideElementXFalseForNaN`, `testHasOverrideElementXTrueForFiniteValue` | adequate | — | — |
| ElementFrame | `hasPreviewShift()` — negative vs. non-negative index | unit | `testHasPreviewShiftFalseForNegativeIndex`, `testHasPreviewShiftTrueForNonNegativeIndex` | adequate | — | — |
| ElementFrame | `LINE_LEVEL` constant values | unit | `testLineLevelHasNoElementOverrideOrShift` | adequate | — | — |
| ElementFrame | `lineLevelWithPreviewShift()` — copies LINE_LEVEL indices, attaches shift | unit | — | missing | Add unit test: verify currentElementIndex==-1, overrideXSs==NaN, fromIndex/shiftSs match args | ⬜ |
| ElementFrame | `withElement()` — creates per-element frame, inherits preview shift | unit | — | missing | Add unit test: verify element index + override set, previewShift inherited from parent | ⬜ |
| GraphicsState | `save()` + `close()` restore contract (bitmask-gated, per-property) | unit | — | missing | Add unit test with a mocked/real Graphics2D: set properties, enter try-with-resources, modify, confirm restore on close | ⬜ |
| GraphicsState | `Property` enum / `has()` bitmask — no separate logic beyond branching in save/close | none | — | none | — | — |
| RenderContext | Pure interface — no logic | none | — | none | — | — |
| RenderingUtils | `getDecorationColor()` — null line → preview color | unit | `testGetDecorationColorNullLineReturnsPreviewColor` | adequate | — | — |
| RenderingUtils | `getDecorationColor()` — element not in line → preview color | unit | `testGetDecorationColorElementNotInLineReturnsPreviewColor` | adequate | — | — |
| RenderingUtils | `getDecorationColor()` — element in line → `invariants.getElementColor(index)` | unit | `testGetDecorationColorElementInLineReturnsCtxColor` | adequate | — | — |
| RenderingUtils | `getDecorationColor()` fast path — frame has valid element index (≥0) bypasses line scan | unit | — | missing | Add unit test: construct frame with valid elementIndex, verify fast-path color returned without consulting line | ⬜ |
| RenderingUtils | `noteStaffPositionToCoordinateSs()` — trivial delegation to `spToSs` + offset | none | — | none | — | — |
| RenderingUtils | `forEachLedgerLineYSs()` — parity normalization + stepping loop, both above and below staff | unit | — | missing | Add unit tests: positions above/below staff, on-staff (no callback), parity normalization edge cases | ⬜ |
| RenderingUtils | `centeredGlyphX()` — multi-term centering: noteheadCenter + xOffset − bBoxLeft − glyphWidth/2 | unit | — | missing | Add unit test: assert computed X equals expected arithmetic result for known inputs | ⬜ |
| RenderingUtils | `glyphOriginYFromLayoutTop()` — trivial subtraction (layoutTop − bbox.top) | none | — | none | — | — |
| RenderingUtils | `stemCenterXOffsetSs()` — branches on minim vs. black notehead, upper vs. lower | unit | — | missing | Add unit tests: all 4 combinations (minim-up, minim-down, black-up, black-down) | ⬜ |
| RenderingUtils | `layoutYToComponentYSs()` — trivial addition | none | — | none | — | — |
| RenderingUtils | `drawLedgerLine`, `drawBravuraGlyph`, `applyDecorationColor` — pure painting | none | — | none | — | — |
| LineInvariants | `getElementColor()` — not in edit mode → BLACK | unit | `testNotEditModeReturnsBlack` | adequate | — | — |
| LineInvariants | `getElementColor()` — playing note → playing color | unit | `testPlayingElementReturnsPlayingColor` | adequate | — | — |
| LineInvariants | `getElementColor()` — grace note playing → playing color | unit | `testGraceNoteCountsAsPlaying` | adequate | — | — |
| LineInvariants | `getElementColor()` — element in playing tie → playing color | unit | `testElementInPlayingTieReturnsPlayingColor` | adequate | — | — |
| LineInvariants | `getElementColor()` — selected element → selectionColor | unit | `testSelectedElementReturnsSelectionColor` | adequate | — | — |
| LineInvariants | `getElementColor()` — hovered (replaced-element) → REPLACED_ELEMENT_COLOR | unit | — | missing | Add unit test: mockStatic PreviewElementManager to return matching location; verify semi-transparent red returned | ⬜ |
| LineInvariants | `getElementColor()` — default (none of the above) → BLACK | unit | `testDefaultReturnsBlack` | adequate | — | — |
| LineInvariants | `isElementPlaying()` — both primary and grace note | unit | `testIsElementPlayingFalseForUnrelatedIndex`, `testGraceNoteCountsAsPlaying` | adequate | — | — |
| LineInvariants | `isElementInPlayingTie()` — in tie vs. no playing note | unit | `testElementInPlayingTieReturnsPlayingColor`, `testIsElementInPlayingTieFalseWithoutPlayingNote` | adequate | — | — |
| LineInvariants | `getLyricColor()` + span-aware `isLyricSpanPlaying()` — melisma/BEGIN-MIDDLE/tied spans | unit | — | missing | Add unit tests covering: anchor playing, tied anchor, melisma extender carrier playing, BEGIN/MIDDLE continuation, span end boundary, no lyric on element | ⬜ |
| LineInvariants | `getLyricConnectorColor()` — 3 branches (sourceIndex<0, no line, delegate to colorFor) | unit | — | missing | Add unit tests for each branch | ⬜ |
| LineInvariants | `Builder.build()` validation — throws `IllegalStateException` when required fields unset | unit | — | missing | Add unit test: assert `assertThatThrownBy` when any of layoutResult/songLayoutMetrics/lyricRenderMetrics is null | ⬜ |
| LineInvariants | Trivial getters (getSong, getFonts, getCurrentLine, getMiddleLineYSs, getLineIndex, etc.) | none | — | none | — | — |
| NoteArea | Pure data record holder | none | — | none | — | — |
| NoteAreaBuilder | `getOrBuildArea()` cache hit — same instance returned when note unchanged | unit | `testAreaCacheReturnsSameInstanceWhenNoteUnchanged` | adequate | — | — |
| NoteAreaBuilder | `getOrBuildArea()` cache invalidation — all 7 attribute-change cases | unit | `testAreaCacheRebuilds*` (7 tests) | adequate | — | — |
| NoteAreaBuilder | `getOrBuildArea()` cache stable — on-staff position change within same ledger tier | unit | `testAreaCacheRetainsCacheWhenStaffPositionChangesWithinStaff` | adequate | — | — |
| NoteAreaBuilder | `buildNoteArea()` — quarter note (only asserts `isEmpty()==false`) | unit | `testBuildNoteAreaQuarterNoteNoExtras` | inadequate | Strengthen: assert bounds height > 0 and bounds width > 0 (or compare with a known baseline geometry) | ⬜ |
| NoteAreaBuilder | `buildNoteArea()` — with accidental extends left | unit | `testBuildNoteAreaWithAccidentalExtendsLeft` | adequate | — | — |
| NoteAreaBuilder | `buildNoteArea()` — dots extend right (one dot, two dots) | unit | `testBuildNoteAreaWithDotsIsWider`, `testBuildNoteAreaWithTwoDotsIsWiderThanOne` | adequate | — | — |
| NoteAreaBuilder | `buildNoteArea()` — ledger lines above staff extend bounds width | unit | `testBuildNoteAreaWithLedgerLinesAboveStaff` | adequate | — | — |
| NoteAreaBuilder | `buildNoteArea()` — ledger lines below staff (only asserts `isEmpty()==false`) | unit | `testBuildNoteAreaWithLedgerLinesBelowStaff` | inadequate | Strengthen: verify bounds width is wider than note on-staff, mirroring the above-staff test | ⬜ |
| NoteAreaBuilder | `buildNoteArea()` — whole note / half note / grace note noteheads | unit | — | missing | Add tests for SEMIBREVE, MINIM, grace noteType variants (different shape constants are selected) | ⬜ |
| NoteAreaBuilder | `buildNoteArea()` — beamed flag suppression (flag absent when beamed=true) | unit | — | missing | Add test: beamed area max-Y should be smaller than non-beamed (flag suppressed) for a quaver stem-up | ⬜ |
| NoteAreaBuilder | `createOffsetArea()` — contains original, expands bounds | unit | `testCreateOffsetAreaContainsOriginal`, `testCreateOffsetAreaExpandsShape` | adequate | — | — |
| NoteAreaBuilder | `getLedgerLineCount` boundary tests (tested here, belong in StaffElementTest) | unit | `testGetLedgerLineCount*` (3 tests) | redundant | Move to `StaffElementTest`; they test `StaffElement.getLedgerLineCount()`, not `NoteAreaBuilder` | ⬜ |

**Notes.** Rows: 46. Tally: adequate 21, missing 13, inadequate 2, none 9, redundant 1. No dead code found — all public/package-private symbols in scope have active callers in the production tree. Production observations: (1) `GraphicsState.close()` silently skips restoration when a saved value is `null` (e.g., `color`, `font`, `transform`) — this is intentional for rendering hints but means a `save(COLOR)` on a context whose `getColor()` returns `null` will never restore. In practice `Graphics2D` implementations do not return `null` from `getColor()`, but the guard is asymmetric: `CLIP` restores unconditionally while all other properties guard on `!= null`. A future implementor swapping in a custom `Graphics2D` could observe silent no-restore for `COLOR`/`STROKE`/`FONT`/`TRANSFORM`. (2) `NoteAreaBuilder.addAccidentalToArea()` uses `ACCIDENTAL_HEIGHT_SS` (derived from the sharp bbox) as a uniform height for all accidentals, which overestimates the natural bounding area. This is documented as an approximation, but a double-flat is taller than a sharp — so the area may understate the actual footprint for that accidental, potentially letting a glissando endpoint land too close to a double-flat. (3) In `LineInvariants.isLyricSpanPlaying()`, when iterating forward for a STOP/CONTINUE carrier, the loop returns on the first lyric found. If a note has no lyric (`next == null`) it is skipped, but the spanning end index (`spanEnd`) is computed only when a lyric is found. A STOP/CONTINUE carrier at index `i` correctly sets `spanEnd = i`, but a text-bearing lyric at `i` sets `spanEnd = i - 1` even if `i - 1 == anchorIndex`. That means a single-note syllable with no carriers would compute `spanEnd == anchorIndex`, and `playingNoteIndex <= anchorIndex` would have already returned `false` before entering the loop — so the edge case is harmless. However, the early-exit guard `playingNoteIndex <= anchorIndex` discards the case where the same note is both anchor and playing, which is handled higher up by `isElementPlaying(anchorIndex)`. The logic is correct but non-obvious and entirely without test coverage.

