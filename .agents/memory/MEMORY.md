# Team Memory Index

Shared memory for agents and developers working in this repository.
Each entry is a memory file with YAML frontmatter and a body. Types: `feedback`, `project`, `user`, `reference`.

- [Proto Builder DSL Policy in Tests](feedback_proto_builder_dsl.md) — Use Kotlin DSL for `.build()` only; plain Java chains for `.buildPartial()`; never use `.apply {}` with proto builders
- [`@NonValidated` on `buildPartial()` stubs](feedback_non_validated_annotation.md) — Annotate every function return type that returns a `buildPartial()` proto with `@NonValidated`
- [which-fixer applied](which-fixer-applied.md) — bulk sweep done; skill now runs in incremental mode
