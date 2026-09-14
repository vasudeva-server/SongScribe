# Accidentals

What a note sounds, when it carries a written accidental, and who decides. For
what a key change does to accidentals, see [key-changes.md](key-changes.md); for
what a paste keeps, see [clipboard.md](clipboard.md).

## What is in force

A written accidental stays in force at its staff position until an accidental
written at that position replaces it or a key change ends it. Nothing else ends
it: barlines, repeats, line breaks, rests, breath marks and notes at other
positions change nothing about what is in force. Where nothing is in force a
note sounds its key's alteration for its pitch class — natural where the key
does not alter it. After any note, what is in force at its position is that
note's sounding alteration, whatever its state.

## The five states

Every note is in exactly one state, decided from three facts: what it
*sounds*, what is *in force* before it at its position, and what the *key*
says for its pitch class.

- **In key.** Sounding equals the key's, and the key's is in force. Not written.
- **Anchor.** Sounding differs from what is in force and from the key's. Written.
- **Cancel.** Sounding differs from what is in force and equals the key's. Written.
- **Repetition.** Sounding equals what is in force, which differs from the key's,
  and something intervenes. Written.
- **Implicit.** Sounding equals what is in force, which differs from the key's,
  and nothing intervenes. Not written.

Something **intervenes** before a note when the element immediately before it,
skipping rests and breath marks, is a note or grace note at another staff
position, a barline or a repeat, or when the note is the first element of its
line. A tie changes nothing.

A **chain** is the notes at one staff position from an anchor up to, and not
including, the next anchor or cancel at that position, or the next key change.
A chain crosses barlines, repeats and line breaks; its notes need not be
adjacent, since notes at other positions sit between them without belonging.

The rule is **strict**: a note is written exactly when its state says so,
silently. The notator is never asked which accidental to write, and never asked
to confirm one.

Worked example in C major, one line, staff position of F throughout:

```
F   F♭  F   G   F♭  F   F♮  E   F
1   2   3   4   5   6   7   8   9
```

1 in key; 2 anchor; 3 implicit; 5 repetition (the G intervenes); 6 implicit;
7 cancel; 9 in key. Notes 2, 3, 5 and 6 are one chain. Pressing natural on 5
makes 5 a cancel and 6 an anchor; deselecting the flat on 2 makes 2 in key and
3 the anchor; pressing flat on 7 makes 7 implicit and 9 a cancel.

## Every note stores its pitch and its state

A note carries its sounding pitch and its state as stored facts. Nothing is
derived at render time: the renderer draws an accidental where the state says
one is written, and reads nothing to the left of the note to find out. The
pitch is what the note sounds, not what is written on it, so an implicit note
sounds its chain's alteration while showing nothing.

## The song decides

The song performs the chain walk after every recorded change. No edit decides
states for itself, and none can skip the walk: an edit changes pitches and
elements, then the song decides what is written.

The walk starts from the change and, for each staff position the change
touched, stops once the first untouched note at that position is decided —
past that note nothing can have moved, because what is in force after it is
its own sounding alteration, which the edit did not change. When the change
moved a key it instead walks every note from the change to the next key change,
because a key change ends everything in force and every note past it is decided
against a key that may have moved.

Undo replays what the walk decided rather than walking again, so an undone edit
restores exactly the states the notator saw before it.

## An edit measured for fit is measured with its accidentals

Some edits are measured for fit before they mutate anything: preview insertion,
preview replacement, paste, and any selection action that widens a column. Such
an edit also decides states on a projection of the song as it will stand, and is
refused when a line it would widen can no longer hold its accidentals. A written
accidental is layout width, so measuring without it would pass edits that then
overflow.

Every other edit's states may leave a line wider than its fit. That is the
ordinary best-effort behaviour: the line renders overflowing and flagged rather
than the edit being refused.

## What a note adopts and what it keeps

Every note keeps its pitch through every edit, with two exceptions. A note the
edit itself pitches sounds what the edit says. A note that has no pitch of its
own — a fresh note, and a note moved to another staff position by the arrow
keys or a drag — adopts what is in force where it lands. A pasted note keeps the
pitch it had where it was copied from; what is written on it after the paste is
the song's decision, like every other note's.

## Accidentals are not selectable

An accidental is part of its note. It has no hit region of its own, cannot be
selected, and is changed only through the accidental actions on the note that
carries it.

## Load

A file is reread under this policy on load. A note the file writes an
accidental on sounds that accidental; a bare note adopts what is in force at
its position; parentheses are ignored. The song then decides every note's state
as it does after any change, so a file written under any earlier policy opens
with the accidentals this one writes.
