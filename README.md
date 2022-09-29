[![Build & Integration tests](https://github.com/bucketeer-io/android-client-sdk/actions/workflows/build.yml/badge.svg)](https://github.com/bucketeer-io/android-client-sdk/actions/workflows/build.yml)

# Bucketeer Client-side SDK for Android

## Setup

Install prerequisite tools.

- Android Studio Chipmunk or later
- Java 11

Then, you need to create `local.properties`.

```
# build
sdk.dir=<SDK_DIR_PATH> # e.g. /Users/<USER_NAME>/Library/Android/sdk

# test
api_key=<API_KEY>
api_url=<API_URL> # e.g. api-media.bucketeer.jp

# sample
sample.use_released_sdk=false
sample.sdk_version=<SDK_VERSION>
```

## Development

### Development with Android Studio

Open Android Studio and import `bucketeer-android-sdk`.

### Development with command line

#### project :bucketeer (SDK)

Displays the tasks runnable from project ':bucketeer'.

```
./gradlew :bucketeer:tasks
```

Runs lint on the Debug build.

```
./gradlew :bucketeer:lintDebug
```

Run unit tests for the debug build.

```
./gradlew :bucketeer:testDebugUnitTest
```

Deletes the build directory and assembles all Release builds. (Create `./bucketeer/build/outputs/aar/bucketeer-release.aar`)

```
./gradlew clean :bucketeer:assembleRelease
```

Installs and runs the e2e tests for debug on connected devices. Open Android Emulator, then run the command below.

```
./gradlew :bucketeer:connectedCheck
```

#### project :sample (Sample)

Displays the tasks runnable from project ':sample'.

```
./gradlew :sample:tasks
```

Deletes the build directory and assembles all Release builds. (Create `./sample/build/outputs/apk/sample-release.apk`)

```
./gradlew clean :sample:assembleRelease
```

### Tips

#### Use published SDK in Sample

If you want to use published SDK instead of local one, change `local.properties` like below,

(check SDK versions [here](https://repo1.maven.org/maven2/jp/bucketeer/sdk-android/))

```
# sample
sample.use_released_sdk=true
sample.sdk_version=X.Y.Z
```

then build and start the sample app.

## Contributing

[CONTRIBUTING.md](./CONTRIBUTING.md)

## SDK User Docs

- [Tutorial](https://docs.bucketeer.io/sdk/client-side/android)

