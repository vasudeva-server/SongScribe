---
paths: "**/*.{kt,kts}"
---

# Kotlin code style

## Braces

Always use braces for `if`, `else`, `for`, `while`, and `do` statements, even if the body is a single statement. This improves readability and prevents bugs when adding new statements later.

```kotlin
// Bad — no braces
if (condition) doSomething()

// Good — braces
if (condition) {
    doSomething()
}
```

## Control structure vertical spacing

Separate control structures from surrounding lines with a blank line to improve readability, unless at the beginning or end of a containing block. This includes `if`, `else if`, `else`, `for`, `while`, and `do` statements.

```kotlin
// Bad — no blank lines around control structures
val foo = "foo"
if (condition) {
    doSomething()
}
doSomethingElse()

// Good — blank lines around control structures
val foo = "foo"

if (condition) {
    doSomething()
}

doSomethingElse()

fun doSomething() {
    if (condition) {
        doSomethingElse()
    }
}
```
