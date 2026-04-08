# TODOs

## Add system-tier stacking tests for beat changes and annotations

**What:** Add positioning tests for `stackBeatChange` and `stackAnnotations` to
`SystemTierStackingTest`, mirroring the structure of `StructuralTierStackingTest`.

**Why:** System-tier stacking currently has zero positioning tests for these elements.
The refactoring (Phase 4) extracts `SystemStacker` without a safety net for beat change
and annotation stacking. Tempo tests are added during the refactor, but beat changes and
annotations cannot be reliably tested yet because they have not been migrated to the new
layout system.

**Context:** `stackBeatChange` and `stackAnnotations` live on `SystemStacker` after the
refactor. They use `StackingUtils.stackAbove` and `StackingUtils.stackAboveWithRegions`
respectively. Once these elements are migrated to the new layout system, add tests that
verify: positioning above the structural layer, positive dimensions, no overlap with
adjacent system-tier elements.

**Depends on:** Migration of beat changes and annotations to the new layout system.
