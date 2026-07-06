---
name: feedback-kdoc-tag-descriptions-sentence-case
description: KDoc/Javadoc @param/@return/@throws descriptions start with a capital letter — legacy Javadoc uses lowercase, do not carry it over
metadata:
  type: feedback
---

Start every KDoc tag description (`@param`, `@return`, `@throws`) with a capital letter
and end it with a period: `@param I The type of the entity identifiers.`
Descriptions opening with a code span (e.g. `` `true` if... ``) stay as they are.

**Why:** User feedback on the `TransactionalEntity` conversion (2026-07-06):
"We use Sentence capitalisation for parameter description." The rule is stated in
`.agents/guidelines/kdoc.md` § Layout of descriptions; the trap is Java→Kotlin
conversions — legacy Javadoc in this repo often uses lowercase
(`@param id the ID...`), and the `java-to-kotlin` skill's "preserve the original
wording" does **not** extend to preserving lowercase tag descriptions.

**How to apply:** When converting Javadoc or writing new KDoc, capitalize the first
word of each tag description. Re-check `@param`/`@return`/`@throws` lines before
finishing a conversion.
