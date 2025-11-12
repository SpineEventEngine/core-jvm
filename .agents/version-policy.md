# Version policy

## We use semver
The version of the project is kept in the `version.gradle.kts` file in the root of the project.

The version numbers in these files follow the conventions of
[Semantic Versioning 2.0.0](https://semver.org/).

## Quick checklist for versioning
1. Increment the patch version in `version.gradle.kts`.
   Retain zero-padding if applicable:
    - Example: `"2.0.0-SNAPSHOT.009"` → `"2.0.0-SNAPSHOT.010"`
2. Commit the version bump separately with this comment:
   ```text
   Bump version → `$newVersion`
   ``` 
3. Rebuild using `./gradlew clean build`.
4. Update `pom.xml`, `dependencies.md` and commit changes with: `Update dependency reports`

Remember: PRs without version bumps will fail CI (conflict resolution detailed above).

## Resolving conflicts in `version.gradle.kts`
A branch conflict over the version number should be resolved as described below.
 * If a merged branch has a number which is less than that of the current branch, the version of
   the current branch stays.
 * If the merged branch has the number which is greater or equal to that of the current branch,
   the number should be increased by one.

## When to bump the version?
 - When a new branch is created.
