# The Score View

Read `ScoreView` first.

## Membership

- `ScoreView`
- `ScorePanel`
- `MainPanel`
- `StaffPanel`
- `LinePanel`
- `TextPanel`
- `ScoreComponent`
- `BaseTitleComponent`
- `LineComponent`
- `LineOverlayComponent`
- `OverlayHost`
- `LineSelectionHandler`

## Rules

- Register a score component's mouse listener on the component itself, never on an ancestor that
  works out where the click landed, because the toolkit hands a click to the deepest component
  under the point that has listeners.
