

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.BKTValue
import io.bucketeer.sdk.android.BKTValueAdapter
import io.bucketeer.sdk.android.internal.evaluation.getBKTValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

class BKTValueTest {

  @Test
  fun testAsBoolean() {
    assertEquals(true, BKTValue.Boolean(true).asBoolean())
    assertNull(BKTValue.String("string").asBoolean())
    assertNull(BKTValue.Number(123.0).asBoolean())
    assertNull(BKTValue.Number(123.456).asBoolean())
    assertNull(BKTValue.List(listOf(BKTValue.Boolean(true))).asBoolean())
    assertNull(BKTValue.Structure(mapOf("key" to BKTValue.Boolean(true))).asBoolean())
    assertNull(BKTValue.Null.asBoolean())
  }

  @Test
  fun testAsString() {
    assertEquals("test", BKTValue.String("test").asString())
    assertNull(BKTValue.Boolean(true).asString())
    assertNull(BKTValue.Number(123.0).asString())
    assertNull(BKTValue.Number(123.456).asString())
    assertNull(BKTValue.List(listOf(BKTValue.String("value"))).asString())
    assertNull(BKTValue.Structure(mapOf("key" to BKTValue.String("value"))).asString())
    assertNull(BKTValue.Null.asString())
  }

  @Test
  fun testAsInteger() {
    assertEquals(123, BKTValue.Number(123.0).asInteger())
    assertNull(BKTValue.Boolean(true).asInteger())
    assertNull(BKTValue.String("string").asInteger())
    assertEquals(123, BKTValue.Number(123.456).asInteger())
    assertNull(BKTValue.List(listOf(BKTValue.Number(123.0))).asInteger())
    assertNull(BKTValue.Structure(mapOf("key" to BKTValue.Number(123.0))).asInteger())
    assertNull(BKTValue.Null.asInteger())
  }

  @Test
  fun testAsDouble() {
    assertEquals(123.456, BKTValue.Number(123.456).asDouble())
    assertNull(BKTValue.Boolean(true).asDouble())
    assertNull(BKTValue.String("string").asDouble())
    assertEquals(123.0, BKTValue.Number(123.0).asDouble())
    assertNull(BKTValue.List(listOf(BKTValue.Number(123.456))).asDouble())
    assertNull(BKTValue.Structure(mapOf("key" to BKTValue.Number(123.456))).asDouble())
    assertNull(BKTValue.Null.asDouble())
  }

  @Test
  fun testAsList() {
    assertEquals(
      listOf(BKTValue.String("value")),
      BKTValue.List(listOf(BKTValue.String("value"))).asList()
    )
    assertNull(BKTValue.Boolean(true).asList())
    assertNull(BKTValue.String("string").asList())
    assertNull(BKTValue.Number(123.0).asList())
    assertNull(BKTValue.Number(123.456).asList())
    assertNull(
      BKTValue.Structure(mapOf("key" to BKTValue.List(listOf(BKTValue.String("value"))))).asList()
    )
    assertNull(BKTValue.Null.asList())
  }

  @Test
  fun testAsStructure() {
    assertEquals(
      mapOf("key" to BKTValue.String("value")),
      BKTValue.Structure(mapOf("key" to BKTValue.String("value"))).asStructure()
    )
    assertNull(BKTValue.Boolean(true).asStructure())
    assertNull(BKTValue.String("string").asStructure())
    assertNull(BKTValue.Number(123.0).asStructure())
    assertNull(BKTValue.Number(123.456).asStructure())
    assertNull(
      BKTValue.List(listOf(BKTValue.Structure(mapOf("key" to BKTValue.String("value")))))
        .asStructure()
    )
    assertNull(BKTValue.Null.asStructure())
  }

  @Test
  fun testIsNull() {
    assertTrue(BKTValue.Null.isNull())
    assertFalse(BKTValue.String("string").isNull())
  }

