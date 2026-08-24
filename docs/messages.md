# The Message Bus

Components talk to each other by posting messages rather than by holding
references. For the naming and subscribing conventions code follows, see
[messages.md](../.claude/guides/messages.md).

## Delivery is synchronous

Posting a message runs every handler for it, on the calling thread, before the
post returns. Nothing is queued and nothing is deferred.

That is relied on in more places than it looks. An edit can post a message and
then read the state a handler was expected to update. A guard set before a post
and cleared after it genuinely covers the whole delivery. Ordering between
handlers is decided entirely by priority, because there is no other source of
ordering.

## Subscribers are held weakly, which is not enough

The bus does not keep its subscribers alive, so **every subscriber needs a strong
reference elsewhere for as long as it should live** — a static field, or a field
on a longer-lived owner. That much is a familiar hazard.

The unfamiliar half is the other direction: a subscriber that has lost its last
strong reference is **still subscribed and still receiving messages** until the
collector runs, and the collector may never run. Dropping the reference is
therefore not a way to retire a subscriber. Weak references prevent a leak; they
do not perform a retirement.

Most subscribers never need retiring, because they live as long as the process.
Two kinds do:

- **A static field that gets reassigned.** Replacing a generation of long-lived
  objects leaves the outgoing generation subscribed, handling messages nobody
  meant for them.
- **An object retired while the process continues.** Loading a document replaces
  the previous one; left subscribed, it keeps handling broadcast commands and
  recording undo steps against a document nobody has open.

See [lifecycle.md](lifecycle.md) for how those are disposed.

## A scope replaces the bus; it does not layer on it

A bounded piece of work can push a bus of its own for its duration. Three things
follow, and each is a promise the scope makes:

- **It replaces.** While a scope is in force, a post reaches only what subscribed
  inside it. Whatever subscribed beneath hears nothing during, and hears nothing
  afterwards about what happened while it was open.
- **Closing discards everything subscribed inside it**, in one operation, with no
  per-subscriber bookkeeping. This is not disposal — it covers the unsubscribing
  and nothing else — and for a process about to exit it buys nothing at all.
- **The error handler is the scope's own.** This is the reason scopes exist in
  production: a headless conversion has no display for the fatal-error dialog the
  application bus ends in, so it supplies a handler that reports to the log
  instead.

What consumes the discarding half is the test suite, where a scope per test is
what keeps one test's subscribers out of the next.

**Unsubscribing reaches only the bus in force.** A subscriber that registered on
the application bus and tries to unsubscribe while a scope is open matches
nothing and stays subscribed. Disposal inside a scope is therefore not supported:
dispose where the bus that saw the subscription is still the bus in force.

The scope stack is process-wide rather than per-thread, because a bounded piece
of work may hand parts of itself to other threads and they must post to the same
bus. That is only coherent if scopes are pushed and popped when nothing else is
running — open one when the work begins, close it when the work is finished, never
around a stretch of a live application while other threads are still posting.
Nesting is fine; interleaving is not, and closing out of order is reported rather
than allowed through.
