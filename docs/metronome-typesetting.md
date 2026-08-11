# Metronome Typesetting

Background and rationale for how metronome markings — beat changes, tempo changes, and the
song-level tempo mark — are typeset, implemented for issue #735. The design decisions behind it,
not a walkthrough of the code.

------------------------------------------------------------------------

## Build once, carry, paint

A metronome marking is typeset exactly once, by layout, into a `MetronomeContent`. Nothing
downstream measures anything.

```
  MetronomeContent.forBeatChange(beatChange, annotationFont)
  MetronomeContent.forTempo(tempo, annotationFont)
                    │
                    │  items[]  · widthSs · regions[]
                    ▼
  SystemStacker.stackMetronomeAttachment / .stackTempoMark
                    │
                    ▼
  StackingUtils.stackAboveWithRegions ──▶ LayoutResult.DecorationLayout(…, content)
                    │                                    │
                    │                                    ▼
                    │              VerticalStackingCalculator.applyDecorationOffsets
                    │                    (rebuilds the record — must carry content through)
                    ▼                                    │
  HitRegionBuilder.decorationInkRectSs                   ▼
        (xSs, ySs, widthSs, heightSs)     BeatChangeRenderer / TempoChangeRenderer
                                          SongTempoMarkRenderer
                                                         │
                                                         ▼
                                          MetronomeRenderer.drawContent
                                              (walks items; measures nothing)
```

`MetronomeContent` is a list of positioned `Item`s (a SMuFL `GlyphItem` or a `TextItem` in the
resolved annotation font), a total `widthSs`, and a list of `CollisionRegion`s. Every measurement
is in staff spaces and therefore zoom-invariant.

Each item carries its position on **both** axes — an `xSs` from the content's left edge and a
`baselineOffsetSs` from its top edge — so the renderer decides nothing. An earlier revision let
text items take their baseline from `QUARTER_NOTE_HEIGHT_SS` at paint time while the builder
used the same constant to place the text's collision region: one number, computed in two files,
agreeing only by hand. That is the shape of the bug described below, so it does not belong here
either.

The content is stated once for all three depictions of a metronome marking: the per-note
`BeatChangeAttachment`, the per-note `TempoChangeAttachment`, and the song-level `SongTempoMark`
at the first line's staff header. There is no second typesetter anywhere for any of them.

------------------------------------------------------------------------

## Why the content is carried rather than recomputed

The measured box and the drawn ink have to agree, because three separate consumers depend on the
same numbers: `DecorationLayout.widthSs` becomes the horizontal extent of the marking,
`HitRegionBuilder.decorationInkRectSs` turns that box into the clickable target, and the
`CollisionRegion`s drive vertical stacking. If layout measures one thing and the renderer draws
another, the marking's hit box and its stacking reservation are both wrong by the difference, and
nothing in the code can notice.

Before #735 layout and rendering were two independent typesetters, and they disagreed in three
ways at once:

1. The renderer drew `" = "` — the `MetronomeAttachment.EQUALS_STR` literal, spaces included —
   while the measuring path measured the bare `"="`. Shortfall 0.80 Ss at default settings.
2. The renderer advanced by one augmentation-dot step after *every* left-hand note glyph, and
   again for the dot itself when the note was dotted. The measuring path added two dot steps for
   a dotted note and nothing at all for an undotted one. Shortfall 0.32 Ss whenever the left note
   was undotted.
3. Every layout path measured with `getAttributionFont()`; every render path drew with
   `getAnnotationFont()`.

The combined 1.12 Ss shortfall exceeded the 1.06 Ss advance width of a right-hand quarter-note
glyph: the note to the right of the `=` sat entirely outside the marking's own hit target. It
could be seen but not clicked, and the `CollisionRegion`s were misplaced by the same amount.

Each of those three could have been fixed on its own. Fixing them individually would have left the
structure that produced them — two typesetters that have to be kept in agreement by hand — intact,
and the next divergence would have been introduced by the next person who touched either side.
Building the content in layout and carrying it makes divergence unrepresentable rather than merely
absent.

Two consequences of that principle are worth stating outright, because they look like defensive
overhead and are not:

- `MetronomeAttachment.metronomeGlyphFor` is non-nullable and throws
  `RuntimeError.missingResource` for a type it cannot map. A missing metronome glyph is a broken
  install, never a skippable case, so no caller is offered the option of quietly drawing nothing.
- A metronome `DecorationLayout` that exists but carries null content is a layout bug, so the
  renderers throw rather than draw nothing. Silently painting an empty marking would hide exactly
  the class of failure this design exists to prevent.

User offsets are the one place the chain could once be broken by accident:
`VerticalStackingCalculator.applyDecorationOffsets` rebuilds the `DecorationLayout` when the user
nudges a decoration, and dropping `content()` there makes a nudged marking vanish from the score
with no exception. That rebuild is now `DecorationLayout.shiftedBy`, a method on the record
itself, so a component added later is carried through without anyone having to remember it.

