---
name: writer
description: >
  Write, edit, and restructure user-facing and developer-facing documentation.
  Use when asked to create/update docs such as `README.md`, `docs/**`, and
  other Markdown documentation; 
  when drafting tutorials, guides, troubleshooting pages, or migration notes; and
  when improving inline API documentation (KDoc) and examples.
---

# Write documentation (repo-specific)

## Decide the target and audience

- Identify the target reader: end user, contributor, maintainer, or tooling/automation.
- Identify the task type: new doc, update, restructure, or documentation audit.
- Identify the acceptance criteria: “what is correct when the reader is done?”

## Choose where the content should live

- Prefer updating an existing doc over creating a new one.
- Place content in the most discoverable location:
  - `README.md`: project entry point and “what is this?”.
  - `docs/`: longer-form docs (follow existing conventions in that tree).
  - Source KDoc: API usage, examples, and semantics that belong with the code.

## Follow local documentation conventions

- Follow `.agents/documentation-guidelines.md` and `.agents/documentation-tasks.md`.
- Use fenced code blocks for commands and examples; format file/dir names as code.
- Avoid widows, runts, orphans, and rivers by reflowing paragraphs when needed.

## Make docs actionable

- Prefer steps the reader can execute (commands + expected outcome).
- Prefer concrete examples over abstract descriptions.
- Include prerequisites (versions, OS, environment) when they are easy to miss.
- Use consistent terminology (match code identifiers and existing docs).

## KDoc-specific guidance

- For public/internal APIs, include at least one example snippet demonstrating common usage.
- When converting from Javadoc/inline comments to KDoc:
  - Remove HTML like `<p>` and preserve meaning.
  - Prefer short paragraphs and blank lines over HTML formatting.

## Validate changes

- For code changes, follow `.agents/running-builds.md`.
- For documentation-only changes in Kotlin/Java sources, prefer `./gradlew dokka`.

