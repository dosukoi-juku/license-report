# License Report

## Development

To develop this plugin, follow these steps:

1. Comment out the following lines:
   - In `build.gradle.kts`:
     ```kotlin
     classpath("io.github.dosukoi-juku:license-report-gradle-plugin:0.0.1-SNAPSHOT")
     ```
   - In `sample/build.gradle.kts`:
     ```kotlin
     id("io.github.dosukoi-juku.license-report")
     ```
2. Run the publish task:
   ```
   ./gradlew :plugin:publish
   ```
3. After publishing, uncomment the lines you commented out in step 1.
