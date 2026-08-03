---
name: warn-rg-usages-query
enabled: true
event: bash
action: warn
pattern: ^(?!.*\\s\*\\\().*\b(?:rg|grep)\b.*?(?:[A-Za-z_]\w*\\?\(|\b(?:extends|implements)\s+[A-Z]\w*)
---

⚠️ **This looks like a usages query — use serena, not `rg`.**

You are searching for a call shape (`name(`) or a type relationship (`extends`/`implements`). That is *determining the set of places a symbol is used*, which `.agents/rules/serena.md` requires you to start with `jet_brains_find_referencing_symbols`.

The rule fires on the **operation**, not on how the task was phrased. It still applies when you are verifying a claim in a plan, sizing a change, checking whether something is safe to delete, or "just checking quickly."

**What `rg` costs you here:**

- It searches **only the paths you name**, so it silently omits everything else — usually the tests. The result looks complete and is not.
- It matches **text, not types**, so it cannot report a call site's receiver type. You end up inferring types by eye from surrounding lines, which is exactly the judgment the type-aware tool exists to make for you.
- It counts comments, Javadoc and string literals as calls.

**Instead:**

```
jet_brains_find_referencing_symbols(name_path: "Class/method", relative_path: "src/main/java/.../Class.java")
```

**Legitimate exceptions** (proceed if one applies):

- Reading a constructor body — the documented `rg -n "ClassName\s*\(" path/to/ClassName.java` form, which this rule already exempts.
- Searching for **prose** mentions in comments, Javadoc or string literals, which `find_referencing_symbols` does not cover. Run the symbolic query **first** for the authoritative list, then the text search only for prose.
- The file is not Java, or the IDE connection is unavailable.
