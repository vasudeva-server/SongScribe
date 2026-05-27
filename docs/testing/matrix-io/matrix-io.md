## 2. `io` (audited 2026-05-21)

Audited all 15 production classes (excl. `package-info`) via four parallel production-first sub-audits: **orchestration & XML**; **element & annotation serialization**; **line & view serialization**; **migration & legacy import**. Read-only; e2e assessed from source only; no `io` behavior warranted e2e (serialization/migration is data-driven logic — prime unit territory). Coverage checked across unit (mirrored + cross-package) and e2e. Five verdicts reclassified from the sub-audits' `wrong-level` (vocabulary reserves that for unit↔e2e mismatches; these are unit tests covered only indirectly).

- [2A. orchestration & XML — `SongIO`, `SongLoader`, `SongLoadResult`, `XML`](2a-orchestration-xml.md)
- [2B. element & annotation serialization — `StaffElementIO`, `AnnotationIO`, `TempoIO`](2b-element-annotation-serialization.md)
- [2C. line & view serialization — `LineIO`, `ViewIO`](2c-line-view-serialization.md)
- [2D. migration subsystem & legacy import — `FormatMigrator`, `MigrationPipeline`, `MigrationContext`, `SongMigration`, `StageId`, `LegacyLyricsImporter`](2d-migration-subsystem-legacy-import.md)

### io — production observations (out of test-audit scope)

Filed as a single tracked GitHub issue ([#407](https://github.com/vasudeva-server/SongScribe/issues/407)) — these are real code observations, not test gaps, so the disposable matrix isn't their only home:

1. **`LineIO` — missing bounds guards.** `createBeamsFromPending`/`createTupletsFromPending` validate index ranges; `createTiesFromPendingPairs`, `createCrescendosFromPending`, `createDiminuendosFromPending`, `createTrillsFromPendingPairs`, `createEndingsFromPendingPairs` do not — they throw `IndexOutOfBoundsException` on truncated/corrupt files. Make uniform or document as fail-loud.
2. **`LineIO` — `parseEndingPairs` asymmetry.** It uniquely `.clear()`s its pending list at entry; all other `parse*` methods accumulate. Document or unify.
3. **`XML` — static mutable `indent`.** `setIndent`/`printIndent` use an unsynchronized static field: a thread-safety hazard in production and a test-isolation hazard (tests that call `setIndent` leak state). Make it a parameter or document not-thread-safe.
4. **`FormatMigrator` — pixel-vs-staff-space unit coupling.** `applyTopPaddingFallback` and `migrateLineLevelOffsets` compute pixel-valued quantities/deltas and assign them to `*Ss` fields, correct only because `migratePixelsToStaffSpace` divides by pps afterward. Tests written in terms of the same formula can't catch a unit mismatch. Verify against `SongIOTest.testTopPaddingFallbackValueReachesSong` and add an explanatory comment about the two-step dependency.
5. **`StaffElementIO` — `lenght` parameter misspelling** in `characters` (cosmetic; compiles and works).
6. **`TempoIO` — `endElement11` has no legacy-duration-name lookup** (only `endElement10` does); a v1.1 file with a legacy name (e.g. `MINIMDOTTED`) throws `IllegalArgumentException` from `Duration.valueOf`. Likely an intentional v1.1 contract, but untested.

### io — summary

Audited all 15 production classes (excl. `package-info`). Dominant patterns to drive remediation:

1. **Serialization *write* paths are the biggest blind spot.** Existing coverage is round-trip-via-`SongIO`, which verifies value preservation but never **conditional emission** (tag omitted when zero/null/empty) or exact serialized format. `XML.escapeXML`, `writeSong`'s conditional fields, `ViewIO.writeView`, and most of `LineIO`'s field/range-element writers are unasserted.
2. **`LineIO` (the largest IO class) has no dedicated test file** — six of seven range-element serializers and the shared `forEachSegment` parser are entirely untested.
3. **Legacy/v1.0 decode paths are dark:** `StaffElementIO`/`TempoIO` `*10` methods, legacy type/duration renames, `AnnotationIO`/`TempoIO` round-trips, and `BeatChange.fromLegacyName` happy paths (echoing the `dom` finding).
4. **Migration is best-covered, but its *per-line* conversions aren't:** `migratePixelsToStaffSpace`/`migrateLineLevelOffsets` bodies run only against empty line lists in tests; several pipeline "effect" tests are no-crash smoke (one mocks away the very call it claims to test).
5. **"Weak-but-green" tests:** tautologies (`ViewIO` `x.equals(x)`), `isNotNull()`-only assertions (`SongLoaderTest`, legacy-tolerance), and indirect round-trips standing in for direct behavioral assertions.
6. **Real code defects surfaced** (see production observations) — most notably the `Ending.Type` round-trip data loss.
