# The Change Report

How the user learns what the song changed on its own. An edit can break more
than it touches — a deleted note takes a beam with it, a retyped bar strands an
ending, a new tempo leaves a tuplet off the beat — and a file can hold content
the model will not accept. In every such case the model makes the change, and
the report tells the user afterwards what it made and why.

For how the changes themselves are decided, see
[span-invalidation.md](span-invalidation.md) and
[mutations.md](mutations.md#rules-at-the-close-of-an-edit).

## One way to tell the user

The report is the one way an automatic change reaches the user. No edit asks
for consent before it removes something, and no kind of change has an alert of
its own. The edit goes ahead, the report follows it, and undo reverses the edit
and everything it did on its own in one step. So there is one place that decides
how a change reads, and one dialog the user learns to read.

A report is a list of entries in the order they were recorded. Each entry names
the **kind** of change — beams removed, tuplets updated, accidentals converted —
gives a **description** of why, and says **where**: a range of elements, which
may cross a line break, as a tie does. A change with no place in the song, such
as a conversion applied to the whole file, has no location.

## The reporter writes the words

The code that makes a change builds its entry, text and all. It is the only code
that knows what happened: a span's own rule knows whether an endpoint was deleted
or the notes inside it changed, the tuplet pass knows which ratio it derived, the
ending check knows which bar broke it. The dialog knows none of that. It groups
entries by kind, counts each group into its heading, and shows each entry's
description at its location. A new kind of change therefore needs no change to
the dialog — only an entry recorded where the change is made.

The one thing the dialog does add is presentation of the location: entries hold
positions as the model counts them, and the dialog alone turns them into the
line and element numbers the user reads.

## What reports into it

Every removal the user did not directly request is reported. A removal the user
asked for — deleting a selected span, deleting every element a span covers,
toggling a beam or a tie off — is not, since the user already knows it happened.

- **The funnel.** Each span an element change leaves invalid is removed with the
  reason the span's rule gives. A span whose elements a deletion takes whole is
  not among them: it goes with its elements, unreported.
- **Displacements.** A span replaced by a new one of the same kind added over it
  is reported as replaced.
- **The rules at the close of an edit.** Each tie that no longer joins two notes
  of the same pitch, each ending whose split no longer closes an open repeat
  section, and each tuplet that no longer fits the beat.
- **Paste.** A span of the target the paste lands inside is removed; a span of
  the pasted fragment that would land inside one of the same kind is not pasted;
  a pasted tuplet, ending or hairpin that is not valid where it lands is not
  pasted. Each is reported, though a span that was not pasted was never in the song.
- **The load passes.** A tuplet the file did not time is given a ratio, and one
  that cannot stand is dropped; an ending that is not valid where the file puts
  it is dropped; a hairpin that is not valid where the file puts it is dropped; a
  breath mark that follows no note or rest is dropped; accidentals
  written in an older form are converted.

A stranded key change — a mid-line key change that restates the key already in
effect — is removed without a report. It carries no information the document
does not already hold, so there is nothing to tell. A span absorbed into a
neighbor of the same kind — two hairpins or two beams merged into one — goes
unreported for the same reason: the merged span covers every element it did.

## Two routes to one dialog

An edit and a load reach the same dialog by different routes, because they run
under different recording modes.

**An edit** runs inside a modification bracket. The outermost bracket holds the
edit's report; every reporter records into it through the song, wherever in the
edit it runs. When the outermost bracket closes — after the rules at the close
have run and the batch has been delivered, so the score is already laid out
again — the report is posted once for the whole edit, and only when it is not
empty. The dialog then opens once, however many changes it lists.

The dialog is modal, and a message is delivered synchronously, so it opens on a
later turn of the event queue rather than inside the delivery. Opened there, it
would hold the rest of the edit — whatever runs after the bracket closes — until
the user dismissed it. A load defers it the same way.

Undo and redo report nothing: a replay re-applies what the edit recorded, and
the user was told about it when the edit was made. A change made while tracking
is suspended reports nothing either.

**A load** runs with tracking suspended, so there is no bracket to record into.
The load passes are handed a report of their own instead, run in order over the
freshly read song, and the finished report travels back with the loaded song.
When it is not empty, the song is marked modified — the user should save the
repaired version — and the same dialog opens with a title and message that say
the changes were made during the load.
