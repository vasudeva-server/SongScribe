# Lyrics and Verses

## Verses are languages, not stanzas

A song may carry several verses, but **only one is ever displayed at a time**.
Multi-verse support exists so a song can hold its lyrics in more than one
language; the user picks one, and that one is active. Verses are not stanzas
stacked under the staff, and no feature should assume they are.

What follows for anything touching verses:

- There is exactly one active verse at any moment.
- Never design layout or UI that shows two verses at once, and never treat the
  second verse as "the row below the first".
- Verse numbering starts at one; there is no verse zero.
- Code iterating verses is iterating *available languages*, not rows to paint.
  Pass the verse explicitly rather than defaulting to the first — the active verse
  will not always be the first.

The active verse is **session state, not document state**: it is deliberately not
written to the file, so every document opens on its first verse. Changing it
changes what every line lays out, so a caller that changes it must invalidate
layout afterwards.

There is no UI for choosing it yet, so every song shows its first verse. The
picker — along with naming verses by language and round-tripping those names
through the file format — is separate work.

## The column is the verse

An element holds every verse at once and cannot say which one is being laid out.
So the verse is resolved **once**, when the layout columns are built, and each
column carries the one lyric it was built from together with the width measured
for it. Past that point nothing looks a lyric up on an element again.

Because the text and its measured width are fixed together at construction, a
column cannot exist carrying text from one verse and a width measured for
another. Later passes read both off the column. A verse still travels alongside,
but only to stamp what those passes emit; the words themselves come off the
columns.

**Switching the active verse therefore means rebuilding the columns.** A layout
pass does this anyway; a test exercising a second verse must do the same rather
than reusing columns built for the first.

## One row, always

The lyrics band is one row deep, whatever the song carries. A song with lyrics in
three languages is exactly as tall as one with lyrics in one.

The row is reserved even on a line with no lyrics yet, so entering the first
syllable does not re-space the song.

## Two chains run through a verse

Both are stored on the individual syllables rather than as spans: the **syllabic
chain**, which draws the hyphens inside a word, and the **melisma chain**, a start
followed by text-less carriers and a close, which draws the extender under a
syllable sung across several notes.

Because a member of either chain names its neighbours only by position, **every
edit that changes who a syllable's neighbours are has to repair both.**

Those repairs live on an abstraction over "a run of elements carrying lyrics",
not on the line — because a run lifted out of a line is the other case that needs
them. A line is one such run; a clipboard fragment is another. Only three things
differ between them: how to reach an element, how many elements count (a line
excludes its auto-maintained terminal, a detached run has nothing to exclude), and
what to do with a repair once applied — a line records it so it can be undone, a
detached run records nothing, being in no document.

**Adding a repair** means adding it there and routing every write through the
recording step, and it then works on both. Reaching into an element's lyrics
directly from a call site would escape the modification bracket, and undo would
not see the write.

A run lifted out of a longer one has to end every chain that would otherwise
point at an element the run no longer contains — which is composed from the
deletion repairs, since "everything around this run is gone" is exactly what a
deletion produces.

## What a chain runs through

Two facts about an element's type decide where a syllable can go and how far a
chain reaches:

- **Bearing syllable text** — a note or a grace note, and only those. A syllable
  is sung on a pitch, so a rest cannot hold one and neither can any structural
  marker.
- **Carrying a chain onward** — everything except a repeat. A rest, a barline, a
  breath mark and a key change all carry a melisma or a hyphenated word past
  themselves without ever taking a syllable of their own. A repeat ends a section,
  so nothing runs through it.

Type is only half of "can a syllable go here". The other half is grace-note
pairing: a note with a paired grace note in front of it takes no syllable, because
that syllable is the grace note's. Both halves are asked as one question, taking
the preceding element as an input — so a run of elements belonging to no line gets
the same answer as a live line.

**Inserting an element breaks the chains around it only when it interrupts
them.** An element that carries a chain on but can never take a syllable is
transparent: the word and the melisma are left exactly as they were. Everything
else interrupts, for one of two opposite reasons — a note or grace note is a
syllable slot that arrived empty, so the word can no longer be sung as one word;
a repeat carries nothing at all. The judgement is made over the whole inserted run
at once: one interrupting element in it breaks the chains.

Two repairs in that same pass deliberately do *not* read that rule — stripping a
glissando, and syncing a grace note's melisma with its host. Those are about which
element a pairing points at rather than about who a syllable's neighbours are, and
a barline standing between a note and its glissando target orphans it however
transparent it is to a word.

Layout says the same thing and has to: a column bearing no syllable leaves an
active extender running and a pending hyphen open, so a melisma is drawn straight
through a rest, a barline and a key change. The walks that close out the end of a
line pass over those columns for the same reason.

## The editor asks for rewrites; the run performs repairs

The repairs above answer "an element changed — make the chains around it
well-formed again". The rewrites an open editor session *asks for* are a level
above that: clearing a placeholder ends a word or gives up a carrier, and typing
the extender character builds a chain backward from a predecessor or forward to a
new carrier.

Those live separately, written in terms of the repairs, taking element positions
rather than controls and touching no toolkit code — so they can be exercised
without a text field on screen. All but one must be called inside a modification
bracket the editor has already opened, because the editor pairs them with its own
commit so that the whole thing is a single undoable step.
