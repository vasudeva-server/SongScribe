# Branch Topology

Feature branches are based on `develop`, not `main`. Before any operation that
references a base branch — diff, checkout, rebase, or PR creation — verify the
actual parent with `git log --oneline --graph` or `git merge-base`. Never
assume `main`.
