### 2D. migration subsystem & legacy import — `FormatMigrator`, `MigrationPipeline`, `MigrationContext`, `SongMigration`, `StageId`, `LegacyLyricsImporter`

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| FormatMigrator | `migrate` skips when `formatVersion >= 2` | unit | `MigrationPipelineTest.LegacyFormatStage.testDoesNotApplyAtThreshold` (gate via pipeline) | inadequate | the in-`migrate` version guard never asserted directly; call w/ version=2, confirm untouched | ✅ |
| FormatMigrator | `migrate(lines,1)` iterates calling `migrateLineLevelOffsets` per line | unit | `MigrationPipelineTest.LegacyFormatStage.testEffectRunsOnEmptyLines` | inadequate | smoke (empty list); test a line w/ non-zero `tempoChangeYPosPx`, verify `userYOffsetSs` updated | ✅ |
| FormatMigrator | `migrateLineLevelOffsets` — non-zero `tempoChangeYPosPx` → delta to each `TempoChangeAttachment.userYOffsetSs` | unit | — | missing | line+attachment offset → verify delta | ✅ |
| FormatMigrator | `migrateLineLevelOffsets` — `beatChangeYPosPx`≠default → delta to `BeatChangeAttachment.userYOffsetSs` | unit | — | missing | non-default + zero-delta no-op | ✅ |
| FormatMigrator | `migrateLineLevelOffsets` — `firstSecondEndingYPosPx`≠default → delta to `Ending.yPositionSs` | unit | — | missing | write test | ✅ |
| FormatMigrator | `migrateLineLevelOffsets` — `trillYPosPx`≠default → delta to `Trill.yPositionSs` | unit | — | missing | write test | ✅ |
| FormatMigrator | `migrateAnnotationPositions` — below-staff (`yPosPx>0`) → above-staff + userYOffset | unit | — | missing | positive yPosPx → yPosPx=ABOVE, userYOffset += (old−ABOVE) | ✅ |
| FormatMigrator | `migrateAnnotationPositions` — above-staff (`yPosPx<=0`) → unchanged | unit | — | missing | no-op | ✅ |
| FormatMigrator | `migrateElementAttachments` — empty body | none | — | none | no behavior | — |
| FormatMigrator | `migrateAnnotationDynamics` — text matches dynamic symbol → replaced w/ `DynamicAttachment`, annotation removed | unit | `FormatMigratorTest.MigrateAnnotationDynamics` (forte/pianissimo/removal) | adequate | keep | — |
| FormatMigrator | `migrateAnnotationDynamics` — non-matching text → kept, no attachment | unit | `FormatMigratorTest.testAnnotation*NotConverted` (2) | adequate | keep | — |
| FormatMigrator | `migrateAnnotationDynamics` — pre-existing `DynamicAttachment` → annotation removed, no duplicate | unit | `FormatMigratorTest.testAnnotationRemovedWhenDynamicAlreadyExists` | adequate | keep | — |
| FormatMigrator | `migratePixelsToStaffSpace` — `lyricsYPosSs` /= pps per line | unit | `MigrationPipelineTest.PixelsToSsStage.testEffectDividesAllScalarsByPps` (scalars only) | missing | line w/ `lyricsYPosSs` → assert division | ✅ |
| FormatMigrator | `migratePixelsToStaffSpace` — `Tuplet.verticalPositionSs` /= pps (non-zero only) | unit | — | missing | non-zero /= pps; zero no-op | ✅ |
| FormatMigrator | `migratePixelsToStaffSpace` — Hairpin `x1/x2/yShiftSs` /= pps | unit | — | missing | non-zero shifts | ✅ |
| FormatMigrator | `migratePixelsToStaffSpace` — Glissando `x1/x2Translate` /= pps | unit | — | missing | write test | ✅ |
| FormatMigrator | `migratePixelsToStaffSpace` — attachment `userYOffsetSs` /= pps (non-zero) | unit | — | missing | note w/ non-zero offset | ✅ |
| FormatMigrator | `migratePixelsToStaffSpace` — `note.xOffsetPx` reset to 0 unconditionally | unit | — | missing | non-zero → reset to 0 | ✅ |
| FormatMigrator | `migratePixelsToStaffSpace` — `Ending.yPositionSs`/`Trill.yPositionSs` /= pps (non-zero) | unit | — | missing | write test each | ✅ |
| FormatMigrator | `migrateFinalTerminal` — empty list → no-op | unit | `MigrationPipelineTest.FinalTerminalStage` (empty ctx) | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — FINAL_DOUBLE_BARLINE on non-last lines stripped; last line's terminal preserved | unit | `FormatMigratorTest.MigrateFinalTerminal.testFinalBarlineOnNonLastLine*` (2) | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — REPEAT_RIGHT on non-last line untouched | unit | `FormatMigratorTest.testRepeatRightOnNonLastLineIsPreserved` | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — last line ends in replaceable (SINGLE/DOUBLE/REPEAT_LEFT_RIGHT) → replaced w/ FINAL_DOUBLE_BARLINE | unit | `FormatMigratorTest.test*AtEndIsReplaced` (3) | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — last line ends in REPEAT_RIGHT (valid terminal) → no-op | unit | `FormatMigratorTest.testRepeatRightAtEndIsPreservedAsTerminal` | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — last line already FINAL_DOUBLE_BARLINE → no-op | unit | `FormatMigratorTest.testAlreadyEndsInFinalBarlineIsNoOp` | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — non-replaceable non-terminal (REPEAT_LEFT, note) → FINAL_DOUBLE_BARLINE appended | unit | `FormatMigratorTest.testRepeatLeftAtEnd…`/`testNoteAtEnd…` (2) | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — empty last line → FINAL_DOUBLE_BARLINE appended | unit | `FormatMigratorTest.testEmptyLastLineGetsFinalBarlineAppended` | adequate | keep | — |
| FormatMigrator | `migrateFinalTerminal` — misplaced FINAL_DOUBLE_BARLINE not at terminal pos stripped before decision | unit | `FormatMigratorTest.testMisplacedFinalBarline*` (2) | adequate | keep | — |
| MigrationPipeline | `PRE_ASSEMBLY` registration order + stage count | unit | `MigrationPipelineTest.testStageOrderingPreservesScalarInvariant` (ordering only) | inadequate | assert all 6 stages registered in StageId order | ✅ |
| MigrationPipeline | `POST_ASSEMBLY` registration (LEGACY_LYRICS then SYLLABIC_BACKFILL) | unit | — | missing | assert list == `[LEGACY_LYRICS, SYLLABIC_BACKFILL]` | ✅ |
| MigrationPipeline | `versioned` helper — `ctx.isBefore(major,minor)` as `appliesTo` | unit | `MigrationPipelineTest` (implicit) | adequate | keep | — |
| MigrationPipeline | LEGACY_FORMAT gate — applies <2.0, skips ≥2.0 | unit | `MigrationPipelineTest.LegacyFormatStage` (2) | adequate | keep | — |
| MigrationPipeline | LEGACY_FORMAT effect — delegates to `FormatMigrator.migrate` w/ non-empty lines | unit | `MigrationPipelineTest.LegacyFormatStage.testEffectRunsOnEmptyLines` | inadequate | smoke only; non-empty behavior covered by missing `migrateLineLevelOffsets` tests | ✅ |
| MigrationPipeline | ANNOTATION_DYNAMICS gate — applies <2.3, skips ≥2.3 | unit | `MigrationPipelineTest.AnnotationDynamicsStage` (2) | adequate | keep | — |
| MigrationPipeline | ANNOTATION_DYNAMICS effect — delegates to `migrateAnnotationDynamics` | unit | `MigrationPipelineTest.AnnotationDynamicsStage.testEffectRunsOnEmptyLines` | inadequate | smoke only; add wiring test through a line w/ annotation | ✅ |
| MigrationPipeline | FINAL_TERMINAL gate — applies <2.4, skips ≥2.4 | unit | `MigrationPipelineTest.FinalTerminalStage` (2) | adequate | keep | — |
| MigrationPipeline | FINAL_TERMINAL effect — FINAL_DOUBLE_BARLINE appended when last line ends in note | unit | `MigrationPipelineTest.FinalTerminalStage.testEffectAppliesFinalBarline` | adequate | keep | — |
| MigrationPipeline | PIXELS_TO_SS gate — applies <2.1, skips ≥2.1 | unit | `MigrationPipelineTest.PixelsToSsStage` (2) | adequate | keep | — |
| MigrationPipeline | PIXELS_TO_SS effect — four song-level scalars /= pps | unit | `MigrationPipelineTest.PixelsToSsStage.testEffectDividesAllScalarsByPps` | adequate | keep | — |
| MigrationPipeline | PIXELS_TO_SS effect — per-line fields also /= pps | unit | — | missing | covered by missing `migratePixelsToStaffSpace` line-level tests; add integration via `runPreAssembly` w/ non-empty line | ✅ |
| MigrationPipeline | LINE_WIDTH_FIX gate — major=2 AND minor<3 AND `lineWidthSs>=MIN`; else skip (3 negative branches) | unit | `MigrationPipelineTest.LineWidthFixStage` (5) | adequate | keep | — |
| MigrationPipeline | LINE_WIDTH_FIX effect — `lineWidthSs /= pps` | unit | `MigrationPipelineTest.LineWidthFixStage.testEffectDividesLineWidthByPps` | adequate | keep | — |
| MigrationPipeline | TOP_PADDING_FALLBACK gate — applies when `topPaddingSs==0` | unit | `MigrationPipelineTest.TopPaddingFallbackStage` (2) | adequate | keep | — |
| MigrationPipeline | TOP_PADDING_FALLBACK effect — `(2·titleSize + lineCount·attributionSize) − ssToRoundedPx(2.0)` | unit | `MigrationPipelineTest.TopPaddingFallbackStage.testEffectComputesCorrectFallbackValue` (attribution="") | inadequate | attribution="" ⇒ lineCount term never exercised; add non-empty attribution test | ✅ |
| MigrationPipeline | LEGACY_LYRICS gate — `!lyrics.isBlank()` AND `isBefore(2, PER_NOTE_LYRIC_VERSION)` | unit | `MigrationPipelineTest.LegacyLyricsStage` (3) | adequate | keep | — |
| MigrationPipeline | LEGACY_LYRICS effect — delegates to `LegacyLyricsImporter.importLegacyLyrics` | unit | (no direct effect test; indirect via `SongIOTest.testLegacyLyricsBlobPopulatesPerNoteRecords`) | inadequate | add direct effect test asserting lyric records populated | ✅ |
| MigrationPipeline | SYLLABIC_BACKFILL gate — always applies | unit | `MigrationPipelineTest.SyllabicBackfillStage.testAlwaysAppliesRegardlessOfVersion` | adequate | keep | — |
| MigrationPipeline | SYLLABIC_BACKFILL effect — `line.backfillSyllabic()` per line | unit | `MigrationPipelineTest.SyllabicBackfillStage.testEffectRunsOnSongWithNoLines` | inadequate | smoke mocks an empty line list ⇒ call never fires; test a line w/ stale markers → normalized | ✅ |
| MigrationPipeline | `requireSong(ctx)` throws ISE when `ctx.song==null` | unit | — | missing | post-assembly stage `apply()` w/ null song → ISE | ✅ |
| MigrationPipeline | `runPreAssembly` executes applicable stages in order | unit | `MigrationPipelineTest.testPreAssemblyScalarConversion`, `testStageOrderingPreservesScalarInvariant` | adequate | keep | — |
| MigrationPipeline | `runPostAssembly` executes applicable stages | unit | (indirect via `SongIOTest.LegacyMigrationWiring`) | adequate | keep | — |
| MigrationPipeline | stage ordering — PIXELS_TO_SS before LINE_WIDTH_FIX | unit | `MigrationPipelineTest.testStageOrderingPreservesScalarInvariant` | adequate | keep | — |
| MigrationPipeline | `PER_NOTE_LYRIC_VERSION`=6, `LEGACY_LINE_WIDTH_PX_MIN`=400.0 boundaries | unit | `MigrationPipelineTest` (used in gate setup) | adequate | keep | — |
| MigrationContext | `isBefore` — cross-major true; same-major minor< true; at-threshold false; major> false | unit | `MigrationPipelineTest` (multiple stage gate tests) | adequate | keep | — |
| MigrationContext | default field values (empty lines/scalars/strings, null song) | none | — | none | pure data holder | — |
| SongMigration | record accessors | none | — | none | pure record | — |
| StageId | enum constants | none | — | none | compile-time identifier | — |
| LegacyLyricsImporter | blank/empty blob → no lyrics | unit | `LegacyLyricsImporterTest.test*BlobEmitsNothing` (2) | adequate | keep | — |
| LegacyLyricsImporter | more blob lines than song lines → surplus dropped; fewer → surplus lines unset | unit | `LegacyLyricsImporterTest.testMultiLineDoesNotOverrunShorterLines` (+ implicit) | adequate | keep | — |
| LegacyLyricsImporter | more tokens than elements → surplus dropped | unit | `LegacyLyricsImporterTest.testTrailingWordsBeyondElementCountAreDropped` | adequate | keep | — |
| LegacyLyricsImporter | `deriveSyllabic` — 4 quadrants → SINGLE/BEGIN/END/MIDDLE | unit | `LegacyLyricsImporterTest.testDoReMi…`, `testEqualsProducesCompoundWord` | adequate | keep | — |
| LegacyLyricsImporter | single-hyphen `-` → BEGIN/MIDDLE/END chain | unit | `LegacyLyricsImporterTest.testDoReMiProducesThreeSyllables` | adequate | keep | — |
| LegacyLyricsImporter | double-hyphen `--` → compound | unit | `LegacyLyricsImporterTest.testDoubleHyphen*` (2) | adequate | keep | — |
| LegacyLyricsImporter | equals `=` → compound | unit | `LegacyLyricsImporterTest.testEqualsProducesCompoundWord` | adequate | keep | — |
| LegacyLyricsImporter | leading `--` on a line → inWord init, first word MIDDLE/END | unit | `LegacyLyricsImporterTest.testMidWordLineContinuationPrefix` | adequate | keep | — |
| LegacyLyricsImporter | trailing `_` run (extend=START) advances elementIdx by run length | unit | `LegacyLyricsImporterTest.testExtenderWith…`, `testFullCombinedExample` | adequate | keep | — |
| LegacyLyricsImporter | standalone `_` run → elementIdx += runLen, no Lyric | unit | `LegacyLyricsImporterTest.testExtenderWithSpaceSeparatedUnderscores…` | adequate | keep | — |
| LegacyLyricsImporter | `_` run abutting next word → one underscore absorbed (`runLen--`) | unit | `LegacyLyricsImporterTest.testExtenderWith…` ("_garden") | adequate | keep | — |
| LegacyLyricsImporter | trailing `_` run abutting next word → one continuation absorbed | unit | `LegacyLyricsImporterTest.testFullCombinedExample` | adequate | keep | — |
| LegacyLyricsImporter | stray `-`/`=` without preceding word → skipped (no lyric, no advance) | unit | — | missing | blob `- word` → first note gets `word` | ✅ |
| LegacyLyricsImporter | stray `--` without preceding word mid-line → skipped (two chars consumed) | unit | — | missing | blob `-- word` → first note gets `word` | ✅ |
| LegacyLyricsImporter | `isWordChar` boundary (space/tab/`_`/`-`/`=`/`\n` false; ASCII true) | unit | (implicit via all paths) | adequate | no isolated gap given full path coverage | — |
| LegacyLyricsImporter | full combined scenario (extend + compound + multi-syllable) | unit | `LegacyLyricsImporterTest.testFullCombinedExample` | adequate | keep | — |

