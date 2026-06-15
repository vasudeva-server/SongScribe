# Refactor: extract the font row into `FontSettingRow`
## Goal
Collapse the three near-identical font-row builders — `TitleTab.createTitleFontRow`, `AttributionTab.createFontRow`, `FontTab.createFontRow` (8 call sites total) — into one reusable unit, and move the machinery it owns out of the already-large `SongSettingsDialog`.
## Recommendation
Create a new package-private class `FontSettingRow` in `src/main/java/songscribe/ui/dialog/`. It owns everything that currently makes up a font row:

- a static factory that builds the `JPanel`
  
- `ChooseFontAction` (moved from `FontTab`)
  
- `ResetFontAction` (moved from `FontTab`)
  
- `applyFont(...)` (moved from `FontTab`)
  

`applyFont` is also called 5× by `FontTab.getData`; those calls become `FontSettingRow.applyFont(...)`.
### Why a class, not a private helper method
The row factory + the two actions + `applyFont` already form one cohesive widget ("a font setting row") and are already reached across tabs. Extracting them removes ~150 lines from a 1700-line file and makes the row real shared infrastructure rather than a buried helper. A private static helper would deduplicate the layout but leave the dialog just as large and keep the actions nested in `FontTab`.
## Unified factory signature
```java
static JPanel create(
    MainFrame mainFrame,
    JLabel rowLabel,        // col 0 leading label
    JComponent fontDisplay, // col 1 stretchy display
    FontKey fontKey,
    JLabel targetLabel,     // the label the Choose/Reset actions mutate
    JComponent preview      // the preview the actions mutate
)
```

This is essentially today's `AttributionTab.createFontRow` shape, plus an explicit `mainFrame` (instead of `getMainFrame()`).
### How each call site maps
| Caller | rowLabel | fontDisplay | fontKey | targetLabel | preview |
|---|---|---|---|---|---|
| Title | new "Font" label | `titleFontDisplay` | `TITLE` | `fontTab.titleFontLabel` | `fontTab.titleFontPreview` |
| Attribution #1 | `wordsMusicLabel` | `wordsMusicFontDisplay` | `ATTRIBUTION` | `fontTab.attributionFontLabel` | `fontTab.attributionFontPreview` |
| Attribution #2 | `datePlaceLabel` | `datePlaceFontDisplay` | `SUB_ATTRIBUTION` | `fontTab.subAttributionFontLabel` | `fontTab.subAttributionFontPreview` |
| Font ×5 | new "Font" label | `…FontLabel` field | per row | same `…FontLabel` field | `…FontPreview` field |

In the Font tab, the middle display and the action target are the same `JLabel` — that already works: pass the same reference for both `fontDisplay` and `targetLabel`.
### Convenience overload for the "Font"-labeled rows
6 of the 8 call sites (Title + 5 Font) use the same leading `new JLabel(Strings.get(Strings.DIALOG_SONG_SETTINGS_FONT))`. To avoid repeating that, add a second overload that creates the standard "Font" label internally:

```java
static JPanel create(
    MainFrame mainFrame,
    JComponent fontDisplay,
    FontKey fontKey,
    JLabel targetLabel,
    JComponent preview
)
```

Attribution keeps using the full overload (it passes a content label).
## One behavior decision to confirm {#c1}
Today: Title and Font call `fontLabel.setLabelFor(fontDisplay)`; Attribution does **not** set it.

{==Plan: always call `rowLabel.setLabelFor(fontDisplay)` inside the factory==}{>>Agreed<<}{#c1}. For Attribution this _adds_ an accessibility association between its content label and the display — harmless and arguably more correct, but it is a (tiny) behavior change. The alternative is to thread a boolean and preserve the exact current state.

I recommend always setting it. Flagging because it is not strictly behavior-preserving.
## Steps
1. Create `FontSettingRow.java`; move `ChooseFontAction`, `ResetFontAction`, `applyFont` into it (Serena `jet_brains_move` where possible so references update atomically).
  
2. Add the two `create(...)` overloads with the layout body (single copy of the GridBag code + the leading-gap comment).
  
3. Repoint `FontTab.getData`'s 5 `applyFont` calls to `FontSettingRow.applyFont`.
  
4. Replace the 8 row-building call sites:
  
  - delete `TitleTab.createTitleFontRow`; `createTitleSection` calls the convenience overload.
    
  - delete `AttributionTab.createFontRow`; `createFontsSection` calls the full overload (×2).
    
  - delete `FontTab.createFontRow`; `initContents` calls the convenience overload (×5).
    
5. `./scripts/compile.sh`; fix anything; confirm SUCCESS.
  
## Out of scope
- `createFontPreviewWrapper` / `createPreviewWrapper` (preview chrome, not the row).
  
- Any string, layout-constant, or visual change beyond the `setLabelFor` decision above.

---
comments:
  c1:
    by: user
    at: 2026-06-15T03:48:28.942Z
