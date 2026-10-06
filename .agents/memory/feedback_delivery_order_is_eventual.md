---
name: feedback_delivery_order_is_eventual
description: Inbox delivery order across writers and nodes is eventually consistent by design — do not "fix" the page reading of Delivery/InboxStorage for it, and never build delivery tests on same-timestamp (frozen-clock) messages
metadata:
  type: feedback
---

Signals emitted on different JVM nodes may be delivered in an order other than the one
they were emitted in. That is by design. Only a global consensus (Paxos, Raft) could
prevent it, and it would kill the idea of the framework. Business rules stay intact
because end-users design their entities around eventual consistency, and each entity is
modified within a single node at a time, so without concurrency.

**Concrete case (2026-10-06, PR #1682).** The PR removed the `synchronized` modifiers of
`InboxStorage` — the owner counts that as proven safe. Along the way, the agent also
"fixed" the page cursor of `Delivery` for two cases:
- messages stamped earlier but stored after a later one was read;
- messages with equal stamps at a page boundary.

That added an SPI method, a Datastore index, and a chain of review findings (catch-up
duplicates, deduplication gaps, an unbounded read). The owner asked why page reading was
changed at all — "it was all OK before your changeset" — and ruled:

- A message stored late and delivered after later ones is the cross-node reordering
  above. It is not a defect, as long as the message is delivered.
- Tests that fill the inbox storage with same-timestamp messages (a frozen clock set with
  `Time.setProvider()`) "must NOT be ever created again: they are misleading."
  `Time.currentTime()` emulates nanoseconds, so stamps differ within one JVM. The
  `StackOverflowError` of the synchronous observer "found" that way was an artifact.

**Why:** a frozen clock makes a state one JVM never reaches in production, and then
"proves" a defect from it. A fix for an ordering property the framework does not promise
adds surface, risk and breaking changes for nothing.

**What makes late writes safe — data loss is what would need a fix.** A message stored
after a run's page cursor passed its stamp is still delivered, checked in the code:
- every write notifies the shard observers once stored (`NotifyingWriter` →
  `DeliveryDispatchListener`; for a multicast signal, after `Bus.doPost()` completes its
  dispatch, in `finally`);
- every run reads the shard from its start; the cursor lives only within a run;
- `deliverMessagesFrom()` repeats the run while the run delivers anything. After
  releasing the shard, it notifies once more through `newestMessageToDeliver()`, which
  covers a notification that found the shard held by another node.

**How to apply.**
- Do not change how `Delivery`, `InboxPage` or `InboxStorage` read a shard in order to
  order messages across writers or nodes.
- Never write delivery or inbox tests that give several messages the same stamp, and
  never build the case for a defect on such tests.
- Before fixing ordering or timing in `Delivery`, check that the property is a guarantee
  of the framework at all; if unsure, ask the owner first.

Related: [[feedback_proportionate_api_docs]].
