package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.ApiId
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.SourceID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class MetricsEventAdapterFactoryTest {
  @Suppress("unused")
  enum class TestCase(val json: String, val event: EventData.MetricsEvent) {
    LatencyMetric(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 1,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "key1": "value1",
        |      "key2": "value2"
        |    },
        |    "latencySecond": 5.0,
        |    "@type": "type.googleapis.com/bucketeer.event.client.LatencyMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.RESPONSE_LATENCY,
          event =
            MetricsEventData.LatencyMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "key1" to "value1",
                  "key2" to "value2",
                ),
              latencySecond = 5.0,
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    SizeMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 2,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "key1": "value1",
        |      "key2": "value2"
        |    },
        |    "sizeByte": 1234,
        |    "@type": "type.googleapis.com/bucketeer.event.client.SizeMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.RESPONSE_SIZE,
          event =
            MetricsEventData.SizeMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "key1" to "value1",
                  "key2" to "value2",
                ),
              sizeByte = 1234,
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    TimeoutErrorMetric(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 3,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value",
        |      "timeout": "5.0"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.TimeoutErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.TIMEOUT_ERROR,
          event =
            MetricsEventData.TimeoutErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                  "timeout" to "5.0",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    NetworkErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 4,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.NetworkErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.NETWORK_ERROR,
          event =
            MetricsEventData.NetworkErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    BadRequestErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 6,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.BadRequestErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.BAD_REQUEST_ERROR,
          event =
            MetricsEventData.BadRequestErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    UnauthorizedErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 7,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.UnauthorizedErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.UNAUTHORIZED_ERROR,
          event =
            MetricsEventData.UnauthorizedErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    ForbiddenErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 8,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.ForbiddenErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.FORBIDDEN_ERROR,
          event =
            MetricsEventData.ForbiddenErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    NotFoundErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 9,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.NotFoundErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.NOT_FOUND_ERROR,
          event =
            MetricsEventData.NotFoundErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    ClientClosedRequestErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 10,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.ClientClosedRequestErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.CLIENT_CLOSED_REQUEST_ERROR,
          event =
            MetricsEventData.ClientClosedRequestErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    ServiceUnavailableErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 11,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.ServiceUnavailableErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.SERVICE_UNAVAILABLE_ERROR,
          event =
            MetricsEventData.ServiceUnavailableErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    InternalSdkErrorMetric(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 5,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.InternalSdkErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.INTERNAL_SDK_ERROR,
          event =
            MetricsEventData.InternalSdkErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    InternalServerErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 12,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.InternalServerErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.INTERNAL_SERVER_ERROR,
          event =
            MetricsEventData.InternalServerErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    UnknownErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 0,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value",
        |      "response_code": "418",
        |      "error_message": "code 418"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.UnknownErrorMetricsEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.UNKNOWN,
          event =
            MetricsEventData.UnknownErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                  "response_code" to "418",
                  "error_message" to "code 418",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    RedirectRequestErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 13,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value",
        |      "response_code": "302"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.RedirectionRequestExceptionEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.REDIRECT_REQUEST,
          event =
            MetricsEventData.RedirectionRequestErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                  "response_code" to "302",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
    PayloadTooLargeErrorMetricsEvent(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 14,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.PayloadTooLargeExceptionEvent"
        |  },
        |  "sourceId": 1,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.PAYLOAD_TOO_LARGE,
          event =
            MetricsEventData.PayloadTooLargeErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),

    @Suppress("EnumEntryName", "ktlint:standard:enum-entry-name-case")
    Metrics_NoSdkVersion_NoMetadata(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 14,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.PayloadTooLargeExceptionEvent"
        |  },
        |  "sourceId": 1,
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.PAYLOAD_TOO_LARGE,
          event =
            MetricsEventData.PayloadTooLargeErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.ANDROID,
          sdkVersion = null,
          metadata = null,
        ),
    ),

    // Because the default `source_id` is SourceId.Android.
    // We need one more test to make sure the adapter logic for parser `source_id` is correct.
    @Suppress("EnumEntryName", "ktlint:standard:enum-entry-name-case")
    Metrics_With_SourceId_Is_Not_Android(
      json =
        """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 14,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.PayloadTooLargeExceptionEvent"
        |  },
        |  "sourceId": 4,
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
        """.trimMargin(),
      event =
        EventData.MetricsEvent(
          timestamp = 1660210923777,
          type = MetricsEventType.PAYLOAD_TOO_LARGE,
          event =
            MetricsEventData.PayloadTooLargeErrorMetricsEvent(
              ApiId.GET_EVALUATIONS,
              labels =
                mapOf(
                  "tag" to "tag_value",
                ),
            ),
          sourceId = SourceID.GOAL_BATCH,
          sdkVersion = "2.0.1",
          metadata =
            mapOf(
              "app_version" to "1.2.3",
              "os_version" to "os_version_value",
              "device_model" to "device_model_value",
            ),
        ),
    ),
  }

  lateinit var adapter: JsonAdapter<EventData.MetricsEvent>

  @Before
  fun setup() {
    adapter = DataModule.createMoshi().adapter(EventData.MetricsEvent::class.java)
  }

  @Test
  fun fromJson(
    @TestParameter case: TestCase,
  ) {
    val result = adapter.fromJson(case.json)

    assertThat(result).isEqualTo(case.event)
  }

  @Test
  fun toJson(
    @TestParameter case: TestCase,
  ) {
    val result = adapter.indent("  ").toJson(case.event)

    assertThat(result).isEqualTo(case.json)
  }

  @Test
  fun testMetricsIsMissingSourceId() {
    // Versions of the SDK 2.0.5 and below are missing a 'source_id' in a 'EventData.MetricsEvent'.
    // This test simulates the scenario where the app developer updates the SDK from version [2.0.5 or below] to [version 2.0.6 or higher].
    val json =
      """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 14,
        |  "event": {
        |    "apiId": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.PayloadTooLargeExceptionEvent"
        |  },
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
      """.trimMargin()

    val event =
      EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.PAYLOAD_TOO_LARGE,
        event =
          MetricsEventData.PayloadTooLargeErrorMetricsEvent(
            ApiId.GET_EVALUATIONS,
            labels =
              mapOf(
                "tag" to "tag_value",
              ),
          ),
        sourceId = SourceID.ANDROID,
        sdkVersion = "2.0.1",
        metadata =
          mapOf(
            "app_version" to "1.2.3",
            "os_version" to "os_version_value",
            "device_model" to "device_model_value",
          ),
      )

    val result = adapter.fromJson(json)

    assertThat(result).isEqualTo(event)
  }
}
