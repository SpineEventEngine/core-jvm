# 🪄 Converting Java code to Kotlin

* Java code API comments are Javadoc format.
* Kotlin code API comments are in KDoc format. 
 
## Javadoc to KDoc conversion

* The wording of original Javadoc comments must be preserved.
                                                                      
## Treating nullability

* Use nullable Kotlin type only if the type in Java is annotated as `@Nullable`.

## Efficient Conversion Workflow

* First, analyze the entire Java file structure before beginning conversion to understand dependencies and class relationships.
* Convert Java code to Kotlin systematically: imports first, followed by class definitions, methods, and finally expressions.
* Preserve all existing functionality and behavior during conversion.
* Maintain original code structure and organization to ensure readability.

## Common Java to Kotlin Patterns

* Convert Java getters/setters to Kotlin properties with appropriate visibility modifiers.
* Transform Java static methods to companion object functions or top-level functions as appropriate.
* Replace Java anonymous classes with Kotlin lambda expressions when possible.
* Convert Java interfaces with default methods to Kotlin interfaces with implementations.
* Transform Java builders to Kotlin DSL patterns when appropriate.

## Error Prevention

* Pay special attention to Java's checked exceptions versus Kotlin's unchecked exceptions.
* Be cautious with Java wildcards (`? extends`, `? super`) conversion to Kotlin's `out` and `in` type parameters.
* Ensure proper handling of Java static initialization blocks in Kotlin companion objects.
* Verify that Java overloaded methods convert correctly with appropriate default parameter values in Kotlin.
* Remember that Kotlin has smart casts which can eliminate explicit type casting needed in Java.

## Documentation Conversion

* Convert `@param` to `@param` with the same description.
* Convert `@return` to `@return` with the same description.
* Convert `@throws` to `@throws` with the same description.
* Convert `{@link}` to `[name][fully.qualified.Name]` format.
* Convert `{@code}` to inline code with backticks (`).
