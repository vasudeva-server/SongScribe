# Initial Tempo Transfer

Background and rationale for the starting-tempo behavior implemented for issue #731 — the
design decisions behind it, not a walkthrough of the code.

------------------------------------------------------------------------

## What the anchor is

A tempo change notates a change *from* an established tempo *to* a new one. The song's very
first tempo has nothing before it to change from, so it cannot be a tempo *change* in the
notational sense — it has to be the song's tempo, full stop. `Song` therefore keeps a
song-level `tempo` field distinct from the per-note `TempoChangeAttachment`s that appear
everywhere else, and designates exactly one position in the document — the **anchor** — as the
place that song-level tempo is rendered as a mark.

The anchor is the first element of the song's first **non-empty** line
(`Song.firstNonEmptyLineIndex()`, `Song.initialTempoAnchor()`, `Line.isInitialTempoAnchor(int)`).
"First non-empty" rather than "line 0" matters because line 0 can be emptied by an edit while
later lines still hold elements — deleting everything on line 0 does not delete the song's first
note, it just moves it to line 1.

A `TempoChangeAttachment` anywhere else in the song is an ordinary tempo change: it describes a
change from whatever tempo was in effect. The one at the anchor is different in kind, not degree
— it is the mirror of `Song.getTempo()`, not a change from a prior tempo. That distinction is
why an edit that displaces the anchor cannot simply let the attachment vanish with the note it
was sitting on: if a later real tempo change survives, it would be left instructing a change from
nothing, and the score would no longer parse as notation. This document covers the machinery
that keeps the anchor attachment following the song's first element whenever an edit changes
which element that is.

------------------------------------------------------------------------

## The two mirrored representations, and two opposite-direction load paths

The starting tempo lives in two places that must agree: `Song.tempo` (a `@Nullable Tempo`) and a
`TempoChangeAttachment` on the anchor element. Both current-format load and legacy-format load
populate both, but in opposite directions — which is why neither direction can be changed
casually without checking the other still holds.

```
  MusicXML (current format)                    Legacy .mssw (read-only migration)
  ─────────────────────────                    ──────────────────────────────────
  MusicXmlReader                               SongIO.DocumentReader.getSong()
    → Song.newParsingStub()                      → Song.loadFrom(SongData)
    → builds lines directly                        → tempo = data.tempo()
    → headerReader.applyInitialTempo()             → first non-empty line
        anchor.attachment ──► Song.tempo               .attachInitialTempoIfNeeded()
                                                          Song.tempo ──► anchor.attachment
    NEVER calls loadFrom                         ONLY path that calls loadFrom
```

`MusicXmlHeaderReader.applyInitialTempo` is the only `setTempo` caller anywhere in the musicxml
package, so during a MusicXML load `Song.tempo` starts null and is written exactly once, from
the anchor's attachment. Its body is a single call to `Song.syncTempoFromAnchor()` — the one
statement of the anchor→`Song.tempo` rule, reused by every edit path that displaces the anchor
(see below).

`Line.attachInitialTempoIfNeeded()` runs the mirror the other way, and only from
`Song.loadFrom`. The `.mssw` format stored the tempo song-level only, with no per-note
attachment at all, so this is what materializes the tempo mark the first time a legacy file is
opened. It is a strict no-op when `song.getTempo()` is null — it never fabricates a tempo on a
song that has none — which is why it is safe to leave in place as permanent migration machinery
rather than something to prune once old files stop appearing.

Because the two directions are triggered by disjoint load paths (`MusicXmlReader` never calls
`loadFrom`; `SongIO` is the only caller of `loadFrom`), there is no path where both run against
the same load, and no risk of one clobbering the other's result mid-load.

------------------------------------------------------------------------

## The transfer rule, and its two directions

Editing can change which element is first in the song in exactly two shapes: something is
removed from in front of the anchor (delete, line-delete, paste-over-a-selection), or something
is inserted in front of it (insert, paste-at-a-target). Both shapes reduce to the same question —
"what happens to the displaced `TempoChangeAttachment`, now that some other element is the
song's first?" — so the DOM answers it with one shared rule rather than one rule per call site.

