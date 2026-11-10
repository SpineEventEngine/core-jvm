# Rejections in command filters and errors in `Status`

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.620` and to
`2.0.0-SNAPSHOT.660`. In `.620`, a command filter can no longer reject a command with a business
rejection. In `.660`, `Status` loses its `rejection` and `error` fields. Applications need code
changes if they have a `CommandFilter` that calls `reject(envelope, RejectionThrowable)`, a class
that implements `ServerErrorHandler`, or code that reads or builds `Status.rejection` or
`Status.error`.

## What changed, in one paragraph

A `CommandFilter` could reject a command with a business rejection. The rejection went to the
client in the `Status.rejection` of the acknowledgement, and, as an event, to its subscribers.
This made a filter act as a command handler, and the rejection told the client why the command
was refused. Now a filter only checks that a message is valid before it is posted to the bus.
`reject(envelope, RejectionThrowable)` is removed, with no replacement in filters. A refused
request tells the client only the kind of the problem, in `Status.error_case`, so
`Status.rejection` and `Status.error` are removed.

## At a glance

| Area | Before | After |
|------|--------|-------|
| `CommandFilter.reject(envelope, RejectionThrowable)` | public | **removed** (`.620`) |
| `CommandBus.initObservers(EventBus)` | public | **removed** (`.620`) |
| `CommandId.reject(Event)`, `Command.reject(RejectionThrowable)` (Kotlin) | public | **removed** (`.620`) |
| `Status.error_case` | — | **new** (`.620`): a `spine.core.ErrorCase` |
| `Status.rejection`, `Status.error` | sent to clients | **removed** (`.660`); names and numbers reserved |
| `ServerErrorHandler` | `BiConsumer<Message, Error>` | `BiConsumer<Message, ErrorCase>` (`.660`) |
| `Responses.rejectedBecauseOf(Event)`, `Responses.errorWith(Error)` | public | **removed** (`.660`) |
| `causedError(Message, Error)` (Kotlin) | public | **removed** (`.660`) |
| `ResponseMixin.error()` | public | **removed** (`.660`); use `errorCase()` |
| `ResponseMixin.isError()`, `ResponseStatusSubject.isError()` | `Status.error` | `Status.error_case` (`.660`) |
| `ResponseStatusSubject.isRejection()` | public | **removed** (`.660`) |
| `ErrorCase.toError()` | public (`.620`) | **removed** (`.660`) |
| `StreamObservers.fromStreamError()`, `MetadataConverter` | public | **removed** (`.660`); use `errorCaseOf()` |

## 1. Move business rules out of filters

A filter is an anti-corruption layer: it checks that a message is valid before the message is
posted to the bus. A business rule, such as whether the user may do what the command asks,
does not belong in a filter.

Move such a check to the command handler. A handler can reject the command by throwing
a rejection. The client receives the rejection as an event, through a subscription made before
posting the command.

## 2. What the client receives

An acknowledgement that refuses a command carries `Status.error_case`. The Java client passes
the `ErrorCase` to the `ServerErrorHandler` of the request, and cancels the subscriptions made
for the command.

Before `2.0.0-SNAPSHOT.660`, the handler received an `Error` made of the error case, with
the number of the error case in `code` and its name in `message`. `ErrorCase.toError()`,
which made such an `Error`, is removed.

## 3. Move off `Status.error` and `Status.rejection`

- A `ServerErrorHandler` takes a `Message` and an `ErrorCase`. A lambda compiles unchanged;
  a class that implements the interface changes the type of its second parameter.
- Read the kind of an error with `errorCase()` of a `Response` instead of `error()`, and
  the kind of an error of a failed call with `StreamObservers.errorCaseOf(Throwable)` instead
  of `fromStreamError()`.
- Build an error status with `Responses.errorWith(ErrorCase)`. A filter refuses a message with
  `reject(envelope, ErrorCase)`.
- A rejection reaches the client only as an event, through a subscription (see section 1).
