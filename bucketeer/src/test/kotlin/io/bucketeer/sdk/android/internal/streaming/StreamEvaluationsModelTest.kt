package io.bucketeer.sdk.android.internal.streaming

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceId
import io.bucketeer.sdk.android.internal.model.User
import io.bucketeer.sdk.android.internal.model.UserEvaluations
import io.bucketeer.sdk.android.internal.model.request.StreamEvaluationsRequest
import io.bucketeer.sdk.android.internal.model.response.GetEvaluationsResponse
import io.bucketeer.sdk.android.internal.model.response.StreamErrorEvent
import org.json.JSONObject
import org.junit.Test

class StreamEvaluationsModelTest {
  private val moshi: Moshi = DataModule.createMoshi()

  // Shaped like a real put: every field present (the backend emits unpopulated fields),
  // an empty id, and a "variation" object the SDK does not map.
  private val realisticPut =
    """
    {
      "evaluations": {
        "id": "",
        "evaluations": [
          {
            "id": "feature-1:3:user-1",
            "featureId": "feature-1",
            "featureVersion": 3,
            "userId": "user-1",
            "variationId": "variation-1",
            "variationName": "on",
            "variationValue": "true",
            "variation": {
              "id": "variation-1",
              "name": "on",
              "value": "true",
              "description": ""
            },
            "reason": {
              "type": "RULE",
              "ruleId": "rule-1"
            }
          }
        ],
        "createdAt": "1700000000",
        "forceUpdate": false,
        "archivedFeatureIds": ["archived-feature"]
      },
      "userEvaluationsId": "user-evaluations-id-1"
    }
    """.trimIndent()

  @Test
  fun `request encodes every field with evaluatedAt as a string`() {
    val request =
      StreamEvaluationsRequest(
        tag = "android",
        user = User(id = "user-1", data = mapOf("age" to "28")),
        sourceId = SourceId.ANDROID,
        sdkVersion = "2.3.1",
        userEvaluationsId = "",
        evaluatedAt = "0",
      )

    val json = JSONObject(moshi.adapter(StreamEvaluationsRequest::class.java).toJson(request))

    assertThat(json.keys().asSequence().toSet())
      .containsExactly("tag", "user", "sourceId", "sdkVersion", "userEvaluationsId", "evaluatedAt")
    assertThat(json.has("userEvaluationCondition")).isFalse()
    assertThat(json.getString("tag")).isEqualTo("android")
    val user = json.getJSONObject("user")
    assertThat(user.getString("id")).isEqualTo("user-1")
    assertThat(user.getJSONObject("data").getString("age")).isEqualTo("28")
    assertThat(json.get("sourceId")).isEqualTo(1)
    assertThat(json.getString("sdkVersion")).isEqualTo("2.3.1")
    assertThat(json.get("userEvaluationsId")).isEqualTo("")
    assertThat(json.get("evaluatedAt")).isEqualTo("0")
  }

  @Test
  fun `realistic put payload decodes as GetEvaluationsResponse`() {
    val response = parseStreamedEvaluations(moshi, realisticPut)

    assertThat(response).isEqualTo(
      GetEvaluationsResponse(
        evaluations =
          UserEvaluations(
            id = "",
            createdAt = "1700000000",
            forceUpdate = false,
            evaluations =
              listOf(
                Evaluation(
                  id = "feature-1:3:user-1",
                  featureId = "feature-1",
                  featureVersion = 3,
                  userId = "user-1",
                  variationId = "variation-1",
                  variationName = "on",
                  variationValue = "true",
                  reason = Reason(type = ReasonType.RULE, ruleId = "rule-1"),
                ),
              ),
            archivedFeatureIds = listOf("archived-feature"),
          ),
        userEvaluationsId = "user-evaluations-id-1",
      ),
    )
  }

  @Test
  fun `malformed payloads return null`() {
    // Positive control: a valid payload decodes.
    assertThat(parseStreamedEvaluations(moshi, realisticPut)).isNotNull()

    val malformed =
      listOf(
        "{}",
        """{"userEvaluationsId":"x"}""",
        """{"userEvaluationsId":"x","evaluations":null}""",
        """{"userEvaluationsId":"x","evaluations":{"id":"","forceUpdate":false,"evaluations":[]}}""",
        """{"userEvaluationsId":"x","evaluations":{"id":"","createdAt":"1","evaluations":[]}}""",
        """{"userEvaluationsId":"x","evaluations":{"createdAt":"1","forceUpdate":false,"evaluations":[]}}""",
        "[]",
        "null",
        "not-json",
        "",
      )
    malformed.forEach { payload ->
      assertWithMessage("payload: %s", payload).that(parseStreamedEvaluations(moshi, payload)).isNull()
    }
  }

  @Test
  fun `numbers in string fields are accepted`() {
    // Moshi reads a JSON number as a string when a string is expected. Pinned so a change is noticed.
    val payload =
      """{"userEvaluationsId":42,"evaluations":{"id":"","createdAt":1700000000,"forceUpdate":false,"evaluations":[]}}"""

    val response = parseStreamedEvaluations(moshi, payload)

    assertThat(response?.evaluations?.createdAt).isEqualTo("1700000000")
    assertThat(response?.userEvaluationsId).isEqualTo("42")
  }

  @Test
  fun `error payload decodes code and message`() {
    val error = parseStreamError(moshi, """{"code":"INTERNAL","message":"evaluation failed"}""")

    assertThat(error).isEqualTo(StreamErrorEvent(code = "INTERNAL", message = "evaluation failed"))
  }

  @Test
  fun `empty or invalid error payload`() {
    assertThat(parseStreamError(moshi, "{}")).isEqualTo(StreamErrorEvent(code = null, message = null))
    assertThat(parseStreamError(moshi, "not-json")).isNull()
  }
}
