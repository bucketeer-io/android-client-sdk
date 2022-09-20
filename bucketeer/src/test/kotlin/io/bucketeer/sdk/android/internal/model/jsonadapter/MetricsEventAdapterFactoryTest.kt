package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.internal.di.DataModule
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
    GetEvaluationLatency(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 1,
        |  "event": {
        |    "labels": {
        |      "key1": "value1",
        |      "key2": "value2"
        |    },
        |    "duration": 5
        |  }
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.GET_EVALUATION_LATENCY,
        event = MetricsEventData.GetEvaluationLatencyMetricsEvent(
          labels = mapOf(
            "key1" to "value1",
            "key2" to "value2",
          ),
          duration = 5,
        ),
      ),
    ),
    GetEvaluationSize(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 2,
        |  "event": {
        |    "labels": {
        |      "key1": "value1",
        |      "key2": "value2"
        |    },
        |    "size_byte": 1234
        |  }
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.GET_EVALUATION_SIZE,
        event = MetricsEventData.GetEvaluationSizeMetricsEvent(
          labels = mapOf(
            "key1" to "value1",
            "key2" to "value2",
          ),
          size_byte = 1234,
        ),
      ),
    ),
    TimeoutErrorCount(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 3,
        |  "event": {
        |    "tag": "tag_value"
        |  }
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.TIMEOUT_ERROR_COUNT,
        event = MetricsEventData.TimeoutErrorCountMetricsEvent(
          tag = "tag_value",
        ),
      ),
    ),
    InternalErrorCount(
      json = """
        |{
        |  "timestamp": 1660210923777,
        |  "type": 4,
        |  "event": {
        |    "tag": "tag_value"
        |  }
        |}
      """.trimMargin(),
      event = EventData.MetricsEvent(
        timestamp = 1660210923777,
        type = MetricsEventType.INTERNAL_ERROR_COUNT,
        event = MetricsEventData.InternalErrorCountMetricsEvent(
          tag = "tag_value",
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
