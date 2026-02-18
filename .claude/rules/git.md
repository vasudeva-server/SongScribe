## Git Conventions

### Commit Messages

- Reference issue numbers when applicable
- Be descriptive: "feat: add dynamic layout rendering" not "update code"
- First line should begin with a type prefix (e.g., feat:, fix:, docs:, style:, refactor:, test:, chore:)
- Keep first line under 72 characters
- Use imperative mood: "add feature" not "added feature"

### Branch Naming

- Feature branches: `feature/description-with-dashes`
- Bug fixes: `fix/description-with-dashes`

### Creating Commits

Always use the `/commit-commands:commit` skill to create commits instead of manually constructing git commands with bash. The skill handles:
- Proper staging and unstaging of files
- Message formatting and validation
- Co-authorship attribution
- Multi-line messages without quoting issues
- Error handling and retry logic

This avoids shell quoting complexities and ensures consistent, high-quality commits.
