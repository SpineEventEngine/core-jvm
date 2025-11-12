# Guidelines for Junie and AI Agent from JetBrains

Read the `../.agents/_TOC.md` file to understand:
 - the agent responsibilities,
 - project overview,
 - coding guidelines,
 - other relevant topics.

Also follow the Junie-specific rules described below.

## Junie Assistance Tips

When working with Junie AI on the Spine Tool-Base project:

1. **Project Navigation**: Use `search_project` to find relevant files and code segments.
2. **Code Understanding**: Request file structure with `get_file_structure` before editing.
3. **Code Editing**: Make minimal changes with `search_replace` to maintain project consistency.
4. **Testing**: Verify changes with `run_test` on relevant test files.
5. **Documentation**: Follow KDoc style for documentation.
6. **Kotlin Idioms**: Prefer Kotlin-style solutions over Java-style approaches.
7. **Version Updates**: Remember to update `version.gradle.kts` for PRs.
