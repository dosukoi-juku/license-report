# license-report

## Overview

license-report is a Gradle plugin designed to extract dependency information and fetch license details for each
dependency in your Android project.

## Features / 機能

- **Dependency Extraction:** Automatically parses your project's dependency tree.
- **License Retrieval:** Fetches license information from various sources.
- **Report Generation:** Produces detailed reports in formats such as HTML or JSON.
- **Easy Integration:** Simple setup process for Android projects.

## Installation / インストール

Add license-report to your project's build configuration.

```kotlin:build.gradle.kts(project)
plugins {
    id "io.github.dosukoi-juku.license-report" version "1.0.0" apply false
}
```

```kotlin:build.gradle.kts(app)
plugins {
    id "io.github.dosukoi-juku.license-report"
}
```

## Usage

The license report is automatically generated when running the build command, as the license-report task is set as a
dependency of the build task.
You can also run the license-report task directly to generate the report without building the entire project.

```bash
./gradlew licenseReport
```

## Contributing

Contributions are welcome! Please submit issues or pull requests for improvements and bug fixes.

## License

This project is licensed under the MIT License.