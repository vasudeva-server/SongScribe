# Sub-plan: Phase 1 — Conversion Scaffold + Round-Trip Harness

**Type:** Sub-plan  <br>
**Parent:** [master-plan.md](./master-plan.md) → Phase 1  <br>
**Created:** 2026-05-28  <br>
**Status:** Complete  <br>
**BlockedBy:** —

---

## Purpose

Build the foundation every later phase hangs off: a new `songscribe.io.musicxml`
package containing a writer that emits an empty-but-schema-valid
`score-partwise` document, a SAX reader that parses it back into a `Song`, a
schema-validation test harness, and a round-trip harness
(`Song → write → read → Song'`) that asserts model equality for the populated
subset. The subset is just the default key at this phase and grows with every
later phase.

This is **not** a general MusicXML importer — the reader only ever parses
SongScribe's own output. The writer, however, is held to strict MusicXML 4.0
schema conformance so external consumers (MuseScore, Finale) accept it.

## Implementation Approach

Mirror the existing `.mssw` IO machinery rather than introducing a new paradigm
(no JAXB — see master plan § Architectural Decisions):

- **Writer** mirrors `SongIO.writeSong(Song, DocumentFontsHolder, PrintWriter)`
  (`io/SongIO.java:92`) — a `PrintWriter` driven by the `XML` helper
  (`io/XML.java`) for indentation and escaping.
- **Reader** mirrors `SongIO.DocumentReader` (`io/SongIO.java:214`) — a SAX
  `DefaultHandler` with a `where`-state enum and a `value` `StringBuilder`,
  exposing `getSong()`. Parsing is driven through `SAXParserFactory` exactly as
  `SongLoader` (`io/SongLoader.java:52`) does, reusing the parse-or-throw
  helpers in `DocumentValidation` (`io/DocumentValidation.java`).
- **Tests** mirror `SongIOTest`'s `writeSongToString` / `parseXml` helpers
  (`test/.../io/SongIOTest.java:506`).

### Gaps to close in this phase

- **`XML` helper has no attribute support.** `writeBeginTag`/`writeEmptyTag`
  emit tag names only (`io/XML.java:40-52`). MusicXML needs attributes
  (`version="4.0"`, `id="P1"`, `number="1"`, `print-object="no"`). Add a minimal
  attribute-aware variant (or emit those lines inline in the writer); do not
  rewrite the existing `XML` API.
- **Schema vs. DTD.** Validate against the bundled XSD
  (`docs/musicxml-4.0-schema/musicxml.xsd`), not the DTD. **Omit the
  `<!DOCTYPE>`** from writer output so validation is driven programmatically by
  the XSD and there is no network/entity fetch.
- **XSD is a test-time dependency only — do not move it to `src/main/resources`.**
  Nothing validates at runtime: the reader only parses SongScribe's own output
  (no validation), and the writer's schema conformance is enforced solely as a
  test gate (Phase 3). The schema therefore stays under `docs/` and is loaded by
  test code from there. If a future change makes runtime validation a genuine
  necessity, *that* is the trigger to relocate the schema into bundled runtime
  resources.

### Scaffold document shape (target writer output)

`score-partwise` requires at least one `<part>` and each part at least one
`<measure>`, so the "empty" document still carries one measure whose
`<attributes>` block establishes the SongScribe-wide constants:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="4.0">
  <part-list>
    <score-part id="P1"><part-name></part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>{DIVISIONS}</divisions>
        <key><fifths>0</fifths></key>
        <time print-object="no"><senza-misura/></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
    </measure>
  </part>
