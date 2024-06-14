package io.bucketeer.sdk.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BKTEvaluationDetailTest {
  @Test
  fun testDefaultInstance() {
    val userId = "1"
    val intDefaultInstance: BKTEvaluationDetail<Int> =
      BKTEvaluationDetail.newDefaultInstance(userId = userId, 1)
    assertEquals(
      intDefaultInstance,
      BKTEvaluationDetail(
        id = "",
        featureId = "",
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = 1,
        reason = BKTEvaluationDetail.Reason.CLIENT,
      ),
    )

    val doubleDefaultInstance: BKTEvaluationDetail<Double> =
      BKTEvaluationDetail.newDefaultInstance(userId = userId, 1.0)
    assertEquals(
      doubleDefaultInstance,
      BKTEvaluationDetail(
        id = "",
        featureId = "",
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = 1.0,
        reason = BKTEvaluationDetail.Reason.CLIENT,
      ),
    )

    val booleanDefaultInstance: BKTEvaluationDetail<Boolean> =
      BKTEvaluationDetail.newDefaultInstance(userId = userId, true)
    assertEquals(
      booleanDefaultInstance,
      BKTEvaluationDetail(
        id = "",
        featureId = "",
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = true,
        reason = BKTEvaluationDetail.Reason.CLIENT,
      ),
    )

    val stringDefaultInstance: BKTEvaluationDetail<String> =
      BKTEvaluationDetail.newDefaultInstance(userId = userId, "1")
    assertEquals(
      stringDefaultInstance,
      BKTEvaluationDetail(
        id = "",
        featureId = "",
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = "1",
        reason = BKTEvaluationDetail.Reason.CLIENT,
      ),
    )

    val json1 = JSONObject("{\"key1\": \"value1\", \"key\": \"value\"}")
    val jsonDefaultInstance: BKTEvaluationDetail<JSONObject> =
      BKTEvaluationDetail.newDefaultInstance(userId = userId, json1)
    assertEquals(
      jsonDefaultInstance,
      BKTEvaluationDetail(
        id = "",
        featureId = "",
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = json1,
        reason = BKTEvaluationDetail.Reason.CLIENT,
      ),
    )
  }

  @Test
  fun testEqualsWithInt() {
    val detail1 =
      BKTEvaluationDetail(
        "1",
        "feature1",
        1,
        "user1",
        "var1",
        "name1",
        10,
        BKTEvaluationDetail.Reason.TARGET,
      )
    val detail2 =
      BKTEvaluationDetail(
        "1",
        "feature1",
        1,
        "user1",
        "var1",
        "name1",
        10,
        BKTEvaluationDetail.Reason.TARGET,
      )
    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = 11), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithString() {
    val detail1 =
      BKTEvaluationDetail(
        "2",
        "feature2",
        2,
        "user2",
        "var2",
        "name2",
        "value",
        BKTEvaluationDetail.Reason.RULE,
      )
    val detail2 =
      BKTEvaluationDetail(
        "2",
        "feature2",
        2,
        "user2",
        "var2",
        "name2",
        "value",
        BKTEvaluationDetail.Reason.RULE,
      )
    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = "12"), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithDouble() {
    val detail1 =
      BKTEvaluationDetail(
        "3",
        "feature3",
        3,
        "user3",
        "var3",
        "name3",
        10.0,
        BKTEvaluationDetail.Reason.DEFAULT,
      )
    val detail2 =
      BKTEvaluationDetail(
        "3",
        "feature3",
        3,
        "user3",
        "var3",
        "name3",
        10.0,
        BKTEvaluationDetail.Reason.DEFAULT,
      )
    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = 33.0), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithBoolean() {
    val detail1 =
      BKTEvaluationDetail(
        "4",
        "feature4",
        4,
        "user4",
        "var4",
        "name4",
        true,
        BKTEvaluationDetail.Reason.CLIENT,
      )
    val detail2 =
      BKTEvaluationDetail(
        "4",
        "feature4",
        4,
        "user4",
        "var4",
        "name4",
        true,
        BKTEvaluationDetail.Reason.CLIENT,
      )
    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = false), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithJSONObject() {
    val json1 = JSONObject("{\"key1\": \"value1\", \"key\": \"value\"}")
    val json2 = JSONObject("{\"key1\": \"value1\", \"key\": \"value\"}")
    val json3 = JSONObject("{\"key\": \"value\", \"key1\": \"value1\"}")
    val json3Extended =
      JSONObject("{\"key\": \"value\", \"key1\": \"value1\", \"key3\": \"value3\"}}")
    val json4 = JSONObject("{\"key\": \"value1\"}")
    val json5 = JSONObject("{\"key5\": \"value\", \"key1\": \"value1\"}, \"key3\": \"value3\"}")
    val detail1 =
      BKTEvaluationDetail(
        "5",
        "feature5",
        5,
        "user5",
        "var5",
        "name5",
        json1,
        BKTEvaluationDetail.Reason.OFF_VARIATION,
      )
    val detail2 =
      BKTEvaluationDetail(
        "5",
        "feature5",
        5,
        "user5",
        "var5",
        "name5",
        json2,
        BKTEvaluationDetail.Reason.OFF_VARIATION,
      )

    assertEquals(detail1, detail2)
    assertEquals(detail1, detail2.copy(variationValue = json3))
    assertEquals(detail1, detail2.copy(variationValue = json3Extended))
    assertEquals(detail1.copy(variationValue = json3), detail2)

    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
    assertNotEquals(detail1.copy(variationValue = json4), detail2)
    assertNotEquals(detail1.copy(variationValue = json5), detail2)
  }
}
