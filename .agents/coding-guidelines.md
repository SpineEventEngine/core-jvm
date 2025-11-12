# 🧾 Coding guidelines

## Core principles

- Adhere to [Spine Event Engine Documentation][spine-docs] for coding style.
- Generate code that compiles cleanly and passes static analysis.
- Respect existing architecture, naming conventions, and project structure.
- Write clear, incremental commits with descriptive messages.
- Include automated tests for any code change that alters functionality.

## Kotlin best practices

### ✅ Prefer
- **Kotlin idioms** over Java-style approaches:
  - Extension functions
  - `when` expressions 
  - Smart casts
  - Data classes and sealed classes
  - Immutable data structures
- **Simple nouns** over composite nouns (`user` > `userAccount`)  
- **Generic parameters** over explicit variable types (`val list = mutableList<Dependency>()`)  
- **Java interop annotations** only when needed (`@file:JvmName`, `@JvmStatic`)
- **Kotlin DSL** for Gradle files

### ❌ Avoid
- Mutable data structures
- Java-style verbosity (builders with setters)
- Redundant null checks (`?.let` misuse)
- Using `!!` unless clearly justified
- Type names in variable names (`userObject`, `itemList`)
- String duplication (use constants in companion objects)
- Mixing Groovy and Kotlin DSLs in build logic
- Reflection unless specifically requested

## Text formatting
 - ✅ Remove double empty lines in the code.
 - ✅ Remove trailing space characters in the code.

[spine-docs]: https://github.com/SpineEventEngine/documentation/wiki
