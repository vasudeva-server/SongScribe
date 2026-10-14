# Key Changes

Read `KeyChangeElement` first.

## Membership

- `Key`
- `KeyChangeElement`
- `KeyChangeSite`
- `KeyChangeStranding`
- `KeyInheritance`
- `KeySignatureExtent`
- `KeySignatureAppearance`
- `KeySignatureRenderer`
- `LineKeyChange`
- `LineKeys`
- `CautionaryKeySignature`
- `KeyEditFitCalculator`
- `LeadingBarLoadPass`
- `KeyChangeAction`
- `KeyChangeDialogController`

## Rules

- Ask one question, the key in effect at a position, and never read a line's own key and its
  mid-line key changes as two storages.
- Derive which lines a key move reaches (inherited keys re-derived, lines a view re-solves,
  key changes stranded, lines measured before a commit) from the one enumeration of key-moving
  changes, because a second enumeration disagrees silently.
- Remove a stranded key change in every edit that moves a key and again when reading a file,
  because neither covers the other.
- Ask the placement predicate whether a position accepts a key change, and keep every position
  rule out of command availability, because an unavailable position must show as an insertion
  marker that disappears.
- Ask the one answer that states what a cautionary draws, how much room it takes and where each
  part lands, and derive none of the three separately.
- Refuse a key edit that would leave any line it touches overflowing, including a line that
  already overflows, and leave every other path to render overflowing and flagged, because
  refusing on load would make an existing document unopenable.
- Add no mode to a key, and convert the legacy format's key pair only in its reader.