  @Test
  fun testGetVariationBKTValue() {
    // Simple string values
    assertEquals(BKTValue.String(""), "".getBKTValue())
    assertEquals(BKTValue.String("null"), "null".getBKTValue())
    assertEquals(BKTValue.String("test"), "test".getBKTValue())
    assertEquals(BKTValue.String("test value"), "test value".getBKTValue())
    assertEquals(BKTValue.String("test value"), "\"test value\"".getBKTValue())

    // Boolean values
    assertEquals(BKTValue.Boolean(true), "true".getBKTValue())
    assertEquals(BKTValue.Boolean(false), "false".getBKTValue())

    // Numeric values
    assertEquals(BKTValue.Number(1.0), "1".getBKTValue())
    assertEquals(BKTValue.Number(1.0), "1.0".getBKTValue())
    assertEquals(BKTValue.Number(1.2), "1.2".getBKTValue())
    assertEquals(BKTValue.Number(1.234), "1.234".getBKTValue())

    // JSON string as a dictionary
    val dictionaryJSONText = """
{
  "value" : "body",
  "value1" : "body1",
  "valueInt" : 1,
  "valueBool" : true,
  "valueDouble" : 1.2,
  "valueDictionary": {"key" : "value"},
  "valueList1": [{"key" : "value"},{"key" : 10}],
  "valueList2": [1,2.2,true]
}
"""
    val expectedDictionaryValue = BKTValue.Structure(
      mapOf(
        "value" to BKTValue.String("body"),
        "value1" to BKTValue.String("body1"),
        "valueInt" to BKTValue.Number(1.0),
        "valueBool" to BKTValue.Boolean(true),
        "valueDouble" to BKTValue.Number(1.2),
        "valueDictionary" to BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
        "valueList1" to BKTValue.List(
          listOf(
            BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
            BKTValue.Structure(mapOf("key" to BKTValue.Number(10.0)))
          )
        ),
        "valueList2" to BKTValue.List(
          listOf(
            BKTValue.Number(1.0),
            BKTValue.Number(2.2),
            BKTValue.Boolean(true)
          )
        )
      )
    )
    assertEquals(expectedDictionaryValue, dictionaryJSONText.getBKTValue())

    // JSON string as a list (first example)
    val listJSON1Text = """
[
    {"key" : "value"},
    {"key" : 10}
]
"""
    val expectedListValue1 = BKTValue.List(
      listOf(
        BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
        BKTValue.Structure(mapOf("key" to BKTValue.Number(10.0)))
      )
    )
    assertEquals(expectedListValue1, listJSON1Text.getBKTValue())

    // JSON string as a list (second example)
    val listJSON2Text = """
  [1,2.2,true]
"""
    val expectedListValue2 = BKTValue.List(
      listOf(
        BKTValue.Number(1.0),
        BKTValue.Number(2.2),
        BKTValue.Boolean(true)
      )
    )
    assertEquals(expectedListValue2, listJSON2Text.getBKTValue())
  }
}

@RunWith(TestParameterInjector::class)
class BKTValueAdapterTest {
  lateinit var adapter: JsonAdapter<BKTValue>

  @Before
  fun setup() {
    adapter = BKTValueAdapter()
  }

  @Test
  fun fromJson(
    @TestParameter type: TestParameters,
  ) {
    try {
      val value = adapter.fromJson(type.json)
      assertThat(value).isEqualTo(type.value)
    } catch (ex: Exception) {
      assertThat(type.fromJsonShouldThrowError).isTrue()
    }
  }

  @Test
  fun toJson(
    @TestParameter type: TestParameters,
  ) {
    val result = adapter.toJson(type.value)
    val decodedValue = adapter.fromJson(result)
    assertThat(decodedValue).isEqualTo(type.value)
  }

