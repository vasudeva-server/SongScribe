# Line Layout Concepts

## Skyline

A record, indexed across the line, of how far out anything reaches at each horizontal position. Modeled by `StaffExtents`.

## Outward

The direction away from the staff: toward smaller Y for marks above it, toward larger Y for marks below it. Modeled by `Direction`.

## Script

A note-attached decoration such as a staccato, an accent, a fermata or a trill. It is not the lyric script of [lyrics](lyrics.md). Modeled by `NoteAttachedStacker`.

## Support

A reservation lying under an element's inner edge that the element must clear by its padding. The staff line itself is not a support. Modeled by `StaffExtents.Support`.
