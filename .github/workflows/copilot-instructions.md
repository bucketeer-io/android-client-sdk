# Bucketeer Android SDK - AI Coding Assistant Instructions

## Project Overview
This is the Bucketeer feature flag service Android client SDK. It provides a clean, type-safe API for evaluating feature flags with local caching, event tracking, and background synchronization.

## Architecture & Code Organization

### Public API (`io.bucketeer.sdk.android`)
- **BKTClient**: Main singleton interface with evaluation methods (`booleanVariation`, `stringVariation`, etc.)
- **BKTConfig**: Configuration via builder pattern with validation
- **BKTUser**: User context with ID and attributes
- **BKTEvaluationDetails**: Rich evaluation results with metadata

### Internal Architecture (`internal/`)
- **di/**: Manual dependency injection (Component, DataModule, InteractorModule)
- **evaluation/**: Feature flag evaluation logic and caching
- **event/**: Event tracking and batching system  
- **database/**: SQLite storage with migrations
- **remote/**: HTTP client and API communication
- **scheduler/**: Background polling and task management
- **model/**: Data transfer objects and domain models

### Key Design Patterns
- **Singleton**: BKTClient uses singleton pattern with `getInstance()` and `initialize()`
- **Builder**: BKTConfig uses fluent builder with validation
- **Repository**: Data access abstracted through storage interfaces
- **Interactor**: Business logic separated from data access
- **Future-based Async**: Operations return `Future<BKTException?>` for async handling

## Build System & Development

### Gradle Setup
- **Version Catalogs**: Dependencies managed in `gradle/libs.versions.toml`
- **Multi-module**: `:bucketeer` (SDK), `:sample` (demo), `:mocks` (test utils)
- **AGP 8.x**: Modern Android Gradle Plugin with build optimization
- **Kotlin 2.0.x**: Latest Kotlin with JVM target 1.8

### Essential Commands
```bash
# Build SDK library
./gradlew :bucketeer:assembleRelease

# Run unit tests
./gradlew :bucketeer:testDebugUnitTest

# Run linting
./gradlew :bucketeer:lintDebug

# Build sample app
./gradlew :sample:assembleDebug

# Run instrumentation tests
./gradlew :bucketeer:connectedCheck
```

### Configuration (`local.properties`)
```properties
# Required for debug builds and tests
api_key=your_api_key
api_endpoint=https://api.example.com

# Sample app configuration
sample.use_released_sdk=false  # true to use published SDK
sample.sdk_version=2.0.1
```

## Development Workflow

### Testing Strategy
- **Unit Tests**: JUnit + Google Truth in `src/test/`
- **Instrumentation Tests**: Android test orchestrator in `src/androidTest/`
- **Mock Setup**: Robolectric for Android framework mocking
- **Test Data**: MockWebServer for API mocking

### Code Quality
- **Kotlin Linting**: Kotlinter with custom exclusions for generated code
- **ProGuard**: Consumer rules in `proguard-rules.pro`
- **Static Analysis**: Lint checks in CI pipeline

### Database & Storage
- **SQLite**: Local evaluation caching with schema migrations
- **SharedPreferences**: Configuration and metadata storage
- **Migration Pattern**: Versioned migrations in `database/migration/`

## Common Patterns & Conventions

### Error Handling
```kotlin
// Async operations return Future with nullable exception
val future = client.fetchEvaluations()
future.get()?.let { exception ->
    // Handle BKTException subtypes
}
```

### Logging
```kotlin
// Internal logging utilities
internal fun logd(message: () -> String)
internal fun logw(message: () -> String)  
internal fun loge(message: () -> String, throwable: Throwable? = null)
```

### Data Serialization
- **Moshi**: JSON serialization with Kotlin codegen
- **Adapters**: Custom adapters for complex types
- **Internal Models**: Separate from public API types

### Threading
- **Main Thread**: UI callbacks and listeners
- **Background**: Network requests and database operations
- **Handler**: Main thread posting for updates

## Integration Points

### External Dependencies
- **OkHttp**: HTTP client with interceptors
- **Moshi**: JSON processing
- **SQLite**: Local database via AndroidX
- **Lifecycle**: Process lifecycle awareness

### Android Integration
- **Context**: Application context required for initialization
- **Lifecycle**: Automatic pause/resume based on app state
- **Permissions**: Network access for API calls

## Publishing & Release

### Maven Publishing
- **Plugin**: `com.vanniktech.maven.publish`
- **Repository**: Sonatype OSSRH
- **Artifacts**: AAR library with sources and javadoc

### CI/CD Pipeline
- **JDK 17**: Build and test environment
- **Parallel Execution**: Gradle parallel builds and test forks
- **Caching**: Gradle and dependency caching
- **Release**: Automated via release-please

## Key Files & Directories

### Core Implementation
- `BKTClient.kt` - Public API entry point
- `BKTClientImpl.kt` - Main implementation
- `internal/di/Component.kt` - Dependency injection setup
- `internal/evaluation/EvaluationInteractor.kt` - Flag evaluation logic
- `internal/database/DBOpenHelper.kt` - Database initialization

### Configuration
- `gradle/libs.versions.toml` - Dependency versions
- `gradle.properties` - Build properties
- `bucketeer/build.gradle` - SDK module configuration

### Testing
- `bucketeer/src/test/` - Unit tests
- `bucketeer/src/androidTest/` - Instrumentation tests
- `sample/` - Integration testing via demo app

### CI/CD
- `.github/workflows/build.yml` - Main build pipeline
- `.github/workflows/e2e.yml` - End-to-end testing</content>
