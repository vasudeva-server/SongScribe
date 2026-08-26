# Cut, Copy and Paste

How the pieces of the clipboard subsystem fit together. Each class states its own
promises; this is the arrangement they sit in.

## The pieces

The clipboard holds **one fragment** — a run of staff elements taken from a song
together with the spans wholly inside it. Five roles act on it:

- **capture** takes a run off a line, normalizing the selection's boundaries and
  recording the accidental context each element had where it came from;
- **instantiation** produces a fresh, independent copy for each paste, so the
  held fragment is never itself inserted and pasting twice yields two results
  sharing nothing;
- **reconciliation** decides what happens to spans the paste lands inside;
- **placement** picks the spot when there is no selection to replace;
- **accidental reconciliation** preserves the pitches of notes the user did not
  edit.

A fragment carries what the music *is*, never how it was placed: horizontal
nudges are discarded on the way in, because a nudge is meaningful only under the
spring solve, neighbours and header width it was computed against.

## Confirmation, then one bracket

Every clipboard gesture is a single undo step, and every question the user might
be asked comes **before** that step opens.

Cut asks first, then copies, then deletes — so declining leaves both the score
and the clipboard untouched, the copy not yet having happened. A paste over a
selection deletes and inserts inside one bracket, matching what the user
experiences as one action. A paste that will not fit changes nothing at all, so
the bracket closes empty and the selection survives.

The ordering matters because a question asked *inside* a bracket would have the
user answering about an edit that has already partly happened.

## Pasting inside an existing span

Capture keeps the source side consistent, and deleting a range drops any span
that loses an endpoint to it. Neither covers the span that **straddles** the
paste — beginning before it, ending after it, both ends surviving — which would
otherwise stretch silently over material the user never put under it. One place
decides that case, and it runs on the line as it stands *before* any mutation,
while the positions it reads still mean something.

The decision is per span kind, and the two sides can be dropped independently:

| Kind | Destination span | Fragment's own |
| --- | --- | --- |
| tuplet | removed | dropped |
| beam | removed | dropped |
| tie | removed | kept |
| trill | removed | kept |
| hairpin | kept, unless contradicted | same-type dropped |
| ending | kept | dropped |

The shape of that table follows from what each kind means. Tuplets and beams are
rhythmic groupings: one that no longer covers the notes it was written for is
wrong, and a pasted group dropped into the wreckage of a broken one is equally
wrong, so both sides go — per kind, so a straddled beam does not cost a pasted
tuplet. Ties and trills bind particular notes, so the straddling one is wrong
while the fragment's own still binds the fragment's own notes.

A hairpin reads correctly over any stretch of notes, so a straddled one is kept
and simply widened, and the fragment's — necessarily a shorter one of the same
type inside it — is redundant. A fragment hairpin of the *opposite* type is a
contradiction no widening can fix, so there the destination's goes instead. An
ending is the same reasoning in reverse: a bracket covering a few extra notes is
still valid notation, but one nested inside another never is.

A paste that merely clips a group at its edge is deliberately not treated as a
straddle: that group dies anyway when it loses an endpoint, and the pasted group
lands contiguous at the boundary rather than interleaved with orphaned remains.

**Abutment is somebody else's problem.** Two same-type hairpins nose to tail are
one hairpin — the same rule that applies when the user *draws* one flush against
another, so it lives where drawing handles it, and pasting and file-reading both
route through there too.

## Placing a paste by clicking

A paste with no selection enters a modal "click to place" state. Two
responsibilities are split, and the split is the point: **one piece owns picking
a spot on a line, another owns what goes there.** Nothing in the first knows
about clipboards; nothing in the second knows how a mouse position becomes a
position in the music. Mid-line key changes are a second user of the same
machinery — see [key-changes.md](key-changes.md).

A rule that holds for *every* kind of placement lives with the interaction rather
than being restated by each user of it: the staff header, the region past the
staff's right edge, and the gap between a mid-line key change and its barline
are excluded before the client is ever consulted.

