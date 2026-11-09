# Rejections in command filters

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.620`, in which a command
filter can no longer reject a command with a business rejection. Only applications with
a `CommandFilter` that calls `reject(envelope, RejectionThrowable)` need code changes.

## What changed, in one paragraph

A `CommandFilter` could reject a command with a business rejection. The rejection went to the
client in the `Status.rejection` of the acknowledgement, and to the subscribers of the
rejection. This made a filter act as a command handler, and it told the client why the command
was refused. Now a filter only checks that a message is valid before it is posted to the bus.
`reject(envelope, RejectionThrowable)` is removed, with no replacement in filters, and
`Status.rejection` is deprecated.

## At a glance

| Area | Before | After |
|------|--------|-------|
| `CommandFilter.reject(envelope, RejectionThrowable)` | public | **removed** |
| `Status.rejection` | sent for a command rejected by a filter | **deprecated**; not sent |
| `Responses.rejectedBecauseOf(Event)` | public | **deprecated** |
| `ResponseStatusSubject.isRejection()` | public | **deprecated** |
| `CommandBus.initObservers(EventBus)` | public | **removed** |
| `CommandId.reject(Event)`, `Command.reject(RejectionThrowable)` (Kotlin) | public | **removed** |
| `Status.error_case` | — | **new**: the kind of the problem, a `spine.core.ErrorCase` |

## 1. Move business rules out of filters

A filter is an anti-corruption layer: it checks that a message is valid before the message is
posted to the bus. A business rule, such as whether the user may do what the command asks,
does not belong in a filter.

Move such a check to the command handler. A handler can reject the command by throwing
a rejection. The client receives the rejection as an event, through a subscription made before
posting the command.

## 2. What the client receives

The server no longer puts a rejection into an acknowledgement. The Java client treats
an acknowledgement with `Status.rejection` as an invalid status, as it did before.

An acknowledgement may now carry `Status.error_case`. The Java client passes the `ErrorCase`
to the `ServerErrorHandler` of the request. The subscriptions made for the command are
cancelled, as for any other error.

Before `2.0.0-SNAPSHOT.660`, the handler received an `Error` made of the error case, with
the number of the error case in `code` and its name in `message`. `ErrorCase.toError()`,
which made such an `Error`, is removed.
