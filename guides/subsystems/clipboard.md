# Clipboard

Read `FragmentPaste` first.

## Membership

- `ClipboardManager`
- `ClipboardDidChangeNotification`
- `Fragment`
- `FragmentPaste`
- `PasteSpanReconciliation`
- `PasteModeManager`
- `InsertionPointMode`
- `PasteboardAction`
- `PasteboardOpCommand`

## Rules

- Make every clipboard gesture one modification bracket, because a paste over a selection
  deletes and inserts and the user undoes it as one action.
- Ask the user every question before the first change, because declining must leave an empty
  bracket and no undo step.
- Reduce a fragment to what lands before measuring it, judging its spans or walking its
  accidentals, because all three must see the run that actually lands.
- Paste a copy from `Fragment.instantiate`, never the held fragment, because a second paste
  must share nothing with the first.
- Keep a rule that holds for every placement in `InsertionPointMode`, never in a client,
  because `PasteModeManager` is one of several clients.
