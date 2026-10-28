---
slug: catch-up-held-messages-across-pages
branch: claude/hopeful-hawking-244608 (PR #1699)
owner: claude
status: in-progress
started: 2026-10-27
---

# A completed catch-up delivers its held messages page by page

Found while testing PR #1699: item 15 of "Found during planning" in
`catch-up-as-process-managers.md`. The defect is older than #1699.

## Status

- [x] Reproduced: `ParallelCatchUpsIgTest` with pages of 4 failed 68 of 109 cases.
      Every failure had extra copies, and 61 cases had the events out of order too.
- [x] A small failing spec: `HeldCatchUpMessagesIgTest`.
- [ ] Plan approved (CLAUDE.md gate).
- [x] Design reviewed by an agent. Its changes are in "Design".
- [x] Fix, tests, docs.
- [x] Verification, 2026-10-27: the matrix, 217 cases at both page sizes, all green
      (236 s); `build dokkaGenerate` green, `:server:test` 2425 tests, 0 failures.
      Again on 2026-10-28, on top of the merge of #1701: `build dokkaGenerate` green,
      `:server:test` 2441 tests (1 skipped), 0 failures; the matrix 217 cases.
- [x] Lands in PR #1699, on the owner's request (2026-10-28), not in a PR of its own.
      Committed to its branch on 2026-10-28. No version bump: #1699 is at `.610`, above
      `master` (`.600`, since #1701 merged).

## The defect

While a catch-up finalizes, the `Delivery` holds back the messages of its targets. It holds the
live events, and the copies of the last events of the history, which the catch-up reads once
more. A live event posted just before that last read is in the inbox twice, and it comes before
the copies of the events that preceded it, because the copies are written later.

Once the job is `COMPLETED`, `CatchUpStation.completedWith()` drops the second copies and sorts
the events by time, but within one page only. The `Delivery` reads a shard page by page. With
pages of 2, a tally that gets events 1, 2 and 3 holds `[1, 3, 2, 3]`.

## Design

When a page has a message held for a completed job, the `Delivery` adds the shard's other
messages held for completed jobs to the conveyor of that page. The station then drops the
copies and sorts all of them at once (`Delivery.withHeldForCompleted()`).

- **Why the order holds.** All the held messages of a completed job are in the shard when
  the first run that sees it completed starts, and that run starts from the beginning of
  the shard. A live message to its targets that is not held comes after all the held ones.
- **The jobs are read again after the extra read.** A new catch-up of the same instances may
  start in between. Its start signal is a held message too, and the old list would give it to
  the completed job. If the jobs changed, the run ends without delivering the page.
- **One query.** `InboxStorage.readToCatchUp(ShardIndex)` reads the shard's `TO_CATCH_UP`
  messages unpaged and unordered. The page cursor (`received_at >` the last one read) would
  skip messages with equal timestamps, and the station sorts anyway. `NoOpInboxStorage`
  returns none.
- **Whole shard, not per inbox.** Per inbox, every later page with new inboxes would scan
  the held messages again. The memory is about that of the last read of the history, which
  the catch-up process already holds.
- `CatchUpJobs.heldForCompleted(InboxMessage)` tells the messages to take.

Older gaps that this fix leaves open:
- A failed delivery throws before the changes of the page are written. On the retry,
  the messages delivered before the failure are delivered again.
- A new catch-up of the same instances may start before the held messages of the completed
  one are delivered. The new job then takes them, and in progress, it drops the copies within
  a page only.

## Tests

- `HeldCatchUpMessagesIgTest`:
  - pages of 1, 2 and 3 give `[1, 2, 3]`; before the fix, `[3, 1, 2, 3]`, `[1, 3, 2, 3]` and
    `[1, 2, 3, 3]`;
  - a catch-up started between the two reads of the jobs keeps the held messages. Without
    the second read, the tally gets `[1, 2, 3]` from the completed job: checked by removing it.
- `ParallelCatchUpsIgTest`: the page size is a dimension, the default and 4: 217 cases.
- `CatchUpJobsSpec`: `heldForCompleted()`.
- `InboxStorageSpec`: `readToCatchUp()`.
