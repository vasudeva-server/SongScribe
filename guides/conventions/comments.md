# Comments

The Java forms of the rules in *Comments* in `~/.claude/rules/development.md`. Read both before
you write or change a `//` or `/* */` comment. A Javadoc follows [Contracts](contracts.md).

- Extract a group of constants that needs a label into a nested `static final class` or an `enum`
  named for the group, because the type name then holds the label.
- Put a guarantee that a method relies on in a clause of type 1 in that method's Javadoc.
