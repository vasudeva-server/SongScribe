# Layout Geometry Concepts

## Column extent

The horizontal span of ink that a note column occupies, note-local with the origin at the note-head glyph. It covers the accidental, the stem, the note head and the augmentation dots, and excludes ledger lines. Modeled by `NoteColumnGeometry.ColumnExtent`.

## Element top

The highest point of an element's ink: the stem tip for a stem-up note, and the top of the element's own glyph box for any other element. Modeled by `ElementColumn`.

## Frozen gap

A gap between two columns whose rest length is no greater than its strut, so that spacing never compresses it. Modeled by `Spring`.
