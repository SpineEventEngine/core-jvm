---
name: feedback_proportionate_api_docs
description: Keep a caveat in API docs proportional to the contract — do not document failure modes that do not matter, and never let a caveat outweigh what the method actually does
metadata:
  type: feedback
---

Do not add caveats to API documentation for failure modes that carry no consequence.
A `<h2>` section on a method whose contract is four lines is always wrong, however
accurate the section is.

**Concrete case (2026-07-15).** A `registerWith()` that fails after its `super` call
leaves the repository's `Inbox` registered in a map inside the JVM-wide `Delivery`. A
"Registration is not atomic" block was added to `Repository.registerWith()` describing
this. The owner deleted it:

> It overstates the importance and obscures the main part of the documentation. If
> `registerWith` fails, it is a configuration error which should fail the creation of
> whole Bounded Context. Hanging inbox reference in a map is of no importance in the
> scale of the crash of context creation.

**Why:** a reader opens `registerWith()` to learn what it does. Every line spent on a
consequence they will never observe is a line pushing the contract further down. And
calling something a "leak" implies a running system accumulating garbage — when the real
scenario is a process that is about to fail startup, an accurate description creates a
false impression of significance.

**How to apply.** Before documenting a failure mode, ask what the reader would *do*
differently knowing it. If the answer is "nothing, because the application does not
start", leave it out entirely — including out of the task doc, at more than a line.
Failing loudly on a configuration error is correct behavior, not a defect needing a
disclaimer.

Worth keeping (this is the distinction, not a blanket "write less"): a constraint an
overrider would otherwise trip on. `checkDispatchesMessages()` runs *before* the context
is assigned, so an override cannot call `context()` — that changes what someone writes,
so it stays.

Related: [[feedback_kdoc_tag_descriptions_sentence_case]], [[feedback_document_internal_members]].
