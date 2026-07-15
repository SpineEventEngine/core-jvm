# Team Memory Index

Shared memory for agents and developers working in this repository.
Each entry is a memory file with YAML frontmatter and a body. Types: `feedback`, `project`, `user`, `reference`.

- [Proto Builder DSL Policy in Tests](feedback_proto_builder_dsl.md) — Use Kotlin DSL for `.build()` only; plain Java chains for `.buildPartial()`; never use `.apply {}` with proto builders
- [`@NonValidated` on `buildPartial()` stubs](feedback_non_validated_annotation.md) — Annotate every function return type that returns a `buildPartial()` proto with `@NonValidated`
- [No `@Nullable` on locals](feedback_no_nullable_on_locals.md) — the annotation does not apply to local variables (nullness is flow-inferred); keep it on parameters, returns, and fields — and never "restore" one removed from a local
- [which-fixer applied](which-fixer-applied.md) — bulk sweep done; skill now runs in incremental mode
- [Java→Kotlin visibility traps](java-to-kotlin-visibility-traps.md) — protected/package-private mapping, `@JvmName` for Java callers, `HasVersionColumn.getVersion()` clash
- [KDoc tag descriptions: sentence case](feedback_kdoc_tag_descriptions_sentence_case.md) — `@param I The type...`; don't carry lowercase over from legacy Javadoc
- [Document `internal` members](feedback_document_internal_members.md) — every Kotlin `internal` method gets KDoc; project convention
- [New proto types: separate file](feedback_proto_one_message_per_file.md) — one message per file recommended; don't append new types to existing multi-message protos
- [Kotlin over the storage SPI: two traps](kotlin-storage-spi-interop-traps.md) — `ExtractId` method refs fail under K2 (nullable Guava SAM → lambda + `requireNotNull`); proto-DSL receiver properties shadow outer params (bind a renamed local first)
