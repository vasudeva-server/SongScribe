Below is the full matrix derived from `LyricEditor.java`. Each row is **(input × precondition state) → predicate → outcome**. Boundary characters are `space`, `-`, `=`, `_`. I have also included `Tab`, `Shift+Tab`, `Enter`, `Escape`, paste/insert of `\n`, and the length-limit case so the matrix is exhaustive for keystrokes that reach the editor.

State dimensions used below:
- **openedAsExtender** — true when the editor opened on an element whose lyric has `Extend.CONTINUE` or `STOP` (a carrier).
- **text** — current editor text (empty / non-empty).
- **caret** — only meaningful when text is non-empty (at-end / mid).
- **existing lyric on this element** — null / text-bearing / carrier.
- **previous lyric-bearing element** (only consulted by `-` and `_` empty-text branches): none / END or SINGLE / BEGIN or MIDDLE / carrier.
- **next eligible element** — exists / does not exist (eligible = non-rest, or rest already carrying a non-blank lyric in current verse).

---

## 1. Word characters (any printable that is not space, `-`, `=`, `_`, `\n`)

| #   | Precondition                        | Predicate           | Outcome                                  |
| --- | ----------------------------------- | ------------------- | ---------------------------------------- |
| W1  | `currentLength + insertLength ≤ 32` | always allowed      | character inserted; bounds recomputed    |
| W2  | `currentLength + insertLength > 32` | length cap exceeded | **beep**, entire insertion rejected      |
| W3  | inserted text contains `\n` (paste) | newline present     | insertion **silently dropped** (no beep) |

---

## 2. `Space`

`Space` is consumed by the keyTyped listener and always calls `breakChainCommitAndAdvance(WORD_FINAL, nextEligibleIndex)`. This unconditionally: (1) breaks any extend chain at the current element (terminates predecessor chain, clears forward carriers), (2) commits the current text as `WORD_FINAL`/`Extend.NONE` (a no-op when the element already has that exact state), and (3) opens the next eligible element or dismisses.

Space does **not** use `navigationCommitSpec()` and does **not** preserve existing syllabic/compound/extend state.

| #   | text       | element lyric on entry         | next eligible | Outcome                                                           |
| --- | ---------- | ------------------------------ | ------------- | ----------------------------------------------------------------- |
| S1  | non-empty  | any                            | yes           | break chain; commit `WORD_FINAL`/`NONE`; advance                  |
| S2  | non-empty  | any                            | **no**        | break chain; commit `WORD_FINAL`/`NONE`; **dismiss**              |
| S3  | empty      | null                           | yes           | break chain (no-op); no commit; advance                           |
| S4  | empty      | null                           | **no**        | break chain (no-op); no commit; **dismiss**                       |
| S5  | empty      | carrier (`openedAsExtender`)   | yes           | break chain; write empty `SINGLE`/`NONE` lyric; advance           |
| S6  | empty      | carrier (`openedAsExtender`)   | **no**        | break chain; write empty `SINGLE`/`NONE` lyric; **dismiss**       |

---

## 3. `-` (hyphen)

### `openedAsExtender` = true

| #   | text      | next eligible | Outcome                                                                    |
| --- | --------- | ------------- | -------------------------------------------------------------------------- |
| H1  | empty     | n/a           | **beep**, stay open                                                        |
| H2  | non-empty | **no**        | **beep**, stay open                                                        |
| H3  | non-empty | yes           | break chain; commit `WORD_CONTINUING_HYPHEN`; advance                      |

### `openedAsExtender` = false, text non-empty

| #   | text      | next eligible | Outcome                                                                    |
| --- | --------- | ------------- | -------------------------------------------------------------------------- |
| H4  | non-empty | yes           | commit as `WORD_CONTINUING_HYPHEN` (BEGIN/MIDDLE); advance                 |
| H5  | non-empty | **no**        | **beep**, stay open                                                        |

### `openedAsExtender` = false, text empty

| #   | element lyric on entry | previous lyric-bearing | next eligible | Outcome                                                                     |
| --- | ---------------------- | ---------------------- | ------------- | --------------------------------------------------------------------------- |
| H6  | non-null (text-bearing)| n/a                    | n/a           | **beep**, stay open (would otherwise silently delete existing lyric)        |
| H7  | null                   | none                   | n/a           | **beep**, stay open                                                         |
| H8  | null                   | END or SINGLE          | n/a           | **beep**, stay open                                                         |
| H9  | null                   | BEGIN or MIDDLE        | yes           | implicit extension: leave this element's lyric null; advance                |
| H10 | null                   | BEGIN or MIDDLE        | **no**        | **beep**, stay open                                                         |

