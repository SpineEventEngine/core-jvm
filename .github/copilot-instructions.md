# GitHub Copilot Instructions for Spine CoreJvm

## Project Overview

- **Type**: Library/Framework
- **Languages**: Kotlin (primary), Java (secondary)
- **Tech Stack**: Gradle with Kotlin DSL, JDK 17+
- **Architecture**: CQRS (Command Query Responsibility Segregation) and Event Sourcing
- **Main Purpose**: Spine Event Engine core library for building Event Sourcing and CQRS applications

**Key Dependencies**:
- Protobuf for message definitions
- JUnit 5 for testing
- Kotest for assertions
- Static analysis: detekt, ErrorProne, Checkstyle, PMD
- Documentation: Dokka
- Tools: KSP, KotlinPoet

## Detailed Documentation

For comprehensive guidance, please refer to the [`.agents` directory](../.agents/_TOC.md) which contains:
- [Quick Reference Card](../.agents/quick-reference-card.md)
- [Project Overview](../.agents/project-overview.md)
- [Coding Guidelines](../.agents/coding-guidelines.md)
- [Testing Guidelines](../.agents/testing.md)
- [Running Builds](../.agents/running-builds.md)
- [Version Policy](../.agents/version-policy.md)
- [Safety Rules](../.agents/safety-rules.md)

## Coding Standards & Conventions

### Language Preferences
- **Primary**: Kotlin with idiomatic patterns
- **Secondary**: Java (for legacy compatibility)

### Kotlin Best Practices
**✅ Prefer**:
- Extension functions over utility classes
- `when` expressions over multiple `if-else`
- Smart casts and type inference
- Data classes and sealed classes
- Immutable data structures
- Simple nouns over composite names (`user` not `userAccount`)
- Generic parameters over explicit types

**❌ Avoid**:
- Mutable data structures
- Java-style builders with setters
- Redundant null checks and `?.let` misuse
- Using `!!` (null assertion) without clear justification
- Type names in variable names (`userObject`, `itemList`)
- Reflection unless specifically requested
- Mixing Groovy and Kotlin DSLs in build files

### Code Quality
- Follow [Spine Event Engine Documentation](https://github.com/SpineEventEngine/documentation/wiki) for style
- Generate code that compiles cleanly and passes static analysis
- Remove double empty lines and trailing spaces
- Write clear, descriptive commit messages
- Respect existing architecture and naming conventions

## Testing & Quality

### Testing Framework
- **Test Framework**: JUnit 5
- **Assertions**: Prefer [Kotest assertions](https://kotest.io/docs/assertions/assertions.html) over JUnit or Google Truth
- **Test Strategy**: Use stubs, not mocks

### Testing Requirements
- Include automated tests for any code change that alters functionality
- Generate unit tests that handle edge cases and various scenarios
- Documentation-only changes do not require tests

## Build & Workflow

### Building the Project

1. **Code changes**:
   ```bash
   ./gradlew build
   ```

2. **Protobuf (`.proto`) changes**:
   ```bash
   ./gradlew clean build
   ```

3. **Documentation-only changes** (Kotlin/Java sources):
   ```bash
   ./gradlew dokka
   ```

### Version Management

This project follows [Semantic Versioning 2.0.0](https://semver.org/).

**Version Bump Checklist**:
1. Increment patch version in `version.gradle.kts` (retain zero-padding)
2. Commit version bump separately: `Bump version → $newVersion`
3. Rebuild: `./gradlew clean build`
4. Update `pom.xml` and `dependencies.md`, commit as: `Update dependency reports`

**Important**: Version bumps are required when creating a new branch. PRs without version bumps will fail CI.

### Commit Guidelines
- Write incremental, focused commits
- Use clear, descriptive commit messages
- Commit version bumps separately from code changes

## Security & Safety Rules

**✅ Must Do**:
- Ensure all code compiles and passes static analysis
- Test thoroughly before committing

**❌ Never Do**:
- Auto-update external dependencies without review
- Use reflection or unsafe code without explicit approval
- Add analytics or telemetry code
- Make blocking calls inside coroutines

## Project Structure

- `buildSrc/` - Build configuration and dependency management
- `client/` - Client API
- `server/` - Server API
- `core/` - Core types and functionality
- `*-testlib/` - Testing utilities
- `config/` - Shared configuration (Git submodule)

## Common Tasks

- **Adding a new dependency**: Update relevant files in `buildSrc` directory
- **Creating a new module**: Follow existing module structure patterns
- **Documentation**: Use KDoc style for public and internal APIs
- **Refactoring**: Consult [Refactoring Guidelines](../.agents/refactoring-guidelines.md)

## Important Annotations

- `@Internal`: Not part of public API - do not use from outside the framework
- `@Experimental`: Use at your own risk - API may change without notice
- `@SPI`: For framework extension and custom storage implementations

## Additional Resources

- [Spine Event Engine Website](https://spine.io/)
- [Quick Start Guide](https://spine.io/docs/quick-start)
- [Configuration Repository](https://github.com/SpineEventEngine/config/)
- [Spine Examples](https://github.com/spine-examples)

---

*For more detailed information on specific topics, please refer to the comprehensive documentation in the [`.agents` directory](../.agents/_TOC.md).*
