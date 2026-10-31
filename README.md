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
api_endpoint=<API_ENDPOINT> # e.g. api.example.jp

# sample
sample.use_released_sdk=false
sample.sdk_version=<SDK_VERSION>
```

## Development

### Development with Android Studio

Open Android Studio and import `android-client-sdk`.

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

#### Try a real-time flag update (SSE)

The sample app uses the feature tag `android` and the user `bucketeer-android-user-id` by default. You can change both on the screen.<br />
Before you run it, create a feature flag with the feature tag `android` in your Bucketeer console.

- Set `api_key` and `api_endpoint` in `local.properties` (see [Setup](#setup)).
- Install and run the sample app: `./gradlew :sample:installDebug`.
- Turn on the **Streaming** switch at the bottom of the screen (off by default, the same as the SDK). Flipping it destroys the client and starts it again in the new mode. The setting is kept after the app restarts.
- Enter your flag in **Feature Flag Id**, choose its **Variant Type**, and press **Get variation**.
- In the Bucketeer console, change the value of the flag.
- Watch the **Current value** line. It changes and a `User Evaluations has been updated` toast appears, pushed by SSE in about a second. With the switch off, the same change takes up to 20 seconds (the polling interval).

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