The client is entitled to exactly one end-of-placement report — placed or
cancelled, never both, never twice — and always after the interaction has already
gone idle. A refused spot is not an ending: the banner stays up so the user can
pick again. While a placement is pending every action is disabled and presses on
a line are inert, so the next click is always a placement or a cancel.

## Keeping pitches through an edit

Anything that changes which explicit accidentals sit on a line — or where — can
silently change how a note the user never touched sounds. Reconciliation exists
to stop that, and it always runs; no call site decides for itself whether it is
needed.

Two populations are protected: material arriving from elsewhere keeps the pitch
it had in its source context, and material already present keeps the pitch it had
before the edit. A note the user themselves changed is never protected. A note
ending a tie is never a candidate either — a tie asserts that two notes are one
sounding pitch, so the tied note has no pitch of its own to keep.

The comparison is by how a note *sounds*, never by which accidental is written,
since an absent accidental and an explicit natural sound alike. The key
never enters the comparison directly: it is the last fallback of the resolver, so
resolving before the edit against the source and after against the destination
compares the two keys implicitly. That is what lets a key change be handled by the
same walk as everything else.

Where a note would otherwise change, it is given an explicit accidental. The
mirror case removes one: an accidental already present is cleared when the edit
moved the context arriving at that note *and* left the accidental saying nothing
new. Both conditions together are what let a deliberate restatement or a courtesy
accidental survive every edit that does not move its context.

**Reach is the part that surprises.** The backward scan is bounded within a line,
but how many lines one reconciliation covers is a separate question, and what
decides it is whether the edit **moves a key** — not which edit it is. Accidental
context resets at a line boundary; what crosses one is the key. An edit that
leaves every line running in the key it already ran in reaches one line. An edit
that moves a key runs forward to the first line establishing a key of its own,
which is the only way a reconciliation spans more than one line.

A paste is either. A fragment carrying a key change moves the key the destination
line leaves off in, so that paste reaches the whole inheritance chain past it and
is reconciled exactly as a key change written by hand is; a fragment carrying none
reaches its destination line alone. The user sees one prompt for the whole of it
either way — never one per line, and never a second prompt because the paste both
overwrote a selection and re-keyed what followed.

A key change the fragment brings in can also arrive stranded, landing where its
key is already running — see [key-changes.md](key-changes.md) for why that
state is not allowed to exist. **The fragment is reduced before anything measures
it**, rather than pasted whole and swept afterwards, and that ordering is the
point: the fit gate, the accidental reconciliation and the span reconciliation all
read the arriving run, so all three have to see the run that actually lands.

**Order matters against layout.** Accidentals have to be materialized before the
projected columns used for the fit check are built, because accidental width is a
layout input. With that ordering both the fit gate and the committed layout are
correct with no per-position shift machinery. Reconciliation itself only reads
and reports; the caller applies the result, and only if the edit's fit check
passed.

An in-place change — an accidental toggle, a pitch shift, a duration swap — can
widen a column without changing how many elements there are, so it is measured
the same way an insertion is, on copies, before anything commits. Only changes
that can alter a column's horizontal extent are gated; marks that stack
vertically cannot make a line wider. Without the gate an impossible edit is not
refused but surfaces later as a line laid out on its collision floors, running
past the staff and clipped.

## Not supported

- **Beams at the seams.** Pasting mid-line can change the beat context of the
  notes after it, whose beams may then be wrong. A paste affects only what it
  inserts; the surrounding beams are the user's to fix.
- Undoing a cut or a paste-over-selection does not restore the selection that
  preceded it.
- The clipboard is in-process only — no interoperation with the system pasteboard.

Cross-document paste works, and is supported rather than merely tolerated: the
clipboard belongs to no one document, so copying in one song and pasting into
another is an ordinary paste. The held fragment keeps its source song reachable
until the next copy replaces it — one song, bounded, and deliberately not cleared
on document close, which would break that flow for no benefit.
