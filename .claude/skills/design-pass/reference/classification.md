# Classifying a Contract, and the Confirmation Checkpoint

Step 2 writes every contract in the package. Most can be written straight
through; some may not be decided alone. This file is how to tell them apart and
what to do with the second kind.

The rule this implements is `~/.claude/rules/development.md`:

> Where a promise is a judgment about the domain rather than a mechanical fact,
> it is proposed and confirmed with whoever owns that domain, never decided
> unilaterally. A confident, plausible, wrong contract is worse than no contract,
> because everything downstream is then tested against it.

---

## The two tests

Apply both. Either one answering *domain* makes it domain.

**1. Can you state the promise without asserting a fact about music?**

"Returns the elements between `from` and `to`, `from` inclusive and `to`
exclusive" asserts nothing about music. "A tuplet preserves the absolute playback
duration of the passage it encloses" asserts a great deal.

**2. Would two experienced developers who both read this code write the same
contract?**

If they would differ — and differ *defensibly*, each able to argue their reading
— the promise is a judgment, not a fact you are transcribing.

---

## Mechanical — write it and move on

The promise is determinable from something you can point at:

- **the type system** — nullability, sealed hierarchies, what an enum admits;
- **arithmetic** — geometry, spacing, unit conversion
  (`.claude/guides/spatial-units.md` and `docs/zoom.md` already state the
  governing rules);
- **an external standard** — MusicXML's schema, SMuFL's glyph registry, the MIDI
  spec. The standard is the authority and it is readable;
- **an existing subsystem overview** — `docs/mutations.md`, `docs/lyrics.md`,
  `docs/messages.md`, `docs/undo.md`, `docs/lifecycle.md`. If the arrangement is
  already described there, the method contract cites it rather than re-deciding
  it;
- **collections, parsing, string handling, serialization round-trips**, and
  dispatch or delegation whose only promise is *this call reaches that
  collaborator*.

Pointing at the source is the qualifying move. If you cannot name what makes the
promise true, it is not mechanical — you are reading it off the implementation,
which is the failure mode, not the shortcut.

---

## Domain — propose it, do not decide it

The promise encodes a music-notation judgment. Typical territory:

- what an edit *means* — does deleting a note take its lyric syllable with it,
  its tie, its beam, its tuplet membership;
- rhythm and duration — tuplets, dotted values, what a beat change does to what
  precedes it;
- beaming, ties and slurs — when they form, when they break, where they attach;
- lyrics — melismas, hyphen chains, what a verse index means when verses differ
  in length (`docs/lyrics.md` covers some of this; anything past it is domain);
- pitch spelling, key signatures, accidental scope and carry;
- defaults and limits a musician would notice — a stack depth, a spacing minimum,
  what happens at the edge of a line;
- anything the user would call *wrong* rather than *broken*.

**When in doubt, classify as domain.** The costs are not symmetric. A mechanical
contract you get wrong is caught by the test derived from it, usually within the
hour. A domain contract you get wrong becomes the specification everything
downstream is tested against, and the tests will agree with it.

---

## The checkpoint

Batch the package's domain contracts **one checkpoint per class**, presented
after that class's mechanical contracts are written. Do not interleave them with
other work and do not carry them to the end of the package — a checkpoint at the
end is a checkpoint nobody can act on.

Present each proposed promise as four lines, in plain English rather than
Javadoc. Javadoc at this stage reads as already-decided, which is the thing being
avoided.

```
`Line.deleteElement(StaffElement)`

  Proposing: deleting a note deletes the lyric syllable attached to it, and a
  hyphen chain the syllable was part of closes up rather than leaving a dangling
  hyphen.

  Today: the syllable is deleted; the hyphen chain is left as-is, so a dangling
  hyphen can survive. So the proposal is a change, not a description.

  Also defensible: the syllable survives and re-attaches to the following note,
  which is what a lyricist re-timing a line would expect. I favor the first
  because the syllable belongs to the note, not to the position, but this is the
  call I cannot make.
```

The four parts and why each is there:

1. **Proposing** — the promise, stated so a caller could rely on it.
2. **Today** — what the implementation actually does. When the proposal and the
   implementation agree, say so explicitly; that is a fact the reviewer needs, and
   it is also your own check against §4.2, since a contract the code cannot
   violate is describing the code.
3. **Also defensible** — the readings you rejected. Omitting these is what makes
   a wrong contract look right. If you genuinely found no alternative, say that
   rather than leaving the line out.
4. **Where it came from** — fold into the above, but be explicit when a proposal
   came from reading the method body rather than from the domain. That is not
   disqualifying, and hiding it is.

Then ask, per class, for accept / correct / reject on each. Write the accepted
form; where one is corrected, write what the reviewer said rather than a
paraphrase of it.

**A rejected proposal is a finding, not a dead end.** If the promise cannot be
stated, the API is probably wrong — surface that rather than writing a weaker
contract to have something to write.
