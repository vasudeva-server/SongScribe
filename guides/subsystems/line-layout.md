# Line Layout

Read `LayoutEngine` first.

## Membership

- `LayoutEngine`
- `LayoutResult`
- `LayoutResultBuilder`
- `ElementColumn`
- `ElementColumnBuilder`
- `HorizontalSpacingCalculator`
- `InsertionSpacingCalculator`
- `Spring`
- `SpringSpacer`
- `SpringSolveResult`
- `OpticalSpacing`
- `LyricLift`
- `LyricLayoutBuilder`
- `LyricEditFitCalculator`
- `KeyEditFitCalculator`
- `LineKeys`
- `CautionaryKeySignature`
- `StaffExtents`
- `ShapeProfile`
- `VerticalStackingCalculator`
- `NoteAttachedStacker`
- `StructuralStacker`
- `SystemStacker`
- `StackingLayer`
- `DynamicGrouper`
- `HairpinEndpoints`
- `MetronomeContent`
- `HitRegionBuilder`
- `LineLayoutProvider`

## Rules

- Write every reservation a mark makes into the `StaffExtents` layer of the tier that places it, because a later tier clears only what an earlier one wrote.
- Draw in the renderer only geometry that layout measured, because the hit region and the collision reservation derive from the same numbers and a second measurement cannot be noticed when it differs.
