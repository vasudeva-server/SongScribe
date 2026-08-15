# MusicXML Object Model — Build/Read Pipeline

Reference diagrams for the MusicXML I/O path (`songscribe.io.musicxml`), which reads and
writes through a schema-bound object model, `org.audiveris:proxymusic`
(`ScorePartwise`), rather than streaming SAX/XMLStreamWriter events. The classes
themselves carry prose summaries and point here.

MusicXML is the current storage format; `SongIO` and the other legacy-format classes in
`songscribe.io` are read-only migration paths and are not covered here.

---

## Only SongScribe documents are read

**This program reads MusicXML it wrote, and nothing else.** `HeaderMapper.checkProvenance`
rejects any document whose `<software>` is missing, blank, or not SongScribe — before any
mapping runs — and `SongFileLoader.load` reports it to the user as
`SongLoadResult.WrongSoftware`. There is no supported path by which another application's file
reaches a mapper.

**Spend no effort on foreign input.** Do not add a mapping, a fallback, a normalization rule,
a model field or a test for a construct this program's own writer never emits, and do not raise
"what if another application wrote X?" as a design question — it never gets past the gate. The
reader's only obligation is to what the writer produces, including what older versions of the
writer produced.

The write side is the mirror image and the reason the asymmetry exists: what this program emits
must be MusicXML that a foreign consumer reads correctly, which is what
`MusicXmlSchemaValidator` checks on every construct the builders emit. **Output is for
everyone; input is from us.**

The reader's two lenience rules are not exceptions to this. Tolerating an element the model
does not know, and reading a `DOCTYPE` without fetching what it names, both exist so an older
or hand-edited SongScribe file still opens — and so a foreign file is reported as *foreign*
rather than as *damaged*, which is a diagnostic quality, not a step toward supporting it.

---

## The write pipeline

```
Song ──► ScorePartwiseBuilder.build(BuildContext)
           │
           │  BuildContext = (Song, DocumentFontsHolder, ObjectFactory,
           │                  LineLayoutProvider, BuildIndex)
           │
           ├─► HeaderBuilder     ─┐
           ├─► MeasureBuilder    ─┤
           ├─► NoteBuilder       ─┼─► ScorePartwise graph, with handles
           ├─► DirectionBuilder  ─┤   recorded into BuildIndex as they go
           └─► LyricBuilder      ─┘
                       │
                       ▼
           adjustment passes (ScorePartwiseBuilder.runAdjustmentPasses)
           ── every operation needing more than one node: beams, ties,
              tuplets, trills, glissandos, hairpins ──
                       │
                       ▼
           MusicXmlSerializer.marshal ──► PrintWriter
```

