# Plan: Split `MusicXmlRoundTripTest` into focused files
## Problem
`src/test/java/songscribe/io/musicxml/MusicXmlRoundTripTest.java` is 2892 lines and ~110 test methods spanning every concern accreted across Phases 2–7: barlines, notes, writer-output fidelity, reader leniency, per-note spans, hairpins, endings. It is the only grab-bag in the package (`NoteTypeMappingTest`, `PitchSpellingTest` are already narrow). It shares one pile of static helpers and constants.
## Approach
Introduce an abstract `MusicXmlRoundTripSupport extends UnitTest` holding only the cross-cutting plumbing. Each concern moves to its own class extending that support base. Concern-specific helpers/constants live with their tests, not in the base. No test logic changes — pure move + re-home.
### Shared base — `MusicXmlRoundTripSupport`
Holds what ≥2 concern files use:

- `writeToString`, `parse`, `roundTrip` — the write/read cycle (all files)
  
- `buildSong`, `LineBuilder` — song construction (all files)
  
- `assertRangeElementEquals` — used by spans **and** endings
  
- `TENTHS_PER_STAFF_SPACE` — shared coordinate constant
  
## Target files
| New file | Tests | Local helpers / constants moved with it |
| --- | --- | --- |
| `MusicXmlRoundTripSupport` (base) | — | the shared plumbing above |
| `MusicXmlBarlineRoundTripTest` | barlines, repeats, key sig, empty/default song (~20) | `assertPopulatedSubsetEquals`, `barlineTypesOf` |
| `MusicXmlNoteRoundTripTest` | durations, rests, grace, dots, accidentals, stems, X-offset, fermata, dynamics, articulations, glissando, fall, breath, alter/cautionary (~21) | `assertNoteEquals`, `extractAlterFromFirstNote`, note constants |
| `MusicXmlWriterOutputTest` | write-forward fidelity the round-trip can't catch: grace/x-offset/slide-endpoint output, B4/C4 staff-pos, `*WriterOutputIsSchemaValid` (~12) | `slideAttribute`, `scoreWithMeasureBody`, `firstElementText`, `SLIDE_*`/`DIAGONAL_SLIDE_*` |
| `MusicXmlReaderLenienceTest` | reader robustness: dangling/orphan markers, unknown tokens, malformed/throws, characters isolation (~11) | hand-crafted XML, no shared DOM helpers |
| `MusicXmlSpanRoundTripTest` | beam, tie, tuplet, trill + mid-line / measure-boundary (~21) | `beamValue`, `firstActualNotes`, `firstNormalNotes`, tuplet/trill constants |
| `MusicXmlHairpinRoundTripTest` | crescendo/diminuendo + wedge edge cases (~9) | `assertHairpinEquals`, `wedgeAttribute`, `HAIRPIN_*`, `WEDGE_TENTHS_PER_SS` |
| `MusicXmlEndingRoundTripTest` | endings — two-bracket, REPEAT_LEFT_RIGHT split, SINGLE_BARLINE anchor, split-less (4) | (uses base `assertRangeElementEquals`) |

Net: 1 file → 1 support base + 7 test files.
## Open questions
1. **Span file size.** `MusicXmlSpanRoundTripTest` is the largest at ~21 tests. Split beam out into its own `MusicXmlBeamRoundTripTest` (7 tests, the densest hook/level logic), leaving tie/tuplet/trill together? Or keep all four spans in one file since the plan grouped them?
  
2. **Writer-output vs reader-lenience.** Proposed as two files (write-forward fidelity vs. read robustness). Acceptable, or fold both into one `MusicXmlEdgeCaseTest`?
  
3. **Support as base class vs. static util.** Recommending an abstract base (`extends UnitTest`) so statics are inherited with zero import churn. Alternative: a non-test `MusicXmlTestSupport` util + `import static` in each file. Base class is simpler here; flag if you prefer the util form.
  
## Execution & verification
- Create the support base, then create each file by moving its methods + local helpers + constants verbatim; delete the original `MusicXmlRoundTripTest`.
  
- Test methods have no external callers, so moves are self-contained.
  
- Gate: `./scripts/compile.sh` SUCCESS, then `./scripts/test.sh unit` green — same total test count, no behavior change.