**2D notes (quality concerns):** The single biggest blind spot is **`FormatMigrator.migratePixelsToStaffSpace`** — the one effect test asserts only the four song-level scalar divisions; the entire per-line body (`lyricsYPosSs`, tuplet vertPos, hairpin shifts, glissando translates, attachment `userYOffsetSs`, `xOffsetPx` reset, ending/trill yPos) is unasserted, so a divisor off-by-one or a missed field passes silently. Second: **`migrateLineLevelOffsets`** (the v1→v2 stage) runs only against empty line lists in the pipeline test, so every per-type offset migration is untested. Three pipeline "effect" tests are no-crash smoke (`testEffectRunsOnEmptyLines` for LEGACY_FORMAT / ANNOTATION_DYNAMICS / SYLLABIC_BACKFILL); the SYLLABIC_BACKFILL one is especially misleading — it mocks a Song returning an empty list, so `backfillSyllabic()` never fires and a deleted `forEach` would still pass. TOP_PADDING_FALLBACK's effect test uses `attribution=""`, leaving the line-count term at 0 and the multi-line branch uncovered. LEGACY_LYRICS has no direct effect test (relies on `SongIOTest`). `requireSong`'s ISE guard is untested. `LegacyLyricsImporter` is otherwise strongly covered — only the stray-marker paths (`-`/`--` without a preceding word) are missing.

