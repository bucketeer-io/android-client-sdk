package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.ApiID
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class MetricsEventAdapterFactoryTest {

  @Suppress("unused")
  enum class TestCase(val json: String, val event: EventData.MetricsEvent) {
    LatencyMetric(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 1,
        |  "event": {
        |    "apiID": 2,
        |    "labels": {
        |      "key1": "value1",
        |      "key2": "value2"
        |    },
        |    "latencySecond": 5.0,
        |    "@type": "type.googleapis.com/bucketeer.event.client.LatencyMetricsEvent"
        |  },
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.RESPONSE_LATENCY,
        event = MetricsEventData.LatencyMetricsEvent(
          ApiID.GET_EVALUATIONS,
          labels = mapOf(
            "key1" to "value1",
            "key2" to "value2",
          ),
          latencySecond = 5.0,
        ),
        sdkVersion = "2.0.1",
        metadata = mapOf(
          "app_version" to "1.2.3",
          "os_version" to "os_version_value",
          "device_model" to "device_model_value",
        ),
      ),
    ),
    SizeMetricsEvent(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 2,
        |  "event": {
        |    "apiID": 2,
        |    "labels": {
        |      "key1": "value1",
        |      "key2": "value2"
        |    },
        |    "sizeByte": 1234,
        |    "@type": "type.googleapis.com/bucketeer.event.client.SizeMetricsEvent"
        |  },
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.RESPONSE_SIZE,
        event = MetricsEventData.SizeMetricsEvent(
          ApiID.GET_EVALUATIONS,
          labels = mapOf(
            "key1" to "value1",
            "key2" to "value2",
          ),
          sizeByte = 1234,
        ),
        sdkVersion = "2.0.1",
        metadata = mapOf(
          "app_version" to "1.2.3",
          "os_version" to "os_version_value",
          "device_model" to "device_model_value",
        ),
      ),
    ),
    TimeoutErrorMetric(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 3,
        |  "event": {
        |    "apiID": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.TimeoutErrorMetricsEvent"
        |  },
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.TIMEOUT_ERROR,
        event = MetricsEventData.TimeoutErrorMetricsEvent(
          ApiID.GET_EVALUATIONS,
          labels = mapOf(
            "tag" to "tag_value",
          ),
        ),
        sdkVersion = "2.0.1",
        metadata = mapOf(
          "app_version" to "1.2.3",
          "os_version" to "os_version_value",
          "device_model" to "device_model_value",
        ),
      ),
    ),
    InternalErrorCount(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 5,
        |  "event": {
        |    "apiID": 2,
        |    "labels": {
        |      "tag": "tag_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.InternalSdkErrorMetricsEvent"
        |  },
        |  "sdkVersion": "2.0.1",
        |  "metadata": {
        |    "app_version": "1.2.3",
        |    "os_version": "os_version_value",
        |    "device_model": "device_model_value"
        |  },
        |  "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.INTERNAL_ERROR,
        event = MetricsEventData.InternalSdkErrorMetricsEvent(
          ApiID.GET_EVALUATIONS,
          labels = mapOf(
            "tag" to "tag_value",
          ),
        ),
        sdkVersion = "2.0.1",
        metadata = mapOf(
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
  fun fromJson(@TestParameter case: TestCase) {
    val result = adapter.fromJson(case.json)

    assertThat(result).isEqualTo(case.event)
  }

  @Test
  fun toJson(@TestParameter case: TestCase) {
    val result = adapter.indent("  ").toJson(case.event)

    assertThat(result).isEqualTo(case.json)
  }
}
