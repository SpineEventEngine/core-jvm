# Drop `FieldMask` support in querying and subscription APIs

## Status

Implemented 2026-10-02 on branch `drop-support-of-field-mask` (uncommitted).
Verified: `./gradlew clean build dokkaGenerate` — BUILD SUCCESSFUL (client 189 tests,
server 2037 tests, 0 failures). Remaining: commit/PR, then the cross-repo follow-ups below.
Deprecated single-line delegates trigger advisory ErrorProne `InlineMeSuggester` warnings;
`@InlineMe` is not applicable because the methods are overridable.

## Context

Field masks trim entity states returned by queries and subscription updates. The
feature is being dropped: every API element that accepts or carries a `FieldMask`
on the **read path** stays (binary/wire compatibility) but is **deprecated and does
nothing** — results always contain all fields.

Decisions taken with the user:
- **Scope = whole read path**: client request API + server repository/storage read API.
  `FieldMasks` utility and `EventFieldFilter` (event trimming, not a query feature) stay
  untouched.
- **Storage contract**: the published fixture `DelegatingRecordStorageTest` asserts that
  storages **ignore** `RecordQuery.mask()`.

Findings that shape the plan:
- Subscriptions already ignore masks server-side: `Topic.field_mask` is only written by
  `TopicFactory`; no server code reads it.
- Queries honor masks via `ResponseFormat.field_mask` → `QueryConverter.fieldMask()` →
  `RecordQuery.mask()` → `TenantRecords.readAll()` → `FieldMaskApplier`. Typed queries
  carry masks via `EntityQuery.mask()` (spine-base) → `ToEntityRecordQuery.copyMask()`
  and `EntityQueryToProto.addFieldMask()`.
- `jdbc-storage` masks through core's `FieldMaskApplier` (becomes no-op automatically);
  `gcloud-jvm` has its own masker (follow-up).
- The branch already carries the version bump (`2.0.0-SNAPSHOT.560`).

## Deprecation conventions

- Java: `@Deprecated` **plus** a `@deprecated` Javadoc tag (Checkstyle `MissingDeprecated`
  is on), message along the lines of *"Field masks are no longer supported. This method
  does nothing; the results contain all the fields."* + the replacement, if any.
- Proto: `[deprecated = true]` (precedent: `core/.../event.proto`), comment rewritten to
  say the value is ignored.
- Bodies of deprecated members: ignore the mask, delegate to the mask-free counterpart.
  **Argument preconditions stay** (`checkNotNull`, non-empty IDs): `QueryBuilderSpec` runs
  `NullPointerTester.testAllPublicInstanceMethods()` over `QueryBuilder`, so the no-op
  `withMask(..)` overloads must still reject `null`.
- Main code must not use the deprecated generated accessors
  (`setFieldMask/getFieldMask/hasFieldMask`); tests that must touch them get
  `@SuppressWarnings("deprecation")` / `@Suppress("DEPRECATION")` at the narrowest scope.

## Changes — `client`

| File | Change |
|---|---|
| `client/src/main/proto/spine/client/query.proto` | `ResponseFormat.field_mask` → `[deprecated = true]`; fix `ResponseFormat` doc ("which data should be retrieved"). |
| `client/src/main/proto/spine/client/subscription.proto` | `Topic.field_mask` → `[deprecated = true]`; drop "If the field mask is set in `Topic`, this value is masked." from `EntityUpdate.state`. |
| `client/src/main/proto/spine/client/subscription_service.proto` | Drop "(like field masks)" from the `Subscribe` doc. |
| `TargetBuilder.java` | Both `withMask(..)` deprecated: `checkNotNull(fieldNames)`, then return `self()`, storing nothing. Remove `fieldMask` field, `composeMask()`, mask part of `toString()` (`SELECT *` always), mask mentions/examples in class Javadoc. |
| `FilteringRequest.java` | Both `withMask(..)` deprecated, return `self()` without touching the builder. |
| `QueryBuilder.java` / `TopicBuilder.java` / `ClientRequest.java` | `build()` no longer composes a mask; drop `.withMask(..)` from Javadoc examples. |
| `QueryFactory.java` | `byIdsWithMask(..)` → deprecated, returns `byIds(entityClass, ids)` (keeps its non-empty-IDs check); `allWithMask(..)` → deprecated, returns `all(entityClass)`. Drop `FieldMask` params from the package-private `composeQuery(..)` overloads and private `responseFormat(..)`; drop `fromPaths`; fix `byIds`/`all` docs that reference the mask variants. |
| `TopicFactory.java` | Package-private `composeTopic(Target, FieldMask)` → `composeTopic(Target)`. |
| `ResponseFormats.java` (package-private) | Drop the `mask` parameter. |
| `EntityQueryToProto.java` | Remove `addFieldMask(..)` — `EntityQuery.mask()` is no longer transferred. |

## Changes — `server`