```
                     ONE RULE, TWO DIRECTIONS

    DELETE side                              INSERT side
    ───────────                              ───────────
    reanchorInitialTempo, removeLine         addElement
      a removal took the anchor away           an insertion makes `element`
                                               the song's new first element
              │                                        │
              ▼                                        ▼
    transferInitialTempoToAnchor       transferInitialTempoToIncomingElement(
      (displacedTempo, newAnchorLine)     displacedTempo, incomingElement)
              │                                        │
              ╰────────────────╮      ╭────────────────╯
                               ▼      ▼
                       targetAcceptsInitialTempo?
                   no → drop the displaced tempo (target wins)
                  yes → attach a copy of it to the target
              │                                        │
              ▼                                        ▼
    routed through the new anchor          attached plainly: the element is
    line's modifyElement(0,                not in the document yet, so the
    ElementField.TEMPO_CHANGE, …)          ElementInsertion the caller is about
    so undo sees the field change          to record captures it already attached
```

Every displacement call site — `Line.removeElement`, `Line.removeRange` (via the private
`reanchorInitialTempo`), `Song.removeLine`, and `Line.addElement(int, StaffElement)` — resolves
its own displaced attachment and its own target, then hands both to whichever of the two methods
matches its direction, rather than re-deriving "does the target already have a tempo" at each
site.

The split into two named methods is deliberate. The only difference between the directions is
whether the attach needs a mutation record of its own, and that turns entirely on whether the
target is in the document *yet* — a timing condition a single method could only infer from when
it was called. Inferring it would make a future caller that passed an in-document element not
yet at index 0 take the untracked path silently: the forward edit would look right, and undo
would leave the moved tempo behind.

The DOM's default in every collision is **the target wins**: if the prospective new first
element already carries a tempo change of its own, that is left alone and the displaced tempo is
simply dropped. That is the shared `Song.targetAcceptsInitialTempo` both directions consult. The
DOM never asks the user anything — it always has to produce *some* answer synchronously, so it
picks the conservative one and lets the UI layer (`InitialTempoConfirms`, in `songscribe.ui`)
ask the user whether "target wins" is actually what they want before the mutation runs, and undo
the DOM's default afterward when they say no.

Neither direction is routed through `Song.withBeatDefiningEdit`, and the reason belongs to the
rule rather than to either call site: this is the one beat-defining write that cannot change the
beat anywhere. The tempo leaves the song's first element and lands back on the song's first
element carrying the same `Tempo`, so `resolveBeatAt` returns the same beat for every position
in the song and no tuplet can be invalidated. That holds across lines too — what matters is that
the tempo stays at the head of the song, not which line the head happens to be on.

`InitialTempoTransfer` (also `songscribe.dom`) is the read side of that split: it answers "what
*will* be the song's first element if this pending edit runs" for UI code that has to decide
*before* mutating anything — `anchorAfterRemoval`, `anchorAfterLineRemoval`,
`currentInitialTempo` — plus `replaceInitialTempo`, the one mutator that applies a user's answer
to restore the original tempo after the transfer already let the target's tempo win. The two
classes divide cleanly: the `Song` transfer methods are what the DOM does automatically mid-edit;
`InitialTempoTransfer` is what UI code consults before, and undoes after, that automatic default.

Two edge cases fall out of the same rule rather than needing special-casing:

- **Cross-line displacement.** Emptying line 0 while line 1 still holds elements does not stop
  at "the tempo disappeared" — `reanchorInitialTempo` resolves the new anchor through `Song`
  (the first element of whatever line is now first non-empty), which may not be the line the
  removal happened on.
- **Sole-line removal.** Removing a song's last remaining line would leave it with no lines, so
  `Song.removeLine` puts a fresh one in its place, carrying the terminal barline. The transfer
  runs *before* that replacement, against a song that momentarily has no lines at all —
  `transferInitialTempoToAnchor` no-ops when either the displaced attachment or the new anchor
  line is null — so the tempo is dropped rather than landing on a barline. This case therefore
  needs no branch of its own; it falls out of the same null checks that handle "nothing to
  move", and the ordering that puts the transfer ahead of the replacement is what makes it so.

------------------------------------------------------------------------

## Why the confirm's outcome has to land inside the caller's outer bracket

`InitialTempoConfirms.applyDecision` — which applies the user's Yes/No/silent-transfer answer
and then calls `Song.syncTempoFromAnchor()` to bring `Song.tempo` back in step — must run inside
the *same* modification bracket as the edit that triggered the question, not in a bracket opened
afterward. Every wiring site (element-range delete, line delete, both paste paths) follows this
shape:

```
  ⓪  does this edit displace the song's first element at all?
        no → none of the steps below run
  ①  resolve the prospective new first element
  ②  capture the ORIGINAL Tempo — before any mutation, because the attachment
        holding it may be gone afterwards
  ③  InitialTempoConfirms.confirmTransfer(...)
        CANCEL → return immediately, mutating nothing
  ④  song.withModification(label, () -> {        ← ONE outer bracket
          <the edit>                             ← its own bracket nests inside
          InitialTempoConfirms.applyDecision(song, originalTempo, decision)
      })
  ⑤  InitialTempoConfirms.warnIfTempoAndBeatChange(score, song)   ← AFTER the bracket
```

