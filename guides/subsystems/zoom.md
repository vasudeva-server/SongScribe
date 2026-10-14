# Zoom

Read `ViewScale` first.

## Membership

- `ViewScale`
- `DocumentScale`
- `Ss`
- `DocPx`
- `ViewPx`
- `PixelDistance`
- `ZoomController`
- `ZoomDidChangeNotification`
- `ScoreView`
- `TextMeasurement`
- `LyricTextRenderer`

## Rules

- Keep the zoom factor out of layout, measurement and the document model, because a value that
  carries it inward is scaled twice by the time it is drawn.
- Apply the zoom factor only at the paint transform, and never multiply by it again inside that
  transform.
- Read a view's zoom from the view at the moment you need it, never as a pushed or cached field,
  because a line component rebuilt on layout would otherwise draw unzoomed while the rest of the
  tree stayed zoomed.
- Convert a mouse event once, where it enters, with `ViewScale.toSs` for anything that asks
  against layout geometry and `ViewScale.toDocPx` for a consumer that works in document pixels.
- Never route a staff-space destination through `DocPx`, because the zoom magnifies the
  intermediate rounding.
- Size anything rendered outside a live view from `DocumentScale`, never by dividing a zoomed
  measurement by the factor, because that loses information at the layout clamp.
- Convert a view-scaled measurement back to document space before clamping, never after.