---

## 4. `=` (equals / compound)

| #   | openedAsExtender | text                                | caret  | next eligible | Outcome                                                                    |
| --- | ---------------- | ----------------------------------- | ------ | ------------- | -------------------------------------------------------------------------- |
| E1  | any              | empty                               | n/a    | n/a           | **beep**, stay open                                                        |
| E2  | any              | non-empty                           | mid    | n/a           | **beep**, stay open                                                        |
| E3  | any              | non-empty                           | at end | **no**        | **beep**, stay open                                                        |
| E4  | false            | non-empty                           | at end | yes           | commit as `WORD_CONTINUING_COMPOUND`; advance                              |
| E5  | **true**         | non-empty (user typed into carrier) | at end | yes           | break chain; commit as `WORD_CONTINUING_COMPOUND`; advance                 |

---

## 5. `_` (underscore / melisma start)

`_` with non-empty text always beeps regardless of caret position. With empty text, it calls `extendChainBackward()`.

| #   | text      | caret | previous lyric-bearing     | next eligible | Outcome                                                                                                                                                                                                                                     |
| --- | --------- | ----- | -------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| U1  | non-empty | any   | n/a                        | n/a           | **beep**, stay open                                                                                                                                                                                                                         |
| U2  | empty     | n/a   | none                       | n/a           | `extendChainBackward` → no predecessor → **beep**, stay open                                                                                                                                                                                |
| U3  | empty     | n/a   | END or SINGLE              | yes           | `extendChainBackward` rewrites predecessor's extend to `START`, fills intervening elements with `CONTINUE`, current to `CONTINUE`; advance — *current code does this regardless of predecessor syllabic; mirror of H8 which beeps. Inconsistent.* |
| U4  | empty     | n/a   | END or SINGLE              | **no**        | rewrite happens, then **dismiss** without an extend target                                                                                                                                                                                  |
| U5  | empty     | n/a   | BEGIN or MIDDLE            | yes           | rewrite predecessor extend to `START`, fill, current `CONTINUE`; advance                                                                                                                                                                   |
| U6  | empty     | n/a   | carrier (CONTINUE)         | yes           | predecessor already CONTINUE → no rewrite; fill forward to current; advance                                                                                                                                                                 |
| U7  | empty     | n/a   | carrier (STOP)             | yes           | predecessor flipped STOP → CONTINUE; fill; advance                                                                                                                                                                                          |
| U8  | empty     | n/a   | any                        | **no**        | `extendChainBackward` runs, then `advance()` finds nothing → **dismiss** — *no beep; the chain is built but stranded*                                                                                                                        |

---

## 6. `Tab`, `Shift+Tab`, `Enter`, `Escape`

| #   | Key         | Precondition                                                     | Outcome                                                                                                                        |
| --- | ----------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| K1  | `Tab`       | text changed                                                     | commit as `WORD_FINAL`/`NONE`, then advance forward (or dismiss if none)                                                       |
| K2  | `Tab`       | text unchanged, existing lyric is text-bearing                   | preserve existing syllabic/compound/extend, advance                                                                            |
| K3  | `Shift+Tab` | symmetric to K1/K2                                               | retreat backward (or dismiss)                                                                                                  |
| K4  | `Enter`     | any                                                              | uses `navigationCommitSpec()` — preserves existing shape when text is unchanged; commits as `WORD_FINAL`/`NONE` otherwise; dismiss |
| K5  | `Escape`    | any                                                              | no commit; `applyDismissAdjustment` (repairs dangling chains); dismiss                                                         |
| K6  | `Escape`    | `suppressDismissAdjustment` is set (after `extendChainBackward`) | dismiss with no mutations                                                                                                      |

---

## 7. Focus loss / outside click

| #   | Trigger                                 | Precondition                      | Outcome                                                            |
| --- | --------------------------------------- | --------------------------------- | ------------------------------------------------------------------ |
| F1  | focusLost                               | `focused == false` (never gained) | **no-op**                                                          |
| F2  | focusLost                               | `focused == true`                 | `navigationCommitSpec` commit + `applyDismissAdjustment` + dismiss |
| F3  | outside MOUSE_PRESSED on non-descendant | editor still parented & focused   | same as F2 (`commitAndDismiss`)                                    |
