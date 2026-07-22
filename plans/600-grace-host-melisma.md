# Issues #600 + #599 — Automatic grace-host melisma
## Goal
When a grace-host pair carries a lyric, the syllable belongs to the grace note and the host may not carry one of its own. A melisma must therefore extend automatically from the grace across its host.

Both issues ship on one branch: the remaining work in #599 is a single edit, and it is what _creates_ the grace-with-lyric state #600 renders.
## Already done (on `develop`, commit `a45d3312`, closes #322)
The _navigation_ half of #599 is implemented:

- `LyricEditor.isLyricTargetEligible` (`LyricEditor.java:954`) — documented as "the single source of truth for the host-block rule"; returns `false` for a host, so `findNextEligibleIndex`/`findPreviousEligibleIndex` skip it.
  
- `LyricLayoutBuilder.java:159` already `continue`s past the host column so hyphens and extenders pass through.
  
- `LyricEditor.computeLyricBoxLeftXSs` (`:349`) already treats grace+host as one unioned column.
  

So #599 reduces to its second sentence: transfer an existing lyric to a newly-inserted grace note.
## Decision — model-level, not layout-derived
The melisma is written as real state: `Extend.START` on the grace's lyric, a text-less `Extend.STOP` carrier on the host.

The alternative (infer it in `LyricLayoutBuilder`, leave the DOM clean) was rejected: exported MusicXML would carry no `<extend>`, so other notation apps would render a bare syllable on the grace and nothing across the host. A third option — synthesize START/STOP only at MusicXML write time — was rejected because the reader would then have to recognize the pair and _discard_ the carrier it just read, leaving an asymmetric write/read pair to maintain forever.

**This does not violate "the host may NOT contain a syllable of its own."** `Lyric`'s compact constructor (`Lyric.java:75-102`) forces carriers to `text=""` and `syllabic=null`, and `StaffElement.setLyricForVerse` re-enforces it (`:624-647`). A STOP carrier is structurally incapable of holding a syllable, and `isLyricTargetEligible` keeps the user from typing into it.

MusicXML needs **no writer or reader changes** — `MusicXmlNotationsWriter.writeLyrics:210-212` already emits a bare `<extend type="stop"/>` for a carrier.
## Architecture — one idempotent sync, called from many sites
The trace below found **no single chokepoint**. Pair destruction happens along nine independent paths, six of which need work. Writing bespoke teardown at each is fragile.

Instead: a single idempotent `Line` method, e.g.

```java
void syncGraceHostMelisma(int graceIndex)
```

that reads current state and _converges_ — establishes the START/STOP pair when the index is a paired grace note carrying text, tears it down otherwise. Because it derives from current state rather than diffing, every call site only has to _call_ it inside its existing modification bracket; none has to reason about which transition occurred.

It must **remove** the host's carrier lyric, not merely clear its extend. `cascadeClearExtend` (`Line.java:553`) sets `Extend.NONE, Syllabic.SINGLE` while keeping `lyric.text()` — for a carrier that is `""`, leaving an empty non-carrier lyric on the host. That residue matters: `LyricEditor.findPreviousLyricBearingIndex` (~`:1033`) returns any element with a non-null lyric and does **not** consult the host-block predicate, so an empty-lyric host becomes a valid backward target for` -`and`_`.
## Pair-destruction trace
A pair is: element `i` is a grace note with a glissando; the host is always `i + 1` (`Line.isPairedGraceNote:1032`, `isHostOfPairedGraceNote:1047`).

| # | Path | Site | Status |
|---|---|---|---|
| 1 | Delete selected slide decoration — un-pairs, both elements survive | `ScoreViewController.handleDelete:594-596` | ❌ touches only `ElementField.SLIDE`; **primary gap** |
| 2 | Click a FALL zone on the grace — `setFall()` clears the glissando (mutual exclusivity) | `PreviewElementManager.handleClick[1]:771-775` → `SlideZone.applyTo:43-45` | ❌ no lyric handling |
| 3 | Host replaced with a non-pitched element → grace removed | `PreviewElementManager.modifyExistingElement` (grace-cleanup tail) | ❌ raw `removeElement(elementIndex - 1)`, no `adjustExtendsForDeletion` → orphaned STOP on the rest |
| 4 | Insert **non-pitched** between grace and host | `PreviewElementManager.insertElement:1191-1197` | ✅ `adjustExtendsForInsertion:1183` clears the chain first |
| 5 | Insert **pitched** between grace and host — glissando re-targets, new element becomes the host | same | ⚠️ chain correctly cleared; **re-point** the melisma to the new host (decided) |
| 6 | **Delete the grace alone** (select it and delete — not treated specially) | `ScoreViewController.deleteNote:965` | ⚠️ `adjustExtendsForDeletion:998` clears the host's STOP but leaves the empty-lyric residue; the syllable must **hand back to the host** (decided) |
| 7 | Delete the host | `ScoreViewController.deleteNote:965` | ✅ grace-aware — removes both; `adjustExtendsForDeletion:998` collapses the 2-element chain |
| 8 | Delete a range spanning either | `deleteElementRange:643` | ✅ grace-aware fallback to `deleteSelection`, else per-index `adjustExtendsForDeletion:677` |
| 9 | MusicXML import | `RangeSpanResolver.resolveSlide` under `withoutMutationTracking` | ❌ no normalization — a file may pair a grace with a host that has its own syllable; **repair on read** (see Phase 5) |

Grace notes cannot have their type changed, so there is no "grace stops being a grace" path to handle.

