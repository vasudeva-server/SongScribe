### 4F. `font`

Audited from production code outward: enumerated every testable behavior in `DocumentFonts`, `DocumentFontsHolder`, `FontKey`, and `SourceSans3Font`, classified each by the rubric, then checked `src/test/java/songscribe/font/DocumentFontsTest.java` (the only test file in the mirrored package) and e2e test source for coverage.

| class | behavior | required level | existing test | verdict | action | done |
|-------|----------|---------------|---------------|---------|--------|---|
| `DocumentFonts` | `getFont(FontKey)`: returns stored font | unit | `DocumentFontsTest.GetSet.testGetFontRoundTrip` | adequate | parameterized over all `FontKey` values; asserts exact font identity | — |
| `DocumentFonts` | `getFont(FontKey)`: throws `IllegalStateException` (with key name in message) when font not set | unit | `DocumentFontsTest.GetSet.testGetFontThrowsWhenNotSet` | adequate | parameterized; verifies exception type and message content | — |
| `DocumentFonts` | `setFont(FontKey, Font)`: stores font retrievable by key | unit | `DocumentFontsTest.GetSet.testGetFontRoundTrip` (exercises `setFont(FontKey, Font)`) | adequate | covered as part of round-trip | — |
| `DocumentFonts` | `setFont(FontKey, String, int)`: resolves font by PS name and size, stores it | unit | `DocumentFontsTest.GetSet.testSetFontByNameRoundTripSize` | inadequate | asserts `font.getSize()` == expected size (adequate), but `assertThat(font.getPSName()).isNotEmpty()` is a weak assertion — does not verify that the resolved PS name matches `BASE_NAME`; a mutant that resolves the wrong font passes | ⬜ |
| `DocumentFonts` | copy constructor: produces independent copy (mutations to copy do not affect original) | unit | `DocumentFontsTest.CopyConstructor.testMutatingCopyDoesNotAffectOriginal` | adequate | | — |
| `DocumentFonts` | copy constructor: mutations to original do not affect copy | unit | `DocumentFontsTest.CopyConstructor.testMutatingOriginalDoesNotAffectCopy` | adequate | | — |
| `DocumentFonts` | `equals`: identical content → equal | unit | `DocumentFontsTest.Equals.testEqualIdenticalContent` | adequate | | — |
| `DocumentFonts` | `equals`: reflexive | unit | `DocumentFontsTest.Equals.testEqualReflexive` | adequate | | — |
| `DocumentFonts` | `equals`: not equal when font name differs for any key | unit | `DocumentFontsTest.Equals.testNotEqualWhenNameDiffers` | adequate | parameterized over `FontKey` | — |
| `DocumentFonts` | `equals`: not equal when font size differs for any key | unit | `DocumentFontsTest.Equals.testNotEqualWhenSizeDiffers` | adequate | parameterized over `FontKey` | — |
| `DocumentFonts` | `equals`: `null` object → not equal | unit | none | missing | add test for `equals(null)` returning false | ⬜ |
| `DocumentFonts` | `equals`: different type → not equal | unit | none | missing | low risk given `instanceof` pattern, but worth one line | ⬜ |
| `DocumentFonts` | `hashCode`: consistent with `equals` | unit | `DocumentFontsTest.Equals.testHashCodeConsistentWithEquals` | adequate | | — |
| `DocumentFonts` | `defaultsFromPrefs()`: populates all six roles from `Prefs` | unit | `DocumentFontsTest.DefaultsFromPrefs.testAllRolesPopulated` | inadequate | only verifies `getSize()` per role equals the prefs font-size value — does not check the font family name; a mutant that calls the wrong `PrefsKey` font string (or resolves the wrong family) while preserving sizes passes | ⬜ |
| `DocumentFonts` | `defaultsFromPrefs()`: maps each `FontKey` to the correct `PrefsKey` pair (e.g., `TITLE` → `TITLE_FONT` + `TITLE_FONT_SIZE`, not some other key) | unit | none | missing | the authoritative FontKey→PrefsKey mapping is untested; wrong-key bugs are invisible | ⬜ |
| `DocumentFontsHolder` | default methods (`getTitleFont`, etc.): each delegates to `getFont` with the matching `FontKey` | none | — | — | trivial one-liners; delegating to `getFont` already tested | — |
| `FontKey` | pure enum — six constants, no logic | none | — | — | — | — |
| `SourceSans3Font` | `installLazy()`: registers family loader via FlatLaf `FontUtils` | none | — | — | risk is real Swing/FlatLaf integration; cannot be meaningfully unit-tested | — |
| `SourceSans3Font` | `install()`: delegates to `installBasic()` | none | — | — | thin wrapper over FlatLaf font registration | — |
| `SourceSans3Font` | `installBasic()`: installs six font styles via `MyFontUtils.installLocalFont` | none | — | — | font installation is an integration behavior; testing it requires the AWT font subsystem | — |
| `SourceSans3Font` | String constants (`FAMILY`, `STYLE_*`): correct PS name strings | unit | none | missing | constant values are the contract for font resolution everywhere in the app; a typo silently falls back to a system font; verify each constant matches the bundled font file name | ⬜ |

**4F notes (quality concerns):**

The highest-risk gap is `defaultsFromPrefs` coverage: the existing `testAllRolesPopulated` checks only that each role's font size equals its prefs value. It does not verify the family name, which means any wrong-key assignment in the six-line mapping (e.g., swapping `TITLE_FONT` and `LYRICS_FONT`) goes undetected — a plausible cut-paste error given the repetitive structure. The test should additionally assert `font.getFamily()` (or `font.getPSName()`) against `Prefs.getString(PrefsKey.TITLE_FONT)` etc. for each role. Similarly, `testSetFontByNameRoundTripSize` uses `isNotEmpty()` for the PS name instead of asserting the exact resolved value — a weak assertion that survives any font being substituted. The `SourceSans3Font` constant strings (`FAMILY`, `STYLE_REGULAR`, etc.) are the contract for all font lookups across the application; a typo in any constant causes silent font fallback at runtime, yet no test verifies them against the bundled file names. `DocumentFontsHolder` default methods are trivial enough to classify as `none`. No dead code was identified: `DocumentFontsHolder` is implemented by both `ScoreView` and `DocumentFonts`, and `SourceSans3Font` is called from `UIUtils.initLaf`.

