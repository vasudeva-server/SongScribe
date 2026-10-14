# Guides

Rules for adding or changing a file in `guides/`. The reader of these files is Claude.

## Rules for every file

- Put in a guide only what the code, the Javadoc and the Serena tools do not give: get what a type
  holds from `jet_brains_get_symbols_overview`, what uses it from
  `jet_brains_find_referencing_symbols`, its subtypes from `jet_brains_type_hierarchy`, and its
  purpose, contract and rationale from its Javadoc.
- Put each file in the subfolder for its kind: `subsystems/`, `conventions/`, `concepts/` or
  `tool-references/`. Give two files for the same area the same name in different subfolders.
- Write no frontmatter.
- Enclose every type name and method name in backticks, so that `jet_brains_rename` updates it.
- Write a rule as one imperative sentence. Add one "because" clause only when a reader would
  otherwise "fix" the rule away, and one short code example only when the rule is a code shape
  that prose cannot state clearly.
- Write a step sequence as a numbered list, and use numbers for nothing else.
- Write no structure (ownership, calls, references, creation), no role of a type, no behavior (what
  an operation does, its output, its order, its edge cases, what it asks the user), no rationale
  beyond one "because" clause, and no history.
- Name no member (method, field, parameter, signature), except a method that a rule requires
  callers to use.
- Write no sentence that a Javadoc already states, and no number or constant.
- Draw no diagram of any kind: text, image, SVG or HTML. A guide is Markdown text only.
- Move a behavior or rationale statement that is one of the five clause types in
  [Contracts](contracts.md) into the Javadoc of the type it belongs to, and delete any other.
- Update every link to a file by hand when the file moves, because `jet_brains_move` does not
  update Markdown links. Then run `jet_brains_run_inspections` on `CLAUDE.md`, the files in
  `.claude/rules/` and the guides, and confirm that none reports "Cannot resolve file" or "Cannot
  resolve directory".

## `subsystems/`

- Write only three parts: one sentence that names the type to read first, a `## Membership` list of
  type names with no descriptions, and a `## Rules` list of rules that no single type can enforce.
- List interfaces and abstract types, and leave out a concrete subtype of a listed type.
- Delete a rule that a reader who read every Javadoc in the subsystem cannot break.
- Change the file only when a type joins or leaves the subsystem, the entry point changes, or a
  cross-type rule changes, never for a change to behavior, structure or a role.
- Keep the file within 60 lines, and split a subsystem that outgrows them.

## `conventions/`

- Write rules for one kind of code or one activity that apply in more than one subsystem, and put a
  rule that applies in one subsystem in that subsystem's file.
- Write an ordered activity as a numbered list.
- Delete a rule that a reader who reads the code and the Javadoc cannot break.

## `concepts/`

- Write one file for each concept area, and one `##` entry for each term: the term, a definition of
  at most three sentences, and the type that models it, in backticks.
- Write no statement of how the code computes or uses the concept.
- Put a term that only one type uses in that type's Javadoc, not here.

## `tool-references/`

- Open the file with one sentence that names the tool and states that the file holds only what the
  tool's own documentation does not give.
- Write only defects (the symptom the reader sees, and the workaround), capabilities that the
  tool's schema does not make clear (with the condition that triggers their use), usage rules, and
  a short call sequence where the order is the fact.
- Delete a line that the tool's own documentation states.
- Move a rule that the reader needs every time the tool is used to `.claude/rules/`, and write no
  fact that a `.claude/rules/` file already states.
- Write no project structure, behavior or convention, and no story of how a defect was found.
- Delete a defect entry when the defect no longer shows its symptom, and update the file when the
  tool renames or removes a parameter that it names.
