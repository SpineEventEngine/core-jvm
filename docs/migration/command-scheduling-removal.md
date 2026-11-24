# Command scheduling is removed

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.780`. The server no longer
delays commands. An application that never scheduled commands needs no code changes, but
should read [Before you deploy](#before-you-deploy).

## What changed, in one paragraph

A sender could ask the server to run a command later, by setting a delay in the context of
the command. The server kept such a command in memory and posted it when the delay passed, so
a restart lost it. A scheduled command meant for an aggregate or a process manager was
acknowledged, but not delivered. Now the server runs every command when it receives it, and
an application decides by itself when to post a command.

## At a glance

| Area | Before | After |
|------|--------|-------|
| `CommandContext.schedule`, `CommandContext.Schedule` | the delay of a command | **removed**; name and number reserved |
| `Command.isScheduled()` | public | **removed** |
| `CommandScheduler`, `ExecutorCommandScheduler` | public | **removed** |
| `ServerEnvironment.newCommandScheduler()` | public | **removed** |
| `ServerEnvironment.Builder.scheduleCommandsUsing()` | public | **removed** |
| `CommandValidationError.EXPIRED` | not used | **removed**; name and number reserved |
| `BusFilter.reject(envelope)` | refuses a message with the `OK` status | **removed**; use `reject(envelope, ErrorCase)` |
| `GivenCommandContext.withScheduledDelayOf()` | public, in the test library | **removed** |

## 1. Post a command when it is due

Keep the schedule outside of the command: in a job scheduler, in a task queue of your cloud
platform, or in the state of a process manager. When the time comes, post the command as any
other:

```java
client.onBehalfOf(user)
      .command(sendReminder)
      .postAndForget();
```

## 2. Make filters pass or refuse

`BusFilter.reject(envelope)` stopped a message, but told its sender that the message was
accepted. It served the scheduling only. A filter now either lets a message pass, or refuses it
with `reject(envelope, ErrorCase)`.

## Before you deploy

1. **Drain the inboxes, and let the running catch-ups finish.** Some storages look up inbox
   messages by the position of their status in its enumeration, and a removed status moves
   the positions of two others. The messages that wait for an ordinary delivery are not affected.
   Those already delivered, and those held for a catch-up, are.
2. **Post the commands that still wait for their time.** The server kept them in memory only.
3. **Update the senders.** The server ignores a delay set by a sender that was not updated, and
   runs its command at once.
4. **Remove the stored System events of scheduled commands.** The System contexts stored them if
   `SystemSettings.persistEvents()` was called. Their type is removed, so the code that unpacks
   them fails. So does a query of the stored System events that has filters, but none of them by
   the type of the event. Catch-up and other queries by event types are not affected.
5. **Remove the other leftovers, if you want.** If the command log was enabled, the System contexts
   stored a record for each scheduled command. The inboxes may hold scheduled commands that were
   never delivered. The server acts on neither of them.
