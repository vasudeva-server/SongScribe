# Unit Test Guide

Read [testing-common.md](./testing-common.md) first.

## Parameterized tests for equivalence classes and invariants

- Write a row, not a method, from the first case that would sit beside a sibling exercising the
  same method with the same assertion shape and only the input, the expected value or a small piece
  of arrange code differing; the varying piece may be a lambda held in a `record` field.
- Run that check before copying an existing test and changing a literal.
- Use `@ParameterizedTest` with `@MethodSource` (or `@EnumSource` for a plain enum) for an
  enumerated domain or an invariant over many inputs.
- Assert the property, not one expected output per input, when the case is an invariant.
- Use a `record` case table for varying input mapped to a specific expected value, with a
  description field as the display name through `name = "{0}"`:

```java
private record DeleteLabelCase(String description, List<ElementType> types, String expectedKey) {}

@ParameterizedTest(name = "{0}")
@MethodSource("deleteLabelCases")
void testDeleteLabel(DeleteLabelCase testCase) {
    assertThat(OpNames.deleteLabel(testCase.types()))
        .isEqualTo(Strings.get(testCase.expectedKey()));
}
```

See [Contracts](./contracts.md) and
`~/.claude/guides/design.md` for how the cases fall out of the contract and
whether the behavior earns a test.

### Asserting that a table is exhaustive

- Derive the rows from the domain wherever a class Javadoc claims the domain is enumerated:
  `Stream.of(ArticulationType.values())`.
- When each row needs a fixture that cannot be derived from the constant, keep the table and assert
  its coverage separately:

```java
@Test
void testCasesCoverEveryArticulationType() {
    assertThat(cases().map(ArticulationLabelCase::type))
        .containsExactlyInAnyOrder(ArticulationType.values());
}
```

- For a sealed hierarchy, assert against the leaves reached by walking `getPermittedSubclasses()`
  until it returns empty.
- When the domain is private, reword the Javadoc to claim what the table covers, or make the
  taxonomy part of the contract; never widen it.

## Golden-file fixtures

- Name in the test's Javadoc what its fixture pins and nothing more.
- Exclude from the comparison any output that varies without a behavior change, such as the
  writer's `<software>` version string.
- Regenerate an intended change with `./scripts/test.sh --generate-fixtures VoltaAlignmentFixtureTest`.
- Read the property `songscribe.updateFixtures` in a new golden-file test and write the fixture from
  the current output instead of comparing, so that one option regenerates every fixture.
- Open the rewritten fixture in the application and check it before committing it, because the
  test cannot judge the new output.

## Creating staff elements

- Create staff elements with `songscribe.dom.StaffElementFactory`, never with
  `ElementType.X.newInstance()` and never with a local `note()` or `crotchet()` helper.
- Add a method to `StaffElementFactory` when the type you need is missing.

```java
var note = crotchet();
var pitched = StaffElementFactory.createNote(staffPosition, upper);
```

## The MainFrame is injected, never mocked as a singleton

- Take a `MainFrame` as a constructor or factory parameter, build one in the test with
  `MockEnvHelper.setupMockEnv()`, and assert against the model or the return value, never against
  the mock.
- Never `mockStatic(MainFrame.class)`; code under test that reaches `MainFrame.getInstance()` is a
  constructor-injection finding.
- Give a mocked root pane a real `InputMap` and `ActionMap` when the code triggers `Actions`
  initialization, because without them the failure is a `NullPointerException` in static
  initialization that does not name the missing mock.

## AssertJ null checks and NullAway

- Use `assertThat(x).isNotNull()` to narrow `x` for NullAway in a new test, instead of a manual
  `throw new AssertionError` guard, because it reports "expected non-null but was null".

## Reading FlatLaf properties from a test

- Call `UnitTest.installFlatLafDefaults()` from `@BeforeAll` in a test that reads a FlatLaf
  property, because without it the read fails as a missing key rather than as missing setup.