Both renderers reach the content through `DecorationLayout.requireContent()` rather than checking
for null themselves, so the rule that a metronome layout must carry content is stated once, on
the type that owns it.

------------------------------------------------------------------------

## One font, resolved once

`getAnnotationFont()` is the single resolved font for every metronome marking. It is resolved by
`SystemStacker`, scaled once by the builder for the staff-space transform, stored in each
`TextItem`, and set verbatim by the renderer — which neither asks for a font by name nor derives
one. Scaling in the builder rather than at paint time keeps a `deriveFont` allocation out of the
paint loop, the same reason `LyricRenderMetrics` pre-scales the lyrics font.

The choice of `getAnnotationFont()` over `getAttributionFont()` is simply what was already being
drawn, so nothing changed on screen. The reason the old mismatch was invisible is worth
remembering: `src/main/resources/conf/system-defaults.json` gives both keys the same family and
size, so the two fonts measured identically and the bug lay dormant until someone changed one of
them. Layout and rendering resolving different fonts is precisely the failure this design
prevents, and it is prevented by there being only one resolution point rather than by the two
happening to agree.

------------------------------------------------------------------------

## The gap after a note, and who decides it

A note glyph is followed by one augmentation-dot step of space. When the note is dotted, that
step is the space *before* its dot. When it is not, the same step is the space before whatever
comes next — the `=` in a beat change, the `=` and BPM in a tempo mark. But a note with nothing
after it needs no separator, and a trailing gap there would be dead width added to the marking's
advance, its hit box and its collision extent.

The builder resolves this with a **pending gap** rather than with a flag from the caller. An
undotted note leaves one dot step owed; a dotted note has already spent that step before its own
dot, so it owes nothing. Whatever is appended next pays the debt before positioning itself, and a
debt still outstanding when the content is built is dropped. The total width therefore ends on
ink by construction, and no caller can get the question wrong — an earlier revision passed a
`Trailing.GAP` / `Trailing.NONE` enum in from the two factory methods, which made the correct
answer a thing each caller had to know rather than a property of the sequence.

This matches the old *drawing* behavior rather than the old measuring behavior — the second of
the three divergences above was resolved in favor of the ink, so what was on screen stayed on
screen and the measurement moved to meet it.

------------------------------------------------------------------------

## One region per ink run

`CollisionRegion`s exist so that vertical stacking can nestle other decorations into the gaps of a
marking's silhouette rather than treating it as one solid block: each region queries the extents
over its own horizontal span, and each is reserved at its own visual top.

The rule is one region per ink run. Every glyph or text item appended contributes a region
covering the ink it draws; the gap advances count toward `widthSs` but belong to no region, so
nothing is reserved over them. An augmentation dot does not get a region of its own — it extends
the region of the note it belongs to, because the note and its dot are one continuous run of ink.

That gives:

- **Beat change** — 3 regions: duration note, `" = "`, beat note. Dotting either note widens that
  note's region; it never adds a fourth.
- **Tempo mark with `shouldShowTempo()` true** — 3 regions: the metronome note, `" = "`, and the
  BPM plus description drawn as one string.
- **Tempo mark with `shouldShowTempo()` false** — 1 region for the description alone, or none at
  all when the description is empty, which is the same condition that makes `widthSs` zero and the
  mark vanish. See [Song-Level Tempo](song-tempo.md).

Note regions and text regions are not the same shape. A note region starts at the decoration's top
edge and is `QUARTER_NOTE_HEIGHT_SS` tall. A text region sits on the note cap-height baseline, so
it starts lower and reaches below `QUARTER_NOTE_HEIGHT_SS` far enough to cover the font's
descenders — the ascent and descent come from the resolved font, not from the characters actually
in the string, so a marking's silhouette does not change shape when its BPM changes from `120` to
`132`.

------------------------------------------------------------------------

## `drawContent` decides nothing, by design

`MetronomeRenderer.drawContent` walks the items and draws each one at its own stored offset, on
both axes, in its own stored font. It computes no advance, resolves no font, derives no font,
constructs no `TextLayout`, and picks no baseline. That is the point of the method rather than an
incidental property of the current implementation.

The correctness half is stated above: deciding a position in the paint path is how the two
typesetters got out of step in the first place, and it does not stop being true for the vertical
axis. The cost half is that the `ScaleContext.textWidthSs` call `drawContent` replaced built a
`TextLayout` on every paint, for every marking on screen, on every scroll and every zoom step.
Both halves point the same way, so any future change that wants a measurement or a placement rule
inside `drawContent` is asking for the wrong thing in two directions at once — it belongs in
`MetronomeContent.Builder`, where it happens once and everything downstream sees the same answer.
