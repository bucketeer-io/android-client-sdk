package io.bucketeer.sdk.android.e2e

import androidx.test.platform.app.InstrumentationRegistry

/**
 * E2E settings, passed to the test runner when the tests start (see `bucketeer/build.gradle`).
 * They are never compiled into a class: CI saves compiled classes in the Gradle build cache,
 * which pull requests from forks can read.
 */
object E2EArguments {
  val apiKey: String get() = argument("apiKey")
  val apiEndpoint: String get() = argument("apiEndpoint")

  private fun argument(name: String): String = InstrumentationRegistry.getArguments().getString(name).orEmpty()
}