</score-partwise>
```

`{DIVISIONS}` is a **named constant** introduced here with a provisional value;
its final value (must cleanly express the dot/duration/tuplet set) is justified
in Phase 3. Do not hardcode a bare literal.

## Dependencies

- None external. Phase 1 is independent of the blocked attribution rework.
- Inputs that already exist: `Song` model (default key via
  `getDefaultKeyAccidentalCount()` / `getDefaultKeyType()`, used at
  `io/SongIO.java:108-112`), the `XML` helper, `DocumentValidation`, and the
  bundled schema at `docs/musicxml-4.0-schema/`.

## Plan

### Status Dashboard

| Phase | Description | Status | Recommended model |
|-------|-------------|--------|-------------------|
| 1 | [Writer Scaffold](#-phase-1-writer-scaffold) | ✅ Complete | Sonnet 4.6, medium |
| 2 | [Reader Scaffold](#-phase-2-reader-scaffold) | ✅ Complete | Sonnet 4.6, medium |
| 3 | [Schema-Validation Harness](#-phase-3-schema-validation-harness) | ✅ Complete | Sonnet 4.6, medium |
| 4 | [Round-Trip Harness & Tests](#-phase-4-round-trip-harness--tests) | ✅ Complete | Haiku 4.5 or Sonnet 4.6, low |

---

## ✅ Phase 1: Writer Scaffold

**Status:** Complete  <br>
**BlockedBy:** —  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — mostly mechanical
emission mirroring `SongIO.writeSong`, but the scaffold structure and the
attribute-helper gap need judgment.

### Tasks
1. Create the package: `src/main/java/songscribe/io/musicxml/package-info.java`
   (GPL license header + `package songscribe.io.musicxml;`), mirroring
   `io/package-info.java`.
2. Add attribute support: a minimal `writeBeginTag(pw, tag, attrs)` /
   `writeEmptyTag(pw, tag, attrs)` variant (in a musicxml-local helper, or
   extend `XML` additively without changing existing signatures). Reuse
   `XML.escapeXML` for attribute values.
3. Create `MusicXmlWriter.java` with a `writeSong(Song, PrintWriter)` entry
   point: emit the XML declaration and `<score-partwise version="4.0">` root (no
   `<!DOCTYPE>`).
4. Emit `<part-list>` with one `<score-part id="P1">` and an (empty)
   `<part-name>`.
5. Emit `<part id="P1">` containing one `<measure number="1">` whose
   `<attributes>` block holds `<divisions>` (named constant),
   `<key><fifths>0</fifths></key>`, `<time print-object="no"><senza-misura/></time>`,
   and treble `<clef><sign>G</sign><line>2</line></clef>`. Use the default key
   read from `Song` (NONE/0) rather than a literal.
6. `./scripts/compile.sh` succeeds.

---

## ✅ Phase 2: Reader Scaffold

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — directly mirrors the
existing `SongIO.DocumentReader` SAX handler and `SongLoader` parse flow.

### Tasks
1. Create `MusicXmlReader.java` as a SAX `DefaultHandler` mirroring
   `SongIO.DocumentReader` (`io/SongIO.java:214`): a `where`-state enum, a
   `value` `StringBuilder` cleared in `startElement`, and a `getSong()` accessor
   that throws `IllegalStateException` if called before parsing.
2. Recognize the `<score-partwise>` root and its `version` attribute; on an
   unsupported/newer version throw a reader exception (mirror the
   `NewerVersionException` pattern at `io/SongIO.java:207`). Accept `"4.0"`.
3. Parse `<key><fifths>` into the `Song` default key (0 → `KeyType.NONE`). Use
   `DocumentValidation.parseIntOrThrow` for the numeric body.
4. Per architectural decision, **ignore** `<clef>`, `<time>`/`print-object`, and
   `<divisions>` on read; track `<part-list>`/`<score-part>`/`<part>`/`<measure>`
   nesting only as far as the `where` state needs.
5. Add a static load helper (e.g. `read(InputSource)` / `read(File)`) that drives
   `SAXParserFactory` → `parser.parse(...)` and returns the `Song`, mirroring
   `SongLoader` (`io/SongLoader.java:52`).
6. `./scripts/compile.sh` succeeds.

---

## ✅ Phase 3: Schema-Validation Harness

**Status:** Complete  <br>
**BlockedBy:** 1  <br>
**Recommended model/effort:** Sonnet 4.6, medium effort — JAXP `SchemaFactory`
wiring plus local entity/include resolution against the bundled schema dir can
be fiddly; isolated so the round-trip tests stay clean.

> **Uncertainty to verify during implementation:** `musicxml.xsd` pulls in
> sibling `.mod`/`.ent`/`.dtd` files. The `SchemaFactory` must resolve those
> from `docs/musicxml-4.0-schema/` (point the source/`baseURI` at the schema
> file in place, and/or install an `LSResourceResolver`/`catalog.xml`-backed
> resolver). Confirm a clean compile of the schema before asserting on output.

### Tasks
1. Add a test-side helper `MusicXmlSchemaValidator` (under
   `src/test/java/songscribe/io/musicxml/`) that builds a `Schema` from
   `docs/musicxml-4.0-schema/musicxml.xsd` with local resolution, and exposes
   `validate(String xml)` throwing on any schema violation.
2. Write `MusicXmlWriterSchemaTest`: render `MusicXmlWriter.writeSong` for `new
   Song()` to a string and assert it validates clean against the XSD.
3. `./scripts/compile.sh` then `./scripts/test.sh MusicXmlWriterSchemaTest`
   passes.

---

## ✅ Phase 4: Round-Trip Harness & Tests

**Status:** Complete  <br>
**BlockedBy:** 2, 3  <br>
**Recommended model/effort:** Haiku 4.5 or Sonnet 4.6, low effort — mechanical
test wiring mirroring `SongIOTest`'s existing write/parse helpers.

### Tasks
1. Create `MusicXmlRoundTripTest` with a `writeToString(Song)` helper (mirror
   `SongIOTest.writeSongToString`, `test/.../io/SongIOTest.java:506`) and a
   `parse(String)` helper driving `MusicXmlReader`.
2. Add a `roundTrip(Song)` helper: `Song → write → read → Song'`.
3. Add an `assertPopulatedSubsetEquals(expected, actual)` helper asserting the
   fields covered so far (default key accidental count + type). Document that
   later phases extend this helper.
4. Test: an empty `Song` round-trips losslessly — default key preserved,
   `lineCount() == 0`.
5. Reuse the Phase 3 validator to assert the empty-song writer output is
   schema-valid (guards the harness against silently producing invalid XML).
6. `./scripts/compile.sh` then `./scripts/test.sh MusicXmlRoundTripTest` (run
   `unit` target) passes.

---

## Verification (whole sub-plan)

- `./scripts/compile.sh` reports SUCCESS after each phase.
- Writer output for `new Song()` validates against
  `docs/musicxml-4.0-schema/musicxml.xsd`.
- `Song → write → read → Song'` is lossless for the empty-song populated subset
  (default key).
- New unit tests pass via `./scripts/test.sh` (no e2e in this sub-plan).
