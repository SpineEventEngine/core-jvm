# 📄 Documentation tasks

1. Ensure all public and internal APIs have KDoc examples.
2. Add in-line code blocks for clarity in tests.
3. Convert inline API comments in Java to KDoc in Kotlin:
   ```java
   // Literal string to be inlined whenever a placeholder references a non-existent argument.
   private final String missingArgumentMessage = "[MISSING ARGUMENT]";
   ```
   transforms to:
   ```kotlin
   /**
    * Literal string to be inlined whenever a placeholder references a non-existent argument.
    */
    private val missingArgumentMessage = "[MISSING ARGUMENT]"
   ```

4. Javadoc -> KDoc conversion tasks:
   - Remove `<p>` tags in the line with text: `"<p>This"` -> `"This"`.
   - Replace `<p>` with empty line if the tag is the only text in the line.
