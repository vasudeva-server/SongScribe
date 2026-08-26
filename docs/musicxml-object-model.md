# MusicXML I/O

MusicXML is the current storage format. Reading and writing both go through a
schema-bound object graph rather than streaming XML events: the whole document
exists as objects before anything interprets it, which is what lets both
directions resolve a mark whose two ends sit on different notes by reading both
ends rather than holding one open until the other arrives.

The legacy format is a read-only migration path and is not covered here.

## Only SongScribe documents are read

**Output is for everyone; input is from us.** This program reads MusicXML it
wrote, and nothing else. A gate rejects any
document whose encoding software is missing, blank, or not this program, before
any mapping runs, and the failure is reported to the user as a foreign file
rather than a damaged one.

**Spend no effort on foreign input.** Do not add a mapping, a fallback, a
normalization rule, a model field or a test for a construct this program's own
writer never emits, and do not raise "what if another application wrote X?" as a
design question — it never gets past the gate. The reader owes correctness only
to what the writer produces, including what older versions of the writer
produced.

The write side is the mirror, and the reason the asymmetry exists: what this
program emits must be MusicXML that a *foreign* consumer reads correctly. That is
why the writer's output is schema-validated where the reader's input is not.

The reader's two lenient behaviours are not exceptions to this. Tolerating an
element the model does not know, and reading a document type declaration without
fetching what it names, both exist so an older or hand-edited file of our own
still opens — and so a foreign file is reported as *foreign* rather than as
*damaged*, which is a diagnostic quality rather than a step toward supporting it.

## Writing

The writer builds the whole graph, then revisits it.

```
song ──▶ builders, in document order ──▶ object graph
              (header, measures, notes,        │
               directions, lyrics)             │
                                               ▼
                                    adjustment passes
                          (everything needing more than one node:
                           beams, ties, tuplets, trills, slides, hairpins)
                                               │
                                               ▼
                                          marshalled
```

Builders emit nodes and record **handles** to what they emitted as they go. The
handles are output only — nothing reads back a value an earlier element wrote in
order to decide what to emit next; every read is a later pass revisiting a node
an earlier one finished. A pass that can reach its target through a handle does
so rather than searching a parent's child list.

Each adjustment pass walks one kind of span and attaches to what it touches. Most
share one skeleton, because walking a line's spans of one type, reading both
endpoint indices and dropping the span when either falls outside the line is the
same decision every time. Two stand apart, and for reasons worth knowing: a tie
is the one span that legitimately crosses a line boundary, so only the line being
asked can tell its own element from the previous line's; and slide geometry needs
a laid-out line, so that pass reaches each line *through the spans it is
attaching* rather than looping lines, since laying out a line that has none would
change that line's output as a side effect.

**Pass order is mostly not observable, with one exception.** Several passes write
into the same list of note annotations, which marshals in list order. The schema
admits any order and the reader is order-insensitive, so a round trip agrees with
itself either way; only a textual comparison against a known-good document can
see a difference. What actually orders that list is an insertion ranking applied
as each member is added, not the order the passes run in.

Voltas are not a pass. They are children of barlines, and the piece that owns
barlines attaches them as it closes each line.

## Reading

```
document ──▶ unmarshalled ──▶ format gate ──▶ provenance gate
                                                    │
                                                    ▼
                                    mappers, with mutation tracking suspended
                                                    │
                                                    ▼
                                          load-time fix-up passes
```

Both gates run before any mapping, and both read what unmarshalling already
produced. A document this program did not write is not a supported input, so
nothing is gained by building a song out of one first. A file too damaged to
unmarshal fails before either gate; one of ours whose content is corrupt passes
both and reports the corruption where it is found.

Mapping runs with mutation tracking suspended, so a load records nothing, posts
nothing and leaves the document unmodified. That suspension is also what makes it
safe for the fix-up passes to add spans to lines already committed to the song —
the invariant maintenance that would otherwise interfere is not running — and the
reader restores it afterwards.

Load-time fix-ups run here rather than in the UI because the headless export
route goes through this reader too, and a song whose tuplets were resolved on only
one of the two routes would export differently from how it displays.

**Mapping is one structural walk, then one resolution pass per kind of pairing
mark.** The walk builds only what a single child settles on its own, and records
where each child landed. The passes then run over those recorded positions, which
is what lets a pass ask where an element sits without re-deriving it from the
finished line. Each pass keeps its own cursor as a local and ends with its own
"still open at the end" check, so there is never a question of which flush point a
new mark belongs to.

Order *within* a pass matters even where order between passes does not. A tie's
stop resolves before its start on the same note, so an interior note of a chain
closes its pair and reopens the next. A hairpin does the same, except where a note
opens one that nothing else has open — then the start goes first, so a note
carrying both bounds a single-note hairpin rather than closing one that never
opened.

