[![Build Status](https://app.bitrise.io/app/16337d5c0c8d6081/status.svg?token=l2E3TXU8-dnAmep6MJ8cIA&branch=master)](https://app.bitrise.io/app/16337d5c0c8d6081)

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

# publish (if needed)
# sdk.version=<SDK_VERSION>
# signing.keyId=<SIGNING_KEY_ID>
# signing.password=<SIGNING_PASSWORD>
# signing.secretKeyRingFile=<SIGNING_SECRET_KEY_RING_FILE>
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

Publishes SDK to Sonatype repository and releases to Maven Central.
(Usually you don't need to publish manually because CI/CD workflow publishes automatically.)

```
./gradlew :bucketeer:publish --no-daemon --no-parallel -PmavenCentralUsername=$SONATYPE_USERNAME -PmavenCentralPassword=$SONATYPE_PASSWORD
./gradlew closeAndReleaseRepository
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

- [Tutorial](https://bucketeer.io/docs/#/./client-side-sdk-tutorial-android)
- [Integration](https://bucketeer.io/docs/#/./client-side-sdk-reference-guides-android)

## Samples

[Bucketeer Samples](https://github.com/ca-dp/bucketeer-samples)
