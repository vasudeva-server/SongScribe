# Zoom

Zoom is **per-view state**, not a property of the document. A song has one
authoring scale; a view chooses how large to draw it, and two views of the same
song may choose differently.

## Three regimes

| Regime | What it is |
|---|---|
| staff spaces | the zoom- and device-independent layout unit |
| document pixels | pixels at the fixed document scale, i.e. at 100% zoom |
| view pixels | on-screen pixels at the view's *current* zoom |

Because the document scale is fixed, staff spaces and document pixels are two
names for the same underlying scale. A view's zoom is the only thing that folds
on top, producing view pixels. Each regime is a distinct type, so a value cannot
cross from one to another without saying so; see
[spatial-units.md](../.claude/guides/spatial-units.md) for the suffix convention
code follows beneath this.

Both pixel regimes expose the size-rounds-up / position-rounds-to-nearest pair
from that document. Staff spaces have no integer form at all — they stay
fractional until they cross into a pixel regime.

## Where zoom is applied

Zoom reaches exactly five places: the paint transform, component preferred
sizes, mouse input, overlay bounds, and page sizing. Nothing else knows zoom
exists.

**The factor is applied once**, at the paint transform. Everything drawn inside
that transform works in staff spaces and must never multiply by the factor
again. Two renderers draw *outside* it, in pixel space, and so have to
reintroduce the factor by name:

- The **lyric renderer** strips the transform so painted lyrics land on the same
  baseline as the inline lyric editor overlaid on the same spot. Drawing through
  the staff-space scale takes a different rounding path and shifts the baseline
  by up to a device pixel, so text would visibly jump on entering and leaving
  edit mode. This is editor parity, not a general legibility win: a renderer with
  no overlaid editor has no reason to copy it.
- The **attribution block** is not a component and never gets a graphics context
  of its own, so whoever paints it decides its coordinate space. Its two callers
  agree on pixels, so one pixel-based render serves both, taking the factor as an
  explicit parameter. Its *measurement* is deliberately zoom-free, so the
  staff-space dimensions layout reserves are zoom-invariant by construction.

In both cases the carrier is named for what it is, so it cannot be mistaken for a
document-scale value.

## Read zoom on demand, never cache it

A view's zoom is never pushed into components as a field. Each on-score consumer
reads it through its back-reference to the view at the moment it needs it.

This matters because line components are rebuilt on layout rather than through
the document-load path: a push would leave a freshly rebuilt line drawing at 100%
while the rest of the tree stayed zoomed. Reading on demand makes that class of
staleness structurally impossible.

Consumers with no view — dialog previews, exporters — read a shared read-only
identity scale and render at natural size regardless of what any live view is
showing.

## One notification, ordered by priority

A zoom change is announced as a single notification. Every reactor is a handler
of it — applying the change, the status bar, action enablement, the active lyric
editor, overlay bounds — rather than some being called directly and others going
through the bus.

One handler actually applies the change, and it runs at a priority above all the
others, so every remaining reactor observes the zoom as already applied rather
than as about to be.

Zoom state is read and written on the event-dispatch thread only, and nothing is
synchronized.

## Mouse input converts once, at the entry point

Mouse events arrive in view pixels. Convert **once**, where the event enters, so
nothing downstream re-derives the factor. Which regime you convert *to* depends
on what the consumer works in — staff spaces for anything asking questions
against layout geometry, which is the common case; document pixels for the rarer
consumers that genuinely work in that regime.

Do not route a staff-space destination through document pixels on the way. The
intermediate step rounds to a whole document pixel, which buys nothing for a
fractional destination and costs up to half a pixel that the zoom then magnifies
on screen.

## Export is zoom-independent by construction

Export sizing is derived from the document scale and never from a live view. In
particular it is never recovered by dividing a zoomed, already-rounded on-screen
measurement back down by the factor: that round trip loses information at the
clamp applied during layout and accumulates rounding error. Where a view-scaled
measurement is genuinely needed, it is converted back to document space *before*
any clamping, never after. This keeps exporters immune to whatever zoom the view
happens to be showing.