  @Suppress("unused")
  enum class TestParameters(
    val value: BKTValue,
    val json: String,
    // fromJsonShouldThrowError to mark the case which the value is not valid JSON
    val fromJsonShouldThrowError: Boolean,
  ) {
    DOUBLE1(BKTValue.Number(42.0), "42.00", false),
    DOUBLE2(BKTValue.Number(42.0), "42.0", false),
    DOUBLE3(BKTValue.Number(42.55), "42.55", false),
    STRING1(BKTValue.String("hello"), "\"hello\"", false),
    STRING2(BKTValue.String("test default"), "test default", true),
    STRING3(BKTValue.String("42,0"), "42,0", true),
    STRING4(BKTValue.String("42.0"), "\"42.0\"", false),
    STRING5(BKTValue.String(""), "\"\"", false),
    STRING6(BKTValue.String("null"), "\"null\"", false),
    BOOLEAN(BKTValue.Boolean(true), "true", false),
    STRUCTURE_STRING(
      BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
      "{\"key\":\"value\"}",
      false,
    ),
    STRUCTURE_MIXED(
      BKTValue.Structure(
        mapOf(
          "string" to BKTValue.String("value"),
          "integer" to BKTValue.Number(42.0),
          "boolean" to BKTValue.Boolean(true),
          "double" to BKTValue.Number(42.5),
          "nestedList" to
            BKTValue.List(
              listOf(
                BKTValue.String("nestedValue"),
                BKTValue.Number(100.0),
              ),
            ),
          "nestedStructure" to
            BKTValue.Structure(
              mapOf("nestedKey" to BKTValue.String("nestedValue")),
            ),
        ),
      ),
      """
      {
          "string":"value",
          "integer":42,
          "boolean":true,
          "double":42.5,
          "nestedList":["nestedValue",100],
          "nestedStructure":{"nestedKey":"nestedValue"}
      }
      """.trimIndent(),
      false,
    ),
    LIST_STRING(
      BKTValue.List(listOf(BKTValue.String("value"))),
      "[\"value\"]",
      false,
    ),
    LIST_MIXED(
      BKTValue.List(
        listOf(
          BKTValue.String("value"),
          BKTValue.Number(42.0),
          BKTValue.Boolean(true),
          BKTValue.Number(42.5),
          BKTValue.List(listOf(BKTValue.String("nestedValue"))),
          BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
        ),
      ),
      """
      [
          "value",
          42,
          true,
          42.5,
          ["nestedValue"],
          {"key":"value"}
      ]
      """.trimIndent(),
      false,
    ),
    NULL(BKTValue.Null, "null", false),
    EMPTY_STRING(BKTValue.Null, "", true),
  }
}

@RunWith(TestParameterInjector::class)
class BKTValueFromRawStringTest {
  lateinit var adapter: JsonAdapter<BKTValue>

  @Before
  fun setup() {
    adapter = BKTValueAdapter()
  }

  @Test
  fun extensionFromStringToBKTValue(
    @TestParameter testcase: TestParameters,
  ) {
    val decodedValue = testcase.json.getBKTValue()
    assertThat(decodedValue).isEqualTo(testcase.value)
  }

  @Suppress("unused")
  enum class TestParameters(
    val value: BKTValue,
    val json: String,
  ) {
    DOUBLE1(BKTValue.Number(42.0), "42.00"),
    DOUBLE2(BKTValue.Number(42.0), "42.0"),
    DOUBLE3(BKTValue.Number(42.55), "42.55"),
    STRING1(BKTValue.String("hello"), "\"hello\""),
    STRING2(BKTValue.String("test default"), "test default"),
    STRING3(BKTValue.String("42,0"), "42,0"),
    STRING4(BKTValue.String("42.0"), "\"42.0\""),
    STRING5(BKTValue.String(""), "\"\""),
    STRING6(BKTValue.String("null"), "\"null\""),
    BOOLEAN(BKTValue.Boolean(true), "true"),
    STRUCTURE_STRING(
      BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
      "{\"key\":\"value\"}",
    ),
    STRUCTURE_MIXED(
      BKTValue.Structure(
        mapOf(
          "string" to BKTValue.String("value"),
          "integer" to BKTValue.Number(42.0),
          "boolean" to BKTValue.Boolean(true),
          "double" to BKTValue.Number(42.5),
          "nestedList" to
            BKTValue.List(
              listOf(
                BKTValue.String("nestedValue"),
                BKTValue.Number(100.0),
              ),
            ),
          "nestedStructure" to
            BKTValue.Structure(
              mapOf("nestedKey" to BKTValue.String("nestedValue")),
            ),
        ),
      ),
      """
      {
          "string":"value",
          "integer":42,
          "boolean":true,
          "double":42.5,
          "nestedList":["nestedValue",100],
          "nestedStructure":{"nestedKey":"nestedValue"}
      }
      """.trimIndent(),
    ),
    LIST_STRING(
      BKTValue.List(listOf(BKTValue.String("value"))),
      "[\"value\"]",
    ),
    LIST_MIXED(
      BKTValue.List(
        listOf(
          BKTValue.String("value"),
          BKTValue.Number(42.0),
          BKTValue.Boolean(true),
          BKTValue.Number(42.5),
          BKTValue.List(listOf(BKTValue.String("nestedValue"))),
          BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
        ),
      ),
      """
      [
          "value",
          42,
          true,
          42.5,
          ["nestedValue"],
          {"key":"value"}
      ]
      """.trimIndent(),
    ),
    NULL(BKTValue.String("null"), "null"),
    EMPTY_STRING(BKTValue.String(""), ""),
  }
}
