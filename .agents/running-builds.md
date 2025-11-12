# Running builds

1. When modifying code, run:
   ```bash
   ./gradlew build
   ```

2. If Protobuf (`.proto`) files are modified run:
   ```bash
   ./gradlew clean build
   ```

3. Documentation-only changes in Kotlin or Java sources run:
   ```bash
   ./gradlew dokka
   ```
   
4. Documentation-only changes do not require running tests!
