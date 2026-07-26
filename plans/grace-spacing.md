Here are the tables, derived from `OpticalSpacing` + `HorizontalSpacingCalculator.GRACE_HOST_REST_SS`.

**Setup.** A grace column always stems UP on a 2.5 ss stem (`NoteGeometry.effectiveDirection`, `GRACE_NOTE_STEM_LENGTH_SS`), so its vertical span is `[pos − 2.5, pos + 0.59]` (0.59 = `HALF_NOTE_HEAD_SS`). Relative position below is **host − grace, in staff positions** (1 sp = 0.5 ss); positive = host lower. Full range is −22…+22 (`MIN_STAFF_POSITION_SP` −10 … `MAX_STAFF_POSITION_SP` 12). "Space" = `GRACE_HOST_REST_SS` (2.0 ss) + adjustment — the uncompressed whitespace the spring holds between the grace's charged right ink edge and the host's origin.

## Downstem host

Only `oppositeStemCorrectionSs` fires (grace UP → host DOWN, sign +1, always widening), ramped by `min(overlap / 3.5, 1) × 0.5`. Host span `[pos − 0.59, pos + 3.5]` (natural, unshortened stem).

| Rel. staff pos (sp) | Optical adjustment (ss) | Space, uncompressed (ss) |
| ------------------: | ----------------------: | -----------------------: |
| −22 … −12           | 0                       | 2.000 (16.00 px)         |
| −11                 | +0.071                  | 2.071 (16.57 px)         |
| −10                 | +0.143                  | 2.143 (17.14 px)         |
| −9                  | +0.214                  | 2.214 (17.71 px)         |
| −8                  | +0.286                  | 2.286 (18.29 px)         |
| −7                  | +0.357                  | 2.357 (18.86 px)         |
| −6                  | +0.429                  | 2.429 (19.43 px)         |
| −5                  | +0.441                  | 2.441 (19.53 px)         |
| −4                  | +0.441                  | 2.441 (19.53 px)         |
| −3                  | +0.383                  | 2.383 (19.06 px)         |
| −2                  | +0.311                  | 2.311 (18.49 px)         |
| −1                  | +0.240                  | 2.240 (17.92 px)         |
| 0                   | +0.169                  | 2.169 (17.35 px)         |
| +1                  | +0.097                  | 2.097 (16.78 px)         |
| +2                  | +0.026                  | 2.026 (16.21 px)         |
| +3 … +22            | 0                       | 2.000 (16.00 px)         |

Shape of the curve: two different geometries bound the overlap. From +2 down to −4 the overlap is `1.18 − d` (grace notehead bottom vs. host notehead top), rising as the host climbs. From −5 downward it is the host's *stem tip* vs. the grace's stem top (`d + 6.0`), falling again — so the correction peaks at −5/−4 (host 2–2.5 ss above the grace) and dies at −12. Peak overlap is 3.09 ss against a `STEM_OVERLAP_SATURATION_SS` of 3.5, so a grace→downstem-host gap **never reaches the full 0.5 ss**; 0.441 is its ceiling.

## Upstem host

Both stems UP, so `oppositeStemCorrectionSs` is 0 and only `sameDirectionCorrectionSs` fires: a flat ±0.25 once `|Δ| > SAME_DIRECTION_THRESHOLD_SS` (0.5 ss = 1 sp), widening when the host is higher, narrowing when it is lower. Stem lengths are irrelevant here — only notehead positions.

| Rel. staff pos (sp) | Optical adjustment (ss) | Space, uncompressed (ss) |
| ------------------: | ----------------------: | -----------------------: |
| −22 … −2            | +0.250                  | 2.250 (18.00 px)         |
| −1 … +1             | 0                       | 2.000 (16.00 px)         |
| +2 … +22            | −0.250                  | 1.750 (14.00 px)         |

## Caveats on the third column

- **Narrowing can be swallowed by the strut.** The −0.25 rows only show up where the gap has slack above its collision floor. A host carrying an accidental pushes the note-collision strut to `prevRight + 1.0 + |leftExtent|`, which for any accidental (≈1.3 ss) already exceeds the uncorrected 2.0 rest — that gap is frozen and neither table's numbers are visible. Widening always applies.
- **Forced host stems.** Rows −11…−6 of the downstem table are governed by the host's stem tip, so a host whose downstem is *forced* (host below the middle line) and therefore shortened by up to 1.0 ss loses up to 0.143 ss of adjustment there; at maximum shortening, −11 and −10 fall to 0. Every other row is independent of stem length.
- **Flag charging is a separate effect.** The space above is measured from whichever ink edge is charged. Where the grace's flag hangs clear of the host's left-facing band (`getRightExtentFacingSs`, refs #560) the flag is not charged, so the grace's *notehead* sits one flag-extent closer to the host than where it is charged — that is an ink discount, not an optical adjustment, and it does not appear in column 2.
