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

The bus does not keep its subscribers alive by default, so **a subscriber needs a
strong reference for as long as it should live**. A subscriber with a natural
owner — a component in a container, a controller on the view it drives — gets it
from that owner. A subscriber that lives for the process and has no owner is
annotated `@Listener(references = References.Strong)`, which makes the bus hold
it strongly; whoever constructs it then drops the reference, because nothing else
needs to hold it. A static field kept only to prevent collection is a sign the
annotation is missing. That much is a familiar hazard.

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

## A registration has one owner

Every registration is tied to a lifetime when it is made. A listener retired
while the process continues owns its registration and ends it by disposing what
it was handed; a listener that lives for the process registers for the process
and holds nothing, because nothing ends it. There is no third way in and no other
way out. See [lifecycle.md](lifecycle.md) for who owns what, and when it ends.

## The bus is supplied by the entry point

There is no bus until something sets one, and a post or subscribe before then is
a fatal error. The entry point sets the bus once, before anything can post or
subscribe, and keeps it for the life of the process. A process with a different
entry point — a headless conversion — sets a bus of its own.

What happens when a handler throws during delivery is a per-process policy fixed
when the bus is built, not a property the bus exposes. The entry point chooses
the policy by choosing the bus. For SongScribe that is a fatal dialog.
