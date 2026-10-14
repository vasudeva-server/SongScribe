## Step 9: Coverage — once, scoped

`./scripts/coverage.sh unit <the target's test classes>`. Once, as the closing
step, never as a target.

Of **each** unexecuted region, ask exactly one question:

> Does this correspond to a contract case that is missing, or to implementation
> the contract promises nothing about?

The first answer amends the contract and its tests. The second leaves it alone,
**and you write down why**. If nothing can reach a region at all, that is a
dead-code finding against production.

**You never write a test to turn a region green.**

Re-run the unit suite. **Snapshot.**