| File | Change |
|---|---|
| `storage/QueryConverter.java` | Remove `fieldMask(builder, format)` and its two calls. |
| `entity/storage/ToEntityRecordQuery.java` | Remove `copyMask(..)`. |
| `storage/memory/TenantRecords.java` | `readAll(query)` stops applying `query.mask()`; delete unused `get(id, FieldMask)` (class is package-private); drop now-unused imports (`FieldMask`, `FieldMaskApplier`, `applyMask`, `pack`, `unpack`). |
| `storage/FieldMaskApplier.java` | Class deprecated; `apply()` returns its input unchanged; private masking helpers removed. Public constructor kept. |
| `storage/RecordStorage.java` | `read(id, mask)` → `read(id)`; `readAll(ids, mask)` → `readAll(ids)`; `toQuery(id, mask)` → `toQuery(id)`; `toQuery(ids, mask)` → `toQuery(ids)` — all deprecated. |
| `storage/DelegatingRecordStorage.java` | Overrides of the two mask overloads deprecated (keep delegating). |
| `entity/storage/EntityRecordStorage.java` | Public `readAll(ids, mask)` deprecated → `readAll(ids)` (same active+inactive semantics, verified). |
| `entity/RecordBasedRepository.java` | **New** `public Iterator<E> loadAll(Iterable<I> ids)` (replacement); `loadAll(ids, FieldMask)` deprecated → delegates to it. Fix docs of `findRecords(ResponseFormat)` / `find(TargetFilters, ResponseFormat)` that promise masking. |
| `entity/StorageConverter.java` | **New** `protected StorageConverter(TypeUrl, EntityFactory<E>)`; 3-arg constructor deprecated, ignores the mask. `fieldMask()` deprecated → `FieldMask.getDefaultInstance()`. `withFieldMask(..)` becomes concrete, deprecated, returns `this` (subclass overrides still compile). `doBackward()` no longer masks; mask dropped from `equals`/`hashCode`. |
| `kotlin/.../entity/DefaultConverter.kt` | Use the 2-arg constructor; drop the `fieldMask` param and the `withFieldMask` override. |

## Tests

Pattern: tests asserting trimmed results are flipped to assert **full** results (the mask
is ignored); tests that only exercised mask state (e.g. "last `withMask` wins") are deleted;
incidental `hasFieldMask()` assertions in unrelated tests are removed (one dedicated
"ignores mask" test per builder covers it).

- `client`: `QueryBuilderSpec.kt`, `TopicBuilderTest.java`, `QueryFactoryTest.java` +
  `given/QueryFactoryTestEnv.java` (replace `checkFieldMaskEmpty`/`verify*PathsInQuery`
  with a default-`ResponseFormat` check), `ResponseFormatsTest.java`, `TopicFactoryTest.java`.
- `server` tests: `RecordBasedRepositoryTest` (+ switch helper to new `loadAll(ids)`; add
  "deprecated `loadAll(ids, mask)` ignores mask"), `StandTest` (flip
  `forProjectionReadWithMask`; delete `handleMistakesInQuery` — its only "mistake" is an
  invalid mask path), `EntityRecordStorageTest` (flip the two `Read` tests),
  `DefaultConverterSpec.kt` (`withFieldMask` yields an equal converter).
- `server` testFixtures (published): `DelegatingRecordStorageTest` — flip
  `singleRecordWithMask`, `allByMask`, `allByIdsAndMask`,
  `manyRecordsBySeveralColumnsWithLimitAndMask` to expect records equal to the stored ones;
  keep the closed-storage ISE tests for the mask overloads. Remove the now-unused
  `assertOnlyIdAndDueDate`, `RecordBasedRepositoryTestEnv.assertMatches`, and
  `StandTestEnv.AssertProjectQueryResults`'s mask parameter.
- **New** `server/src/test/kotlin/io/spine/server/storage/FieldMaskApplierSpec.kt`
  (`internal`, Kotest): `apply` returns the same instance for a non-empty mask and `null`
  for `null` — keeps the no-op covered now that in-memory storage no longer calls it.
- Untouched: `FieldMasksTest`, `EventFieldFilterTest`.

## Execution order

1. Copy this plan to `.agents/tasks/drop-field-mask-support.md` (AGENTS.md task policy).
2. Protos → client main → client tests (`./gradlew :client:test`).
3. Server main → server tests/fixtures (`./gradlew :server:test`).
4. Run `update-copyright` on the changed source files (default profile is now CodeMatters,
   so touched files get the new header — consistent with the user's earlier run).
5. Full verification (below). No commits unless asked.

## Verification

- `JAVA_HOME=…/amazon-corretto-17.jdk/Contents/Home ./gradlew clean build` — protos
  changed, so `clean build`; run in background **without** a pipe, uninterrupted.
- `./gradlew dokkaGenerate` — catch broken `{@link}`/KDoc links to removed members.
- Grep gates: no `setFieldMask|getFieldMask|hasFieldMask|composeMask|FieldMaskApplier` in
  `*/src/main` outside the deprecated declarations themselves.
- Behavioral proof lives in the flipped tests: Stand query with a mask returns full
  projects; storage `read(id, mask)` / `RecordQuery.withMask()` return full records;
  client `withMask(..)` produces queries/topics without `field_mask`.

## Follow-ups (other repos, not in this change)

- `base-libraries` (spine-base): deprecate `QueryBuilder.withMask(..)`, `whichMask()`,
  `Query.mask()`; update `io.spine.query` package docs.
- `gcloud-jvm`: drop its own `record/FieldMaskApplier` masking — required to pass the
  updated `DelegatingRecordStorageTest`.
- `jdbc-storage`: remove the `FieldMaskApplier` call in `SelectMessagesByQuery` (already a
  no-op; clears deprecation warnings).
