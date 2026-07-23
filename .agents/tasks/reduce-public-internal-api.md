# Reducing the `public` `@Internal` API of the `server` module

Parent plan: [de-event-sourcing-plan.md](de-event-sourcing-plan.md).

The entity-hierarchy rework of the de-event-sourcing train introduced a number
of `public` members annotated `@Internal`: Java has no counterpart of Kotlin's
`internal` visibility, and Java framework collaborators could not otherwise
reach the implementation members they need. The goal of this task is to migrate
the involved Java code to Kotlin so that such members can use the `internal`
access level within the `server` module, shrinking the exposed API.

## Step 1 — merge `AssigneeEntity` into `SignalDispatchingEntity` ✅

Done (2026-07-23, this branch):

- `io.spine.server.command.AssigneeEntity` (Java) merged into
  `io.spine.server.entity.SignalDispatchingEntity` (Kotlin): the cached
  `producerId()`, the abstract `dispatchCommand(CommandEnvelope)`, and the
  `expectedXxx`/`unexpectedXxx` `ValueMismatch` helpers. The class now extends
  `TransactionalEntity` directly and implements `Assignee`.
- The mismatch-helper family is documented as the "Reporting value mismatches"
  section of the class-level KDoc: the idea (reporting a discrepancy between
  the state expected by a command and the actual one, stamped with the entity
  version) and why the methods are `protected` (they serve the receptor bodies
  of subclasses and are useless outside the entity's own handling code).
- `DispatchCommand` moved (`git mv`) from `io.spine.server.command` to
  `io.spine.server.entity`. It calls the `protected dispatchCommand` via
  Java same-package access; that only keeps working if it lives in the package
  of the (now Kotlin) declaring class. The alternative — widening
  `dispatchCommand` to `public @Internal` — would contradict this task's goal.
- `EventDispatch` moved (`git mv`) from `io.spine.server.event` to
  `io.spine.server.entity` for consistency with `DispatchCommand`: both are
  the dispatch operations wrapped by the entity-package `Phase` classes.
- `AssigneeEntityTest` + `AssigneeEntityTestEnv` replaced by the Kotlin
  `SignalDispatchingEntitySpec` (same-package spec with a private fixture
  re-exposing the `protected` helpers).

Not touched on purpose: `io.spine.server.entity.model.AssigneeEntityClass`
(the model class keeps its name and hierarchy; rename is a possible follow-up
once the `@Internal` analysis says what happens to the model layer).

## Step 2 — analyze and shrink the `@Internal` surface (next)

Inventory the `public` + `@Internal` members across the entity hierarchy
(`SignalDispatchingEntity`, `TransactionalEntity`, `AbstractEntity`,
repositories) and, per member, decide: Kotlin `internal` (converting the Java
caller if needed), `protected`, or keep. To be planned with the product owner.
