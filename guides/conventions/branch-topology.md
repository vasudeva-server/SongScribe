# Branch topology

- Base a feature branch on `develop`, never on `main`.
- Verify the actual parent with `git log --oneline --graph` or `git merge-base` before any diff,
  checkout, rebase or PR creation that references a base branch.
