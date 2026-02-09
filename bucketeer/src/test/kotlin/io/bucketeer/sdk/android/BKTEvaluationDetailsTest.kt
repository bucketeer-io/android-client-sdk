package io.bucketeer.sdk.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BKTEvaluationDetailsTest {
  @Test
  fun testDefaultInstance() {
    val userId = "1"
    val featureId = "1001"
    val intDefaultInstance: BKTEvaluationDetails<Int> =
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = userId,
        1,
        reason = BKTEvaluationDetails.Reason.ERROR_FLAG_NOT_FOUND,
      )
    assertEquals(
      intDefaultInstance,
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = 1,
        reason = BKTEvaluationDetails.Reason.ERROR_FLAG_NOT_FOUND,
      ),
    )

    val doubleDefaultInstance: BKTEvaluationDetails<Double> =
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = userId,
        1.0,
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      )
    assertEquals(
      doubleDefaultInstance,
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = 1.0,
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      ),
    )

    val booleanDefaultInstance: BKTEvaluationDetails<Boolean> =
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = userId,
        true,
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      )
    assertEquals(
      booleanDefaultInstance,
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = true,
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      ),
    )

    val stringDefaultInstance: BKTEvaluationDetails<String> =
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = userId,
        "1",
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      )
    assertEquals(
      stringDefaultInstance,
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = "1",
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      ),
    )

    val json1 = JSONObject("{\"key1\": \"value1\", \"key\": \"value\"}")
    val jsonDefaultInstance: BKTEvaluationDetails<JSONObject> =
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = userId,
        json1,
        reason = BKTEvaluationDetails.Reason.ERROR_FLAG_NOT_FOUND,
      )
    assertEquals(
      jsonDefaultInstance,
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = json1,
        reason = BKTEvaluationDetails.Reason.ERROR_FLAG_NOT_FOUND,
      ),
    )

    val object1 =
      BKTValue.Structure(
        mapOf(
          "key1" to BKTValue.String("value1"),
          "key" to BKTValue.String("value"),
        ),
      )
    val object1DefaultInstance: BKTEvaluationDetails<BKTValue> =
      BKTEvaluationDetails.newDefaultInstance(
        featureId = featureId,
        userId = userId,
        object1,
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      )
    assertEquals(
      object1DefaultInstance,
      BKTEvaluationDetails(
        featureId = featureId,
        featureVersion = 0,
        userId = userId,
        variationId = "",
        variationName = "",
        variationValue = object1,
        reason = BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      ),
    )
  }

  @Test
  fun testEqualsWithInt() {
    val detail1 =
      BKTEvaluationDetails(
        "feature1",
        1,
        "user1",
        "var1",
        "name1",
        10,
        BKTEvaluationDetails.Reason.TARGET,
      )
    val detail2 =
      BKTEvaluationDetails(
        "feature1",
        1,
        "user1",
        "var1",
        "name1",
        10,
        BKTEvaluationDetails.Reason.TARGET,
      )
    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = 11), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithString() {
    val detail1 =
      BKTEvaluationDetails(
        "feature2",
        2,
        "user2",
        "var2",
        "name2",
        "value",
        BKTEvaluationDetails.Reason.RULE,
      )
    val detail2 =
      BKTEvaluationDetails(
        "feature2",
        2,
        "user2",
        "var2",
        "name2",
        "value",
        BKTEvaluationDetails.Reason.RULE,
      )
    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = "12"), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithDouble() {
    val detail1 =
      BKTEvaluationDetails(
        "feature3",
        3,
        "user3",
        "var3",
        "name3",
        10.0,
        BKTEvaluationDetails.Reason.DEFAULT,
      )
    val detail2 =
      BKTEvaluationDetails(
        "feature3",
        3,
        "user3",
        "var3",
        "name3",
        10.0,
        BKTEvaluationDetails.Reason.DEFAULT,
      )
    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = 33.0), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithBoolean() {
    val detail1 =
      BKTEvaluationDetails(
        "feature4",
        4,
        "user4",
        "var4",
        "name4",
        true,
        BKTEvaluationDetails.Reason.CLIENT,
      )
    val detail2 =
      BKTEvaluationDetails(
        "feature4",
        4,
        "user4",
        "var4",
        "name4",
        true,
        BKTEvaluationDetails.Reason.CLIENT,
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
      BKTEvaluationDetails(
        "feature5",
        5,
        "user5",
        "var5",
        "name5",
        json1,
        BKTEvaluationDetails.Reason.OFF_VARIATION,
      )
    val detail2 =
      BKTEvaluationDetails(
        "feature5",
        5,
        "user5",
        "var5",
        "name5",
        json2,
        BKTEvaluationDetails.Reason.OFF_VARIATION,
      )

    assertEquals(detail1, detail2)
    assertEquals(detail1, detail2.copy(variationValue = json3))
    assertEquals(detail1, detail2.copy(variationValue = json3Extended))
    assertEquals(detail1.copy(variationValue = json3), detail2)

    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
    assertNotEquals(detail1.copy(variationValue = json4), detail2)
    assertNotEquals(detail1.copy(variationValue = json5), detail2)
  }

  @Test
  fun testEqualsWithBKTValueString() {
    val detail1 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.String("value"),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    val detail2 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.String("value"),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = BKTValue.String("12")), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithBKTValueInt() {
    val detail1 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.Number(1.0),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    val detail2 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.Number(1.0),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = BKTValue.Number(2.0)), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithBKTValueBoolean() {
    val detail1 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.Boolean(true),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    val detail2 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.Boolean(true),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = BKTValue.Boolean(false)), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithBKTValueList() {
    val detail1 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue =
          BKTValue.List(
            listOf(
              BKTValue.String("value1"),
              BKTValue.String("value2"),
            ),
          ),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    val detail2 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue =
          BKTValue.List(
            listOf(
              BKTValue.String("value1"),
              BKTValue.String("value2"),
            ),
          ),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    assertEquals(detail1, detail2)
    assertNotEquals(
      detail1.copy(variationValue = BKTValue.List(listOf(BKTValue.String("value3")))),
      detail2,
    )
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithBKTValueStruct() {
    val detail1 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue =
          BKTValue.Structure(
            mapOf(
              "key1" to BKTValue.String("value1"),
              "key2" to BKTValue.String("value2"),
            ),
          ),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    val detail2 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue =
          BKTValue.Structure(
            mapOf(
              "key1" to BKTValue.String("value1"),
              "key2" to BKTValue.String("value2"),
            ),
          ),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    assertEquals(detail1, detail2)
    assertNotEquals(
      detail1.copy(
        variationValue =
          BKTValue.Structure(
            mapOf(
              "key3" to
                BKTValue.String(
                  "value3",
                ),
            ),
          ),
      ),
      detail2,
    )
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testEqualsWithBKTValueDouble() {
    val detail1 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.Number(1.0),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    val detail2 =
      BKTEvaluationDetails(
        featureId = "feature2",
        featureVersion = 2,
        userId = "user2",
        variationId = "var2",
        variationName = "name2",
        variationValue = BKTValue.Number(1.0),
        reason = BKTEvaluationDetails.Reason.RULE,
      )

    assertEquals(detail1, detail2)
    assertNotEquals(detail1.copy(variationValue = BKTValue.Number(2.0)), detail2)
    assertNotEquals(detail1.copy(featureId = "2", featureVersion = 2), detail2)
  }

  @Test
  fun testReasonFrom() {
    // Test cases for each valid Reason - successful evaluations
    assertEquals(BKTEvaluationDetails.Reason.TARGET, BKTEvaluationDetails.Reason.from("TARGET"))
    assertEquals(BKTEvaluationDetails.Reason.RULE, BKTEvaluationDetails.Reason.from("RULE"))
    assertEquals(BKTEvaluationDetails.Reason.DEFAULT, BKTEvaluationDetails.Reason.from("DEFAULT"))
    assertEquals(
      BKTEvaluationDetails.Reason.OFF_VARIATION,
      BKTEvaluationDetails.Reason.from("OFF_VARIATION"),
    )
    assertEquals(
      BKTEvaluationDetails.Reason.PREREQUISITE,
      BKTEvaluationDetails.Reason.from("PREREQUISITE"),
    )

    // Test cases for error reason types
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_NO_EVALUATIONS,
      BKTEvaluationDetails.Reason.from("ERROR_NO_EVALUATIONS"),
    )
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_FLAG_NOT_FOUND,
      BKTEvaluationDetails.Reason.from("ERROR_FLAG_NOT_FOUND"),
    )
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_WRONG_TYPE,
      BKTEvaluationDetails.Reason.from("ERROR_WRONG_TYPE"),
    )
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_USER_ID_NOT_SPECIFIED,
      BKTEvaluationDetails.Reason.from("ERROR_USER_ID_NOT_SPECIFIED"),
    )
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED,
      BKTEvaluationDetails.Reason.from("ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED"),
    )
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      BKTEvaluationDetails.Reason.from("ERROR_EXCEPTION"),
    )
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_CACHE_NOT_FOUND,
      BKTEvaluationDetails.Reason.from("ERROR_CACHE_NOT_FOUND"),
    )

    // Test deprecated CLIENT reason type
    @Suppress("DEPRECATION")
    assertEquals(BKTEvaluationDetails.Reason.CLIENT, BKTEvaluationDetails.Reason.from("CLIENT"))

    // Test case for an invalid Reason which should return ERROR_EXCEPTION as default
    assertEquals(
      BKTEvaluationDetails.Reason.ERROR_EXCEPTION,
      BKTEvaluationDetails.Reason.from("INVALID_REASON"),
    )
  }
}