`ScorePartwiseBuilder.build` constructs the `BuildContext` once and drives the builders in
document order — movement info, identification, defaults, credits, `<part-list>`, then the
part itself — exactly as `MusicXmlWriter.writeSong` used to walk the stream. Endings (voltas)
are attached to their `<barline>` as `MeasureBuilder` closes each line, not by a later pass —
see [Endings are not a pass](#endings-are-not-a-pass) below.

## The read pipeline

```
InputSource ──► MusicXmlSerializer.unmarshal ──► ParsedDocument(score, rootElement, version)
                       │
                       ▼
           SongMapper.map ──► checkFormat (format gate, from rootElement/version)
                       │
                       ├─► HeaderMapper.checkProvenance (provenance gate)
                       │
                       ├─► HeaderMapper  ─┐
                       ├─► MeasureMapper ─┼─► Song, built while mutation
                       └─► NoteMapper    ─┘   tracking is suspended
                       │
                       ├─► Song.installTerminalAfterParsing
                       ├─► Line.repairGraceHostMelismas (every line)
                       └─► TupletLoadPass.run(song)
```

Both gates run before any mapping, and both read what unmarshalling already produced. A
document this program did not write is not a supported input, so nothing is gained by
building a `Song` out of one first: a foreign file reports *foreign* even when its content
would also have tripped a mapper, and the mapping work is not done at all. A file too
damaged to unmarshal has failed before either gate; a SongScribe file whose content is
corrupt passes both and reports the corruption a mapper finds.

`MusicXmlReader.read(InputSource)` is `SongMapper.map(MusicXmlSerializer.unmarshal(source))` —
one line. `read(File)` remains a one-line delegation to `read(InputSource)`, so there is one
parse path, and every round-trip test in the package exercises it.

`SongMapper.map` suspends mutation tracking with `song.beginSuspendMutationTracking()` /
`endSuspendMutationTracking()` around the mapping and the two load-time fix-up passes, in a
`try`/`finally` rather than `Song.withoutMutationTracking(Runnable)` — the mappers throw a
checked `SAXException`, which a `Runnable` cannot carry. A load therefore records no
mutations, posts no notification, and sets no modified flag, exactly as before.

`TupletLoadPass` runs here rather than in the UI: `SongLoader`'s headless MIDI-export route
goes through this reader too, and a song whose tuplets were resolved in only one of the two
routes would export a different MIDI file than it displays — see `SongLoader.load`'s own
documentation of this.

### `MeasureMapper`: walk, then resolve

`MeasureMapper` is the read-side mirror of [the adjustment passes](#the-adjustment-passes), and
for the same reason: the whole part is parsed before mapping starts, so a mark whose two ends sit
on different notes can be settled by reading both ends rather than by holding one until the other
arrives.

It runs **one structural walk** followed by **one resolution pass per pairing mark**. The walk
builds only what a single child settles on its own — it starts a line at each `<print
new-system>`, applies each `<key>`, appends an element per barline, and calls
`NoteMapper` per note — and records what each child produced in a `ChildSite`: the line current at
that point, the last element the child appended, the element an `<ending>` marker or an annotation
`<direction>` binds to, and the `NoteSite` when the child was a note. `ChildSite` is the read-side
twin of `BuildIndex`: it is what lets a pass ask where an element sits in the document without
re-deriving it from the finished `Line`.

The passes then run over the finished sites, in this order: endings, annotations, metronome
directions, hairpins, then the five per-note spans (slides, beams, ties, tuplets, trills). Order
between them is not observable — each attaches to a distinct model slot — but each pass's own
internal order is:

- **A tie's stop resolves before its start** on the same note, so an interior note of a chain
  closes its pair and re-opens the next.
- **A hairpin's stop resolves before its start**, *except* when a note opens a hairpin nothing
  else has open — then the start goes first, so one note carrying both bounds a single-note
  hairpin rather than closing a hairpin that never opened.
- **The first tempo mark of the first measure is the song's own** and binds to no note. "First
  measure" is the first measure with any content, in document order, not the one numbered 1 — a
  foreign file is not obliged to start at 1.
- **A tie may cross a line boundary**; a hairpin and a volta may not. The five per-note span
  passes therefore run over the part's whole note list and add each span to the line of its
  *closing* note, while the hairpin and ending passes reset at every line change.

Nothing is held on the mapper. Each pass keeps its cursor as a local, and its "still open at the
end" check is the last statement of the method that opened it — so there is no question of which
of several flush points a new mark belongs to. The mapper's own state is three fields: the line
being built, the measure index that line started at, and the accidentals-converted flag.

### Where a key signature lands, in both directions

A `<key>` lives in the `<attributes>` of the measure the key **takes effect in**, and that
position is the whole of the mapping — there is no running-key field on either side that a reader
has to reconstruct.

| Model | Written into | Read back as |
|---|---|---|
| `Line.getKey()` non-null | that line's first measure | `Line.setKey` on the line the measure opens |
| `KeyChangeElement` | the measure its preceding barline opened | a `KeyChangeElement` at the current position |
| cautionary at end of a system | nothing — rendering only | re-derived from the next line's key |

A line that inherits its key emits no `<key>`, and the reader leaves it inheriting rather than
keying it to a carried-forward value. Keying it would round-trip identically and would then stop a
later edit to an earlier line's key from propagating past it;
`Song.rebuildInheritedKeysAfterParsing` settles the inheritance once the walk is done.

`<cancel>` and `<mode>` are **written and never read**. Which accidentals a change cancels is
derived from the change by `songscribe.dom.KeyChange` — the writer asks that policy rather than
restating "when the key type differs", so the two cannot drift — and every SongScribe key is
major, so `<mode>` carries no information the model could store. Both are emitted because
[output is for everyone](#only-songscribe-documents-are-read).

`KeyChangeElement`'s position invariant (always immediately after a barline or repeat) is
**enforced on read**, with `DocumentValidation.corrupt`. It is the one entry point that takes
input from a file; the editing UI and the deletion pairing maintain the invariant rather than
re-checking it, so a mid-measure `<key>` with no barline before it would otherwise load
invariant-violating with no error and no visible symptom.

Spans are added to lines that have already been committed to the song. That is safe only because
a load suspends mutation tracking, which is what stops `Song.addLine` from maintaining the
terminal invariant while the passes run; `SongMapper` restores it afterwards with
`Song.installTerminalAfterParsing`.

### How to add a read pass

1. Add a private static method to `MeasureMapper`, named for what it resolves (`resolveFoo`).
2. Read the marks off the parsed nodes through a `NoteMapper` accessor (`tieMarker`,
   `tupletMarker`, …) — one per span kind, each returning exactly what its pass consumes. Do not
   route new markers back through `NoteMapper.map`'s return value: that is what produced the
   14-field marker record the streaming reader needed.
3. Take `List<NoteSite>` for a per-note span, or `List<ChildSite>` when the pass needs document
   position — a `<direction>` binding forward to a note, or a marker riding on an invisible
   barline.
4. Keep the pass's cursor as a local, and end the method with the diagnostic for anything left
   open (`warnDanglingStart` / `warnOrphanStop`, or a `SAXException` where the state is corrupt
   rather than merely incomplete, as tuplets are).
5. Add it to `mapPart`'s call sequence, and say in a comment if its position there is observable.

---

## `BuildContext` and `BuildIndex`

```java
record BuildContext(Song song, DocumentFontsHolder fonts, ObjectFactory factory,
                     LineLayoutProvider layoutProvider, BuildIndex index) {}

record BuildIndex(Map<StaffElement, Note> notes) {}
```

`BuildIndex` collects **output handles**, recorded by the builders as they emit nodes — it is
not carried input state the way `NoteWriteContext` was in the streaming writer. Nothing reads
a value from it that an earlier element wrote in order to decide what to emit next; every read
is a later pass revisiting a node an earlier pass already finished.

Two heterogeneous `List<Object>`s in the generated model —
`Measure.getNoteOrBackupOrForward()` and `Notations.getTiedOrSlurOrTuplet()` — can be walked
with `instanceof`, and `MeasureBuilder`/`NoteBuilder` do exactly that where they must (finding
the note a breath mark folds into, finding the barline a forward-repeat has to precede). But a
pass that can reach its target through a `BuildIndex` handle instead does so: `applyBeams`,
`applyTies`, `applyTuplets`, `applyTrills`, `applyGlissandos` and `applyHairpins` (see below)
all look their notes up in `BuildIndex.notes()` rather than scanning a measure's child list.

`MeasureBuilder.ElementOutput` is the second handle record, and it is per element rather than
per note: which measure the element's content went into, its `<note>`, its annotation
`<direction>`, and the barlines that belong to it. **`MeasureBuilder` owns the whole element
loop** — where the measure boundaries fall *and* what goes between them, calling `NoteBuilder`,
`DirectionBuilder` and `LyricBuilder` itself. That is what makes one record enough: the code
that appends a node is the code that records it. An earlier draft split the loop between
`MeasureBuilder` and a callback in `ScorePartwiseBuilder`, which left `MeasureBuilder` deducing
what had been appended by comparing the measure's child count before and after the call, and
left `ScorePartwiseBuilder` keeping a second map of the same fact for the hairpin pass.

The two places that must insert a node at a recorded position — the invisible barlines a
note-anchored or note-terminated volta rides on, and a hairpin's wedge `<direction>` — find it
with `MeasureBuilder.positionOf`, which compares by **identity**. `List.indexOf` would give the
same answer today only because the generated classes define no equality: the same assumption
`BuildIndex.notes` guards against, and there is no reason to leave it unguarded here.

`notes` **must** be an `IdentityHashMap`, and the record's compact constructor rejects anything
else. A plain `HashMap` gives identity semantics today only because `StaffElement` defines no
`equals`/`hashCode` — deliberately absent so identity-based hash collections and layout caches
keep working. If value equality is ever added there, a `HashMap` would collide two equivalent
rests and silently attach a span to the wrong note: a mistake that would compile, pass every
test, and show up only as a corrupt saved file. The guard turns it into an immediate failure at
the one place that could introduce it.

`BuildIndex` deliberately carries **nothing a pass does not read**. An earlier draft also filed
every emitted `<lyric>` into a per-verse table for GitHub issue #761's `<end-line/>` pass to use
later. Nothing read it, so nothing could tell whether its ordering or its verse keying was
right, and #761 would have inherited a table vouched for by a comment and by no assertion. When
that issue is built, its author adds the shape it needs, informed by the code that consumes it.

---

## The adjustment passes

| Pass | Walks | Attaches to |
|---|---|---|
| `applyBeams` | `forEachSpanInRange(…, Beam.class, …)` | `Note` via `BuildIndex.notes()` |
| `applyTies` | `line.findTies()`, resolved through `SpanBound` | `Note` |
| `applyTuplets` | `forEachSpanInRange(…, Tuplet.class, …)` | `Note` |
| `applyTrills` | `forEachSpanInRange(…, Trill.class, …)` | `Note` |
| `applyGlissandos` | its own `GlissandoSite` list | `Note` (slide start/stop) |
| `applyHairpins` | `forEachSpanInRange(line, Hairpin.class, …)` | `Direction` (wedge) |

`ScorePartwiseBuilder.runAdjustmentPasses` runs them in this order, once per line, after every
builder has finished emitting that line's notes and directions. This replaces `MusicXmlSpanIndex`
entirely: that class bucketed six span types onto element indices so the streaming loop could
read one array slot per element as it passed. With a finished graph, each pass instead walks its
span list once and attaches directly to what it touches — no precompute, no index to keep in
sync with what the writer actually emits.

**Four of the six share one skeleton, `forEachSpanInRange`.** Walking a line's spans of one type,
reading both endpoint indices and dropping the span when either falls outside the line is the
same decision every time, and it was written four times before. The helper holds it once and
hands the action the line, the span and the two checked indices, so each pass is only what it
attaches. The dropped-span rule is the part worth centralizing: `Span.getAnchorElementIndex()`
answers from whichever line the endpoint element belongs to, so a span left dangling by an edit
reports an index that means nothing in the line being written, and attaching its marker anyway
would mark an unrelated note.

The two that stand outside it stand outside for a reason. **`applyTies` resolves its endpoints
through `line.anchorIndexOf`/`endIndexOf`** and matches on `SpanBound.At` instead — a tie is the
one span that legitimately crosses a line boundary, and only the line being asked can tell "my
element 7" from "element 7 of the previous line" (#493). A range check would be the wrong
question. **`applyGlissandos` iterates glissandos, never lines** — see below.

**Pass order is load-bearing.** Beams, ties, tuplets and trills all write into the same
`Notations.getTiedOrSlurOrTuplet()` list, which marshals in list order, and nothing else catches
a wrong one: `<notations>` content is an unbounded choice in the schema, so any order validates,
and the reader is order-insensitive, so a round-trip agrees with itself either way. Only a
textual comparison against a known-good document can see a difference —
`ScorePartwiseBuilderTest.testNotationsChildrenMarshalInThatOrder` is that comparison.

In practice the list order is not the passes' call order but **`NoteBuilder.addNotation`'s
insertion ranking** — it inserts each member at the position its element type occupied in the
streaming writer's emission order (tied, slide, tuplet, ornaments, articulations, dynamics,
fermata), so the six passes above may run in whatever order suits them. Members of equal rank
keep their insertion order, which is what keeps a tie chain's `<tied type="stop">` before its
`<tied type="start">`.

**`applyGlissandos` iterates glissandos, never lines**, and the difference is not stylistic.
Slide endpoint geometry comes from the line's `LayoutResult`, and a line with no glissando is
deliberately never laid out — a layout costs real work and resolves automatic stem directions as
a side effect, so laying out a line the old path never touched would change that line's output.
Looping lines and asking `layoutProvider` for each would lay out the whole song on every save.
Each glissando site reaches its line through the glissando itself, so a line with none is never
reached.

### Endings are not a pass

`<ending>` is a child of `<barline>`, and `MeasureBuilder` owns the barlines — it attaches an
ending's volta marker to the barline object it belongs to as it closes each line, using
positional insertion (`items.add(0, forwardRepeatBarline)`) where the emitted format genuinely
requires a barline at a position the old streaming loop had already passed. There is no seventh
`applyEndings` pass; `ScorePartwiseBuilder.runAdjustmentPasses`'s Javadoc says so explicitly, so
a reader looking for one does not go hunting.

### How to add a pass

1. Add a private static method to `ScorePartwiseBuilder`, named for what it attaches (`applyFoo`).
2. Walk the spans with `forEachSpanInRange` rather than writing the loop again — the bounds check
   and the drop-a-dangling-span rule come with it, and the body is then only the attachment. Take
   the `List<LineOutput>` overload for a pass that has no per-line state, the single-`Line`
   overload when it does (`applyHairpins` buckets per line). For a per-note attachment, walk the
   notes directly — never a measure's child list — and reach each target through `BuildIndex`
   where a handle exists.
3. Attach a `<notations>` member through `NoteBuilder.addNotation`, not by adding to
   `getTiedOrSlurOrTuplet()` directly, so its emission rank is honored automatically.
4. Add it to `runAdjustmentPasses`'s call sequence, and say in a comment whether its position in
   that list is observable in the output (it usually is not — `addNotation`'s ranking is what
   actually orders `<notations>` content — but a pass that writes into an *ordered* typed list,
   the way endings and directions do, must call out its own ordering requirement explicitly).
5. If the pass needs layout geometry, follow `applyGlissandos`'s guard: reach the line through
   the span, never loop lines directly, so a line the pass has no reason to touch is never laid
   out.

---

## What the generated classes do not enforce

Two things pass compilation, marshal without error, and even round-trip through this program's
own reader unchanged — because our own reader tolerates them the same way it wrote them. Only
`MusicXmlSchemaValidator` catches either, which is why it runs on every construct these builders
emit.

**Choice-branch ordering.** `Lyric.getElisionAndSyllabicAndText()` is a `List<Object>` whose
`@XmlElements` annotation admits exactly `Elision`, `Syllabic` and `TextElementData`, marshalled
in list order. Adding `[TextElementData, Syllabic]` marshals as `<text/><syllabic/>` with no
error — `LyricBuilder` has to add the `Syllabic` value before the `TextElementData` and say why
in a comment. `MusicXmlSchemaValidator` reports it as: *"Element 'syllabic': This element is not
expected. Expected is one of ( text, elision, extend, end-line, end-paragraph, footnote,
level )."*

**Required-but-null attributes.** JAXB omits a null field silently rather than refusing to
marshal. Dropping `type` from a `<supports>` (`#REQUIRED` in the schema) marshals clean —
`HeaderBuilder`'s `newSupports` sets it explicitly on every call for exactly this reason.
`MusicXmlSchemaValidator` reports the omission as: *"Element 'supports': The attribute 'type' is
required but missing."*

A round-trip test cannot catch either case: our own unmarshal reads the malformed document back
and recovers the same `Song`, because the reader tolerates exactly what this writer emits.
Schema validation is the only check that sees the document the way a foreign MusicXML consumer
would — and since [output is for everyone](#only-songscribe-documents-are-read), that consumer
is the one these two defects would actually break.

---

## What belongs in `ProxyMusicAccess`

`songscribe.io.musicxml` is `@NullMarked` and the build runs NullAway as an error
(`OnlyNullMarked=true`, `JSpecifyMode=true`), but ProxyMusic's 355 generated classes carry no
JSpecify annotations, so NullAway checks nothing across them. `note.getPitch().getStep()` on a
`<rest>`, or `note.getType().getValue()` on a hand-edited file missing `<type>`, both compile
clean and throw `NullPointerException` at load time. Nothing in the toolchain will tell you a
read is unguarded; every absent value in the graph is handled by hand.

`ProxyMusicAccess` holds the three kinds of handling that are worth having in one place:

- **Required values** — an absence that makes the document unreadable, rejected by `require` or a
  `requireXxx` accessor so the failure is a `MusicXmlReader.UnsupportedFormatException` with a
  specific `detail()` rather than an NPE. `MusicXmlReaderLenienceTest` is the specification for
  which values those are (41 hand-written XML fixtures pinning the exact policy, e.g.
  `testNoteMissingTypeThrows` for `note.getType() == null`).
- **Optional scalars that need converting** — a generated enum, a `BigDecimal`/`BigInteger`, a
  `yes|no`, or a `tenths` measurement, each turned into what the model wants and null-or-fallback
  when absent: `token`/`isYes`/`decimal`/`integer`/`tenthsToSs`.
- **`JAXBElement` lists** — the heterogeneous `List<JAXBElement<?>>` properties such as
  `Encoding.getEncodingDateOrEncoderOrSoftware()`, unwrapped by element name and type through
  `jaxbValues`/`firstJaxbValue`.

An ordinary null-checked read of a directly-typed child stays at its use site —
`score.getMovementTitle()` in `HeaderMapper`, `note.getTimeModification()` in `NoteMapper`. The
mappers do this at some scale, and deliberately: routing those through `ProxyMusicAccess` would
add a delegating method per getter and check nothing that the caller's own `null` test does not.

Because the checking is by hand, a missed one is possible. `SongFileLoader.load` catches
`RuntimeException` as a backstop, logs it at `error` with its stack trace, and reports the file
as a `ParseError`, so a missed check costs a failed open rather than taking the application down
with it. That is defence in depth, not a substitute for the check — nothing about it makes an
unguarded read acceptable.

The **write path needs no equivalent**, and its absence is not an asymmetry to fix: the builders
construct nodes and set values on them, and the few places that read a node back are reading
nodes the same class created moments earlier.

`ProxyMusicAccess` never imports `org.audiveris.proxymusic.*` as a wildcard — the package
declares its own `String` and `Double`, so a wildcard import makes every plain `String`
reference ambiguous. Single-type imports only, in that file and everywhere else in the package
that touches ProxyMusic types.

---

## Credit routing (`HeaderMapper`)

The writer emits the same value (composer, dates, place) in **both** the head (canonical) and a
`<credit>` (display-only). The reader must take the head value and ignore the credit, or a
hand-edited credit corrupts the model. Every credit routes into exactly one of three classes:

| Class | Reader behavior | Members |
| ----- | --------------- | ------- |
| **Canonical** | read into the model | subtitle credit; the four score-below credits; attribution relative-y |
| **Display-only** | ignored, re-derived on write | title credit; composer, lyricist, arranger, date, rights, place credits |
| **Write-forward** | ignored, recomputed or constant | rights, software, encoding-date, supports, scaling, music-font, `default-x`/`default-y` (for an external renderer) |

---

## The `MusicXmlSerializer.ParsedDocument` seam

`MusicXmlSerializer.unmarshal` returns `ParsedDocument(score, rootElement, version)`, not a bare
`ScorePartwise` — because two facts about a document are not recoverable from the graph at all:

- **The root element name.** `<foo version="4.0"/>` unmarshals into an empty `ScorePartwise`
  without complaint. JAXB matches by type, not by tag, so a foreign root produces a graph
  indistinguishable from an empty, valid one — there is nothing on `ScorePartwise` to ask "was
  this document actually rooted at `<score-partwise>`?"
- **The raw `version` attribute.** The schema declares `version` with a default of `"1.0"`, so
  `ScorePartwise.getVersion()` substitutes that default for an *absent* attribute. An omitted
  `version` and an explicit `version="1.0"` are rejected with different diagnostics
  (`SongMapper.checkFormat`'s `"missing version attribute"` vs. its version-too-old message), and
  the graph cannot tell them apart.

Both are read directly off the `XMLStreamReader` — `unmarshal` advances it to the root
`START_ELEMENT` and reads `getLocalName()`/`getAttributeValue(null, "version")` *before* handing
the same reader to the `Unmarshaller`. `SongMapper.checkFormat` applies the format-gate policy to
these two raw facts rather than to anything on `ScorePartwise`, which is why the gate lives in
`SongMapper` and not in `ProxyMusicAccess`: it is answering a question the object model cannot
represent, not reading a value that is merely absent.

---

## Parser hardening

`MusicXmlSerializer.newHardenedInputFactory` is the StAX equivalent of
`SafeXmlParser.newHardenedFactory()` for the SAX path: a `DOCTYPE` is read, but nothing it names
is ever fetched.

- **`XMLInputFactory.SUPPORT_DTD` is `true`, deliberately.** Skipping the declaration (the
  opposite, seemingly-safer choice) leaves its entities *undeclared*, so a document referencing
  one fails as malformed instead of behaving the way a SAX-based reader behaves: an internal
  entity expands, an external one drops and leaves its element empty. Reading the declaration is
  what makes the parser tolerate a perfectly ordinary foreign export.
- **`IS_SUPPORTING_EXTERNAL_ENTITIES` is `false`**, and the `XMLResolver` returns an empty
  `ByteArrayInputStream` for every system id — declared, never fetched.
- **`ACCESS_EXTERNAL_DTD=""` is deliberately absent.** With `SUPPORT_DTD` on, an emptied
  protocol allow-list turns *any* external DTD reference into a fatal parse error — which would
  report a foreign export (every one of them carries a DOCTYPE naming musicxml.org) as *damaged*
  rather than *foreign*, the exact regression `SafeXmlParser`'s Javadoc exists to prevent. The
  `XMLResolver` refuses the fetch just as completely, without failing the parse. Do not "restore"
  this property.
- **`jdk.xml.entityExpansionLimit`** is requested explicitly, best-effort — StAX has no standard
  property name for it, so an implementation that does not recognize it still parses under
  whatever default it applies.
- **`XMLConstants.FEATURE_SECURE_PROCESSING` is not a supported `XMLInputFactory` property on
  this JDK's StAX implementation** — setting it throws and kills the class initializer. It is not
  set; the expansion cap above is the substitute.

**The `ValidationEventHandler` fails the parse only on a `NumberFormatException`.** JAXB's
default behavior is to swallow a value it cannot convert and leave the field at its type default,
so a non-numeric `<octave>` would otherwise load as a valid song with a wrong pitch and no error.
The handler aborts specifically when `event.getLinkedException() instanceof NumberFormatException`
— any other kind of event (chiefly an element the schema does not allow in that position) is left
tolerated, matching the lenience the reader has always had: a file may carry constructs this
program does not model, and refusing to open it over one of them is worse than ignoring it. A
value that is not the kind of thing it claims to be is different in kind — there is nothing to
ignore, only a number silently becoming a default. A raw `NumberFormatException` that reaches
`BigDecimal`'s constructor without going through the event handler at all (some conversions do)
is caught and wrapped the same way, so both paths report the same failure shape.

---

## The network-fetch trap

**A StAX `XMLResolver` must return an `InputStream`, `XMLStreamReader` or `Source`.** Anything
else — a `Reader`, for instance — is discarded silently, and the parser then resolves the system
id **itself**: a real HTTP request to whatever the document names, issued from whatever machine
opened the file.

This is not a hypothetical. It happened during this plan's Phase 10: an early `XMLResolver`
implementation returned a `Reader`, and every export carrying the standard MusicXML `DOCTYPE`
fetched `http://www.musicxml.org/dtds/partwise.dtd`. Its visible symptom was a parse failure
reading *"The markup declarations contained or pointed to by the document type declaration must
be well-formed"* — which means **content came back**. Read that message as evidence of a fetch
having happened, not as a malformed test fixture, if it ever recurs.

**A `DOCTYPE` pointing at a missing file or a closed port cannot detect this.** The parser
tolerates a failed fetch and reports success either way — that is the entire point of the
hardening — so a fixture built that way passes the test whether or not a request actually went
out. The only way to make "nothing was fetched" observable rather than merely plausible is a
live listener that can be asked whether it was contacted:
`MusicXmlSerializerTest.testUnmarshalDoesNotFetchWhatADoctypeNames` starts a loopback
`HttpServer`, points a `DOCTYPE` at it, parses, and asserts the server logged zero requests. That
test is the guard against a repeat of this regression — it cannot be replaced by a `DOCTYPE`
naming an unreachable path; only a listener that can be asked "were you contacted?" answers the
actual question.

The sibling test, `testSchemaValidationDoesNotFetchWhatADoctypeNames`, runs the same probe
against `MusicXmlSchemaValidator` — which passes today with no hardening of its own, because a
default JAXP `Validator` does not fetch an instance document's `DOCTYPE`. It is not pinning a
fix; it is a guard that would catch a future change to the validator that started resolving
external references, the same way the unmarshal test would catch a `XMLResolver` regression.
