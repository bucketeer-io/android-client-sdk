package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.google.common.truth.Truth
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.MetricsEventData
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceID
import io.bucketeer.sdk.android.internal.model.User
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class EventAdapterFactoryTest {

  // we don't need test case for GOAL_BATCH,
  // as it doesn't used in Client SDK.
  @Suppress("unused")
  enum class TestCase(val json: String, val event: Event) {
    Goal(
      json = """
        |{
        |  "id": "event_id",
        |  "type": 1,
        |  "event": {
        |    "timestamp": 1660210923777,
        |    "goalId": "goal_id_value",
        |    "userId": "user_id_value",
        |    "value": 1.04,
        |    "user": {
        |      "id": "user_id_value",
        |      "data": {
        |        "gender": "male",
        |        "age": "40"
        |      }
        |    },
        |    "tag": "tag_value",
        |    "sourceId": 1,
        |    "sdkVersion": "2.0.1",
        |    "metadata": {
        |      "app_version": "1.2.3",
        |      "os_version": "os_version_value",
        |      "device_model": "device_model_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.GoalEvent"
        |  }
        |}
      """.trimMargin(),
      event = Event(
        id = "event_id",
        type = EventType.GOAL,
        event = EventData.GoalEvent(
          timestamp = 1660210923777,
          goalId = "goal_id_value",
          userId = "user_id_value",
          value = 1.04,
          user = User(
            id = "user_id_value",
            data = mapOf(
              "gender" to "male",
              "age" to "40",
            ),
          ),
          tag = "tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "os_version_value",
            "device_model" to "device_model_value",
          ),
        ),
      ),
    ),

    @Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
    Goal_NoSdkVersion_NoMetadata(
      json = """
        |{
        |  "id": "event_id",
        |  "type": 1,
        |  "event": {
        |    "timestamp": 1660210923777,
        |    "goalId": "goal_id_value",
        |    "userId": "user_id_value",
        |    "value": 1.04,
        |    "user": {
        |      "id": "user_id_value",
        |      "data": {
        |        "gender": "male",
        |        "age": "40"
        |      }
        |    },
        |    "tag": "tag_value",
        |    "sourceId": 1,
        |    "@type": "type.googleapis.com/bucketeer.event.client.GoalEvent"
        |  }
        |}
      """.trimMargin(),
      event = Event(
        id = "event_id",
        type = EventType.GOAL,
        event = EventData.GoalEvent(
          timestamp = 1660210923777,
          goalId = "goal_id_value",
          userId = "user_id_value",
          value = 1.04,
          user = User(
            id = "user_id_value",
            data = mapOf(
              "gender" to "male",
              "age" to "40",
            ),
          ),
          tag = "tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = null,
          metadata = null,
        ),
      ),
    ),
    Evaluation(
      json = """
        |{
        |  "id": "event_id",
        |  "type": 3,
        |  "event": {
        |    "timestamp": 1660210923777,
        |    "featureId": "feature_id_value",
        |    "featureVersion": 2,
        |    "userId": "user_id_value",
        |    "variationId": "variation_id_value",
        |    "user": {
        |      "id": "user_id_value",
        |      "data": {
        |        "gender": "male",
        |        "age": "40"
        |      }
        |    },
        |    "reason": {
        |      "type": "CLIENT",
        |      "ruleId": "rule_id_value"
        |    },
        |    "tag": "tag_value",
        |    "sourceId": 1,
        |    "sdkVersion": "2.0.1",
        |    "metadata": {
        |      "app_version": "1.2.3",
        |      "os_version": "os_version_value",
        |      "device_model": "device_model_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.EvaluationEvent"
        |  }
        |}
      """.trimMargin(),
      event = Event(
        id = "event_id",
        type = EventType.EVALUATION,
        event = EventData.EvaluationEvent(
          timestamp = 1660210923777,
          featureId = "feature_id_value",
          featureVersion = 2,
          userId = "user_id_value",
          variationId = "variation_id_value",
          user = User(
            id = "user_id_value",
            data = mapOf(
              "gender" to "male",
              "age" to "40",
            ),
          ),
          reason = Reason(
            type = ReasonType.CLIENT,
            ruleId = "rule_id_value",
          ),
          tag = "tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = "2.0.1",
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "os_version_value",
            "device_model" to "device_model_value",
          ),
        ),
      ),
    ),

    @Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
    Evaluation_NoSdkVersion_NoMetadata(
      json = """
        |{
        |  "id": "event_id",
        |  "type": 3,
        |  "event": {
        |    "timestamp": 1660210923777,
        |    "featureId": "feature_id_value",
        |    "featureVersion": 2,
        |    "userId": "user_id_value",
        |    "variationId": "variation_id_value",
        |    "user": {
        |      "id": "user_id_value",
        |      "data": {
        |        "gender": "male",
        |        "age": "40"
        |      }
        |    },
        |    "reason": {
        |      "type": "CLIENT",
        |      "ruleId": "rule_id_value"
        |    },
        |    "tag": "tag_value",
        |    "sourceId": 1,
        |    "@type": "type.googleapis.com/bucketeer.event.client.EvaluationEvent"
        |  }
        |}
      """.trimMargin(),
      event = Event(
        id = "event_id",
        type = EventType.EVALUATION,
        event = EventData.EvaluationEvent(
          timestamp = 1660210923777,
          featureId = "feature_id_value",
          featureVersion = 2,
          userId = "user_id_value",
          variationId = "variation_id_value",
          user = User(
            id = "user_id_value",
            data = mapOf(
              "gender" to "male",
              "age" to "40",
            ),
          ),
          reason = Reason(
            // Kenji: we don't need ReasonTypeAdapterTest anymore
            // ReasonTypeAdapter is autogenerate by Moshi
            // We could validate ReasonType toJson and fromJson in this test file.
            type = ReasonType.CLIENT,
            ruleId = "rule_id_value",
          ),
          tag = "tag_value",
          sourceId = SourceID.ANDROID,
          sdkVersion = null,
        ),
      ),
    ),
    Metrics(
      json = """
        |{
        |  "id": "event_id",
        |  "type": 4,
        |  "event": {
        |    "timestamp": 1660210923777,
        |    "type": 1,
        |    "event": {
        |      "labels": {
        |        "key1": "value1",
        |        "key2": "value2"
        |      },
        |      "duration": "5s",
        |      "@type": "type.googleapis.com/bucketeer.event.client.GetEvaluationLatencyMetricsEvent"
        |    },
        |    "sdkVersion": "2.0.1",
        |    "metadata": {
        |      "app_version": "1.2.3",
        |      "os_version": "os_version_value",
        |      "device_model": "device_model_value"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |  }
        |}
      """.trimMargin(),
      event = Event(
        id = "event_id",
        type = EventType.METRICS,
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
          sdkVersion = "2.0.1",
          metadata = mapOf(
            "app_version" to "1.2.3",
            "os_version" to "os_version_value",
            "device_model" to "device_model_value",
          ),
        ),
      ),
    ),

    @Suppress("EnumEntryName", "ktlint:enum-entry-name-case")
    Metrics_NoSdkVersion_NoMetadata(
      json = """
        |{
        |  "id": "event_id",
        |  "type": 4,
        |  "event": {
        |    "timestamp": 1660210923777,
        |    "type": 1,
        |    "event": {
        |      "labels": {
        |        "key1": "value1",
        |        "key2": "value2"
        |      },
        |      "duration": "5s",
        |      "@type": "type.googleapis.com/bucketeer.event.client.GetEvaluationLatencyMetricsEvent"
        |    },
        |    "@type": "type.googleapis.com/bucketeer.event.client.MetricsEvent"
        |  }
        |}
      """.trimMargin(),
      event = Event(
        id = "event_id",
        type = EventType.METRICS,
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
          sdkVersion = null,
          metadata = null,
        ),
      ),
    ),
  }

  lateinit var adapter: JsonAdapter<Event>

  @Before
  fun setup() {
    adapter = DataModule.createMoshi().adapter(Event::class.java)
  }

  @Test
  fun fromJson(@TestParameter case: TestCase) {
    val result = adapter.fromJson(case.json)

    Truth.assertThat(result).isEqualTo(case.event)
  }

  @Test
  fun toJson(@TestParameter case: TestCase) {
    val result = adapter.indent("  ").toJson(case.event)

    Truth.assertThat(result).isEqualTo(case.json)
  }
}