The existing `Line` helpers handle the 2-element chain correctly wherever they are _invoked_:

- Grace (START) deleted → `cascadeClearExtend(graceIndex + 1)` clears the host's STOP.
  
- Host (STOP) deleted → `adjustPrecedingForStopDeletion` sees START on a 2-element chain and clears it to NONE (`Line.java:580`, comment: "a single note cannot carry a melisma").
  

Undo needs no new machinery: every write goes through `modifyElement(..., ElementField.LYRIC, ...)` inside the caller's existing bracket, so `ElementModification` snapshots cover it. The complete-emission invariant (`.agents/guides/mutations.md:59`) requires the sync call to sit _inside_ the bracket that changed the pairing, not after it.
## Phases
### Phase 1 — `Line.syncGraceHostMelisma`
Add the idempotent sync plus two helpers:

- one that removes a verse's lyric outright (the carrier-removal gap above);
  
- one that transfers a verse's lyric between two indices, used in both directions — host→grace on pairing (#599, Phase 2) and grace→host on grace deletion (decision 2, Phase 3).
  

Note the ordering constraint for hand-back: the syllable must be read off the grace and written to the host **before** the grace is removed, and the host's STOP carrier must be gone before the syllable lands on it — otherwise the carrier's text-less invariant and the incoming syllable collide. Sequence is: read grace's lyric → remove host's carrier → write syllable to host → remove grace.

Unit tests in `LineGraceNotePairingTest` / `LineMutationTest`: establish, tear down, re-run twice (idempotence), multi-verse, grace with no text, host with a pre-existing lyric, and hand-back round-trip (pair a note carrying a lyric, then delete the grace — the syllable should return to the host unchanged).

_Model: Opus, high._ This is the whole design; everything else calls it.
### Phase 2 — Establish sites
- `GraceModeManager.enterGraceNotePaired` (~`:721`) — #599's lyric transfer in the` connectNext == true` branch only (a freshly click-inserted host never has a lyric), then sync.
  
- `LyricEditor` commit path (~`:913`) — sync after committing text onto a paired grace.
  

_Model: Opus, medium._
### Phase 3 — Teardown sites
Rows 1, 2, 3, 5, 6 above.

- **Rows 1 and 2** (un-pair without delete) are the primary gap: both elements survive, so the sync call converges to teardown. The syllable stays on the (now ordinary) former grace note — no hand-back, since nothing was deleted.
  
- **Row 3** additionally needs its raw `removeElement` routed through `adjustExtendsForDeletion` regardless of this feature — a pre-existing undo/chain bug. The grace is being removed here, so hand-back does **not** apply: the host is simultaneously being replaced by a non-pitched element that cannot carry a lyric.
  
- **Row 5** re-points: after the insertion, sync against the grace index, which now resolves to the newly inserted host.
  
- **Row 6** is the hand-back case (decision 2), using the Phase 1 transfer helper and its ordering constraint.
  

_Model: Opus, medium._
### Phase 4 — Layout
Relax the unconditional host skip at `LyricLayoutBuilder.java:159` so a real STOP on the host closes the extender at `getNoteheadRightEdgeXSs()` instead of passing through. `MIN_MELISMA_LENGTH_SS` and `clampExtendersToFollowingSyllable:272` should apply unchanged. Extend `LyricLayoutBuilderGraceNoteTest`.

_Model: Opus, high._ Interacts with the extender rules recently tuned on this branch's ancestry.
### Phase 5 — Import normalization
Repair grace-host pairs on read (row 9): if an imported host carries a syllable of its own, move it to the grace and establish the melisma; if the grace carries a syllable with no melisma, establish one. Runs under `withoutMutationTracking`, so it must mutate raw state rather than go through the tracked helpers — the same constraint the rest of `MusicXmlReader` works under. Repair belongs after `RangeSpanResolver` has resolved the slides, since pairing is not known until then.

Confirm the legacy `.mssw` reader (`StaffElementIO.endElement11:566`) reaches the same normalization, or deliberately does not.

_Model: Opus, high._ Load-time mutation with no undo net; getting the ordering wrong corrupts files silently.
### Phase 6 — Navigation audit
`findPreviousLyricBearingIndex` (~`:1033`) and the chain-repair helpers` terminatePrecedingContinueChain:1201`,` clearForwardCarriers:1224`,` breakChainAtCurrentElement:1253` — confirm none treats a host carrier as a syllable target.

_Model: Opus, high._
### Phase 7 — Verify
`./scripts/compile.sh`, then `./scripts/test.sh unit`. Round-trip a paired grace with a lyric through MusicXML and confirm `<extend type="start"/>` / `<extend type="stop"/>`. Also round-trip a file that *violates* the invariant (host carrying its own syllable) to confirm Phase 5's repair fires and is stable across a second load.
## Findings from the trace (code facts, not decisions)
- Grace notes cannot have their type changed — no "grace stops being a grace" path exists.
  
- A paired grace note **can** be deleted alone (select and delete; not treated specially) — row 6.
  
## Decisions
1. **Import repair: yes.** The reader normalizes imported grace-host pairs that violate the invariant. Phase 5 stands.
  
2. **Deleting the grace hands its syllable back to the host.** The host is an ordinary note again and eligible to carry a lyric, so the syllable survives the deletion rather than dying with the grace. This is the inverse of #599's transfer-on-pairing.
  
3. **Inserting a pitched note between grace and host re-points the melisma** to the new host, consistent with the automatic character of the feature.