Step ⓪ gates step ⑤ as much as it gates the question. The warning's text says the first note
"now" has both a tempo and a beat change, and a song can already be in that state without any
edit being involved — the beat-change dialog will attach a beat change to any note, including
the first. Without the gate, every delete and every paste anywhere in the piece would raise a
modal warning blaming the user's edit for a state it did not create. The two delete sites read
the condition from `Line.isInitialTempoAnchor`; both paste sites read it from
`ScoreViewController.pasteDisplacesSongFirstElement`, which has to be asked *before* the paste
runs, since afterwards the anchor has already moved.

Each side reads the predicate its own DOM half gates on, and the two are not the same. The
removal side asks `isInitialTempoAnchor` — "is the tempo sitting here now" — because that is
what `Line.removeElement` / `removeRange` / `Song.removeLine` ask. The insertion side asks the
wider `insertionMakesSongFirstElement` — "will this element lead the song once it is in" —
because that is what `Line.addElement` asks. Gating the insertion side on the narrower predicate
is a real bug, not a stylistic difference: a paste into a leading empty line would then never
prompt, while `addElement` went ahead and displaced the anchor from the line below anyway, so
the user would silently lose their starting tempo to the pasted note's own.

`Song.withModification` nests — the op-name label is captured only at the outermost bracket —
and some of the edits it wraps (`deleteElementRange`, for instance) open their own bracket
internally that the caller cannot reach into. A caller that needs its own mutation recorded in
the same undo step therefore has to open the outer bracket itself, passing `null` as the inner
call's own label, rather than opening a second bracket once the inner one has closed.

Opening a second `withModification` after the edit instead of reusing one outer bracket is wrong
in a way that only shows up under Undo, not under the forward edit: it produces **two** separate
undo steps instead of one. Undo pops the most recent step first, so undoing would strip the tempo
replacement back off the new first element but leave the edit that displaced the original anchor
already applied — a single logical action (the edit *and* the user's answer about its tempo)
would come apart into two, and the intermediate state (edit applied, tempo replacement undone) is
not a state the user ever asked for or saw.

`warnIfTempoAndBeatChange` has the opposite constraint for the opposite reason: it raises a modal
dialog, and a modal dialog opened from inside an open modification bracket would block the EDT
while the bracket is still open, which none of `Song`'s mutation-tracked call paths are written
to tolerate. It always runs as step ⑤, after the bracket that step ④ closed.

The element-range delete and the line delete share this shape through
`ScoreViewController.editWithInitialTempoOutcome`, which takes the undo label, the answer and the
edit as a runnable. Encoding steps ④ and ⑤ once matters more than the line count it saves: stated
twice, one copy can drift into the second-bracket mistake above, and that mistake is invisible
under the forward edit.

------------------------------------------------------------------------

## Why `Tempo.haveSameValue` exists rather than `equals`

`Song.syncTempoFromAnchor()` needs to compare two `Tempo` instances by value — it is the only
caller, since the transfer methods only ask whether the target already carries *any* tempo
change, never how its value compares. A transferred or synced tempo is always a fresh copy
(`TempoChangeAttachment.copy` returns `new TempoChangeAttachment(newOwner, tempo.copy())`), and
`Song.setTempo` routes through `mutateMetadata`, which early-returns on `Objects.equals` — an
**identity** comparison, since `Tempo` declares neither `equals` nor `hashCode`. Without a value
comparison first, syncing an anchor tempo that has not actually changed would still look like a
change to `mutateMetadata`, recording a spurious `MetadataChange(TEMPO, …)` into the undo step
and running `withBeatDefiningEdit` — which can drop tuplets and warn the user — for an edit that
changed no beat at all.

The fix is `Tempo.haveSameValue(@Nullable Tempo a, @Nullable Tempo b)`, a null-safe static
comparison of `visibleTempo`, `tempoType`, `tempoDescription` and `showTempo`, used as an early
return before the `setTempo` call. It is deliberately **not** `Tempo.equals`/`hashCode`:
`Tempo` is mutable — four setters — so giving it value equality would be unsafe the moment an
instance ever entered a hash-based collection (a `HashSet`, a `HashMap` key) and then had one of
those fields mutated out from under its stored hash code. Adding real `equals`/`hashCode` support
for mutable value-ish types across the DOM is a broader gap than this feature, tracked separately
as #740; `haveSameValue` is the narrow, safe tool this feature needs without pre-empting that
decision.