## What the generated model does not enforce

The generated classes are not a safe boundary, in two directions.

**They admit documents the schema rejects.** A list whose members marshal in list
order will happily marshal them in an order the schema forbids, and a required
attribute left null is silently omitted rather than refused. Both produce a file
that this program's own reader accepts, because it tolerates exactly what the
writer emits — so a round-trip test cannot catch either. Schema validation is the
only check that sees the document the way a foreign consumer would, which is why
it runs on every construct the writer emits.

**They carry no nullability information.** The build's null analysis checks
nothing across them, so an absent value that would be a compile error anywhere
else is a crash at load time instead. Every absent value in the graph is
therefore handled by hand. Three kinds of handling live in one place rather than
being scattered: values whose absence makes the document unreadable, optional
values needing conversion into what the model wants, and the heterogeneous
element lists that have to be unwrapped by name and type. An ordinary null-checked
read of a directly-typed child stays at its use site — routing those through a
helper would add a method per getter and check nothing the caller's own test does
not. A missed check costs a failed open rather than taking the application down,
but that is defence in depth, not a substitute for the check.

The write side needs no equivalent, and the absence is not an asymmetry to fix:
builders construct nodes and set values on them, and the few places that read a
node back are reading nodes the same code created moments earlier.

## Two facts the graph cannot hold

Unmarshalling yields more than the graph, because two things about a document are
not recoverable from it:

- **What the root element was called.** The unmarshaller matches by type, not by
  tag, so a foreign root produces a graph indistinguishable from an empty valid
  one.
- **Whether the version attribute was present.** The schema declares a default,
  so an absent attribute and an explicitly-default one arrive identical — and
  they are rejected with different diagnostics.

Both are read straight off the stream before the graph is built, and the format
gate judges those raw facts rather than anything on the graph. That is why the
gate lives with the reader rather than with the null-handling helpers: it answers
a question the object model cannot represent, rather than reading a value that is
merely absent.

## Where credits go

The writer emits the same value — composer, dates, place — in both the document
head and a display credit. The reader must take the head value and ignore the
credit, or a hand-edited credit corrupts the model. Every credit falls into
exactly one of three classes:

| Class | On read | What is in it |
| --- | --- | --- |
| canonical | read into the model | the subtitle, the score-below credits, attribution placement |
| display-only | ignored, re-derived on write | title, composer, lyricist, arranger, date, rights, place |
| write-forward | ignored, recomputed or constant | encoding metadata, scaling, music font, positioning for foreign renderers |

## Where a key change goes

A key lives in the attributes of the measure it **takes effect in**, and that
position is the whole of the mapping — neither side keeps a running-key field to
reconstruct. A line-boundary change is written into the following line's first
measure; a mid-line change into the measure its preceding barline opened; a
cautionary at the end of a system is rendering only and is written nowhere,
re-derived on read from the next line's key.

A line that inherits its key emits nothing, and the reader leaves it inheriting.
Keying it would round-trip identically and would then silently stop a later edit
to an earlier line's key from propagating past it.

Cancellation and mode are **written and never read**: which accidentals a change
cancels is derived from the change itself, and every key here is major. Both are
emitted anyway, because output is for everyone.

The position invariant for a mid-line key change is **enforced on read**, because
this is the one entry point that takes input from a file; the editing UI maintains
the invariant rather than re-checking it. See
[key-changes.md](key-changes.md).

## The parser fetches nothing

A document type declaration is read, but nothing it names is ever fetched.

Reading the declaration rather than skipping it is deliberate: skipping leaves its
entities *undeclared*, so a document referencing one fails as malformed instead of
behaving the way an ordinary reader behaves. Reading it is what makes the parser
tolerate a perfectly ordinary foreign export.

Refusing the fetch is done by supplying a resolver that returns nothing, **not**
by emptying the allowed-protocol list. An emptied list turns any external
reference into a fatal parse error, which would report every foreign export — they
nearly all carry a declaration naming musicxml.org — as *damaged* rather than as
*foreign*. Do not "restore" that setting.

**The trap:** a resolver must return one of a small set of stream types. Anything
else is discarded *silently*, and the parser then resolves the reference itself —
a real network request to whatever the document names, from whatever machine
opened the file. A declaration pointing at a missing file cannot detect this,
because a failed fetch is tolerated and reported as success either way. Only a
live listener that can be asked whether it was contacted makes "nothing was
fetched" observable, and that is what guards it.

Separately, the unmarshaller aborts on a value that is not the kind of thing it
claims to be — a non-numeric number would otherwise be swallowed and left at its
type default, loading as a valid song with a wrong pitch and no error. Any other
kind of complaint is tolerated, matching the lenience above: a file may carry
constructs this program does not model, and refusing to open it over one of them
is worse than ignoring it.
