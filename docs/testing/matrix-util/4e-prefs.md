### 4E. `prefs`

Audited from production code outward: enumerated every testable behavior in `Prefs`, `PrefsKey`, `RecentDocumentsManager`, and `StartupAction`, classified each by the rubric, then checked `src/test/java/songscribe/prefs/PrefsTest.java` (the only test file in the mirrored package) and cross-package unit tests for coverage.

| class | behavior | required level | existing test | verdict | action | done |
|-------|----------|---------------|---------------|---------|--------|---|
| `Prefs` | `getOrDefault`: returns store value when present, falls back to `getDefault` when absent | unit | none | missing | add tests for store-hit and store-miss paths | ✅ |
| `Prefs` | `getDefault`: throws `IllegalArgumentException` for unknown key (no default in `defaults.json`) | unit | none | missing | add test; critical contract for all scalar getters | ✅ |
| `Prefs` | `getString`: returns stored string value | unit | none | missing | add round-trip test | ✅ |
| `Prefs` | `getInt`: casts stored value through `Number.intValue()` — survives if value is `Long` | unit | none | missing | add test; int-stored-as-Long contract matters | ✅ |
| `Prefs` | `getLong`: analogous to `getInt` | unit | none | missing | add test | ✅ |
| `Prefs` | `getBoolean`: casts to `Boolean` | unit | none | missing | add test | ✅ |
| `Prefs` | `getStringList`: returns list from store; returns empty list (not default) when absent | unit | none | missing | add tests for both paths; empty-list contract must be verified | ✅ |
| `Prefs` | `getStringList`: ignores defaults for list keys (unlike scalar getters) | unit | none | missing | this asymmetry is a likely bug-hiding point | ✅ |
| `Prefs` | `getMap`: returns store value when present (Map); falls to default when absent; returns empty map when absent and no default | unit | `PrefsTest.testGetMapReturnsEmptyMapForMissingKey`, `testGetMapOnNonMapValueReturnsEmptyMap` | inadequate | `testGetMapReturnsEmptyMapForMissingKey` name is wrong — `DIALOG_GEOMETRY` has a default `{}` in `defaults.json`; the test happens to pass because `{}` deserializes as empty, but it is not testing the "no default" path | ✅ |
| `Prefs` | `putMap`: merges new entries into existing map | unit | `PrefsTest.testPutMapMergesEntries` | inadequate | asserts only `containsKey` — does not verify values are correct; a mutation that stores the wrong values passes | ✅ |
| `Prefs` | `putMap` + `getMap` round-trip: stored value is retrievable | unit | `PrefsTest.testPutMapAndGetMapRoundTrip` | inadequate | asserts only `containsKey("TestDialog")` — not the nested map values | ✅ |
| `Prefs` | `put(PrefsKey, String)`: stores string, triggers save+notification | unit | none | missing | add test | ✅ |
| `Prefs` | `put(PrefsKey, int)`: stores as `Long` (documented type coercion) | unit | none | missing | critical: only `getInt` works after this if value is `Long`; needs explicit assertion | ✅ |
| `Prefs` | `put(PrefsKey, long)` and `put(PrefsKey, boolean)`: store and retrieve | unit | none | missing | add tests | ✅ |
| `Prefs` | `putStringList`: replaces list wholesale (not merge) | unit | none | missing | add test | ✅ |
| `Prefs` | `reset`: removes key from store, restores default | unit | none (only used in `@AfterEach` teardown, not as a behavior under test) | missing | add test verifying value reverts to default after reset | ✅ |
| `Prefs` | `resetAll`: clears all overrides | unit | none | missing | add test | ✅ |
| `Prefs` | `parseJsonValue`: dispatches by JSON type (boolean / number stored as Long / string / object as Map / array → null) | unit | none | missing | high-risk: number-as-Long contract underpins all numeric getters; array→null gap means array values in defaults.json are silently dropped | ⬜ |
| `Prefs` | `writeTyped`: parses string to typed value (Boolean / Long / String) based on default type; ignores invalid numeric strings | unit | none | missing | migration correctness depends on this | ⬜ |
| `Prefs` | `migrate`: reads old `.properties` file, maps keys via `MIGRATION_MAP`, scans `showwhatsnew*` keys for highest version | unit | none | missing | high-risk legacy migration; no test | ⬜ |
| `Prefs` | `removeObsoleteKeys`: strips keys in `OBSOLETE_KEYS` from store and saves | unit | none | missing | add test | ✅ |
| `Prefs` | `allKeysExistInDefaults`: every `PrefsKey` (except `ALL`) has entry in `defaults.json` | unit | `PrefsTest.testAllKeysExistInDefaults` | adequate | well-written contract guard | — |
| `PrefsKey` | `key()` returns the camelCase JSON string matching the enum constant | unit | `PrefsTest.testAllKeysExistInDefaults` (indirectly exercises `key()`) | adequate | implicitly covered by the defaults check | — |
| `PrefsKey` | enum is purely a typed-key holder with no value logic | none | — | — | — | — |
| `RecentDocumentsManager` | `add`: deduplicates (existing entry moves to front), adds at front of MRU list | unit | none | missing | core MRU logic; no test | ⬜ |
| `RecentDocumentsManager` | `add`: enforces `MAX_SIZE` cap by removing last entries | unit | none | missing | off-by-one risk | ⬜ |
| `RecentDocumentsManager` | `add`: normalizes path before insert | unit | none | missing | normalization correctness | ⬜ |
| `RecentDocumentsManager` | `add`: posts `RecentDocumentsDidChangeNotification` after persist | unit | none | missing | notification contract | ⬜ |
| `RecentDocumentsManager` | `remove`: removes matching normalized path; posts notification | unit | none | missing | add test | ⬜ |
| `RecentDocumentsManager` | `remove`: no-op when path absent (should still persist+notify) | unit | none | missing | verify idempotency | ⬜ |
| `RecentDocumentsManager` | `clear`: empties list, persists, posts notification | unit | none | missing | add test | ⬜ |
| `RecentDocumentsManager` | `getRecents`: returns unmodifiable copy | unit | none | missing | verifies defensive copy | ⬜ |
| `RecentDocumentsManager` | constructor: strips non-existent paths from loaded list and persists if any removed | unit | none | missing | startup cleanup logic; untested | ⬜ |
| `RecentDocumentsManager` | constructor: gracefully skips malformed path strings | unit | none | missing | robustness under corrupt prefs | ⬜ |
| `StartupAction` | pure enum — `DO_NOTHING`, `SHOW_FILE_CHOOSER`, `OPEN_MOST_RECENT` | none | — | — | — | — |

**4E notes (quality concerns):**

The darkest gap in this package is `RecentDocumentsManager` — it has zero tests despite containing real MRU logic (dedup, cap enforcement, path normalization, constructor-time stale-path pruning) and notification side effects. `Prefs` itself has only five test methods, covering exclusively the `getMap`/`putMap` family; every scalar getter, every `put` overload, `putStringList`, `getStringList`, `reset`, `resetAll`, `parseJsonValue`, `writeTyped`, `migrate`, and `removeObsoleteKeys` are all completely untested. The `migrate` method in particular is high-risk: it touches a one-time destructive file operation (deleting the old `.properties` file) and uses `writeTyped` string-to-typed coercion, both of which could silently corrupt prefs on first launch from an old installation. Three of the four existing tests are inadequate by the Quality Principles: `testGetMapReturnsEmptyMapForMissingKey` has a name mismatch (the key has a default), and both round-trip/merge tests assert only `containsKey` rather than verifying actual stored values — a mutant that stores wrong values would survive all of them. The `Prefs` singleton's all-static API makes it straightforwardly unit-testable (the real singleton initializes from classpath resources during tests); no mocking of the singleton chain is needed here.

