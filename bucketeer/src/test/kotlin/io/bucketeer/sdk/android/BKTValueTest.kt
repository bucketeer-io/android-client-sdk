
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.BKTValue
import io.bucketeer.sdk.android.BKTValueAdapter
import io.bucketeer.sdk.android.internal.evaluation.getBKTValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
    DOUBLE1(BKTValue.Double(42.0), "42.00", false),
    DOUBLE2(BKTValue.Double(42.0), "42.0", false),
    DOUBLE3(BKTValue.Double(42.55), "42.55", false),
    STRING1(BKTValue.String("hello"), "\"hello\"", false),
    STRING2(BKTValue.String("test default"), "test default", true),
    STRING3(BKTValue.String("42,0"), "42,0", true),
    STRING4(BKTValue.String("42.0"), "\"42.0\"", false),
    STRING5(BKTValue.String(""), "\"\"", false),
    STRING6(BKTValue.String("null"), "\"null\"", false),
    BOOLEAN(BKTValue.Boolean(true), "true", false),
    INTEGER(BKTValue.Integer(42), "42", false),
    STRUCTURE_STRING(
      BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
      "{\"key\":\"value\"}",
      false,
    ),
    STRUCTURE_MIXED(
      BKTValue.Structure(
        mapOf(
          "string" to BKTValue.String("value"),
          "integer" to BKTValue.Integer(42),
          "boolean" to BKTValue.Boolean(true),
          "double" to BKTValue.Double(42.5),
          "nestedList" to
            BKTValue.List(
              listOf(
                BKTValue.String("nestedValue"),
                BKTValue.Integer(100),
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
          BKTValue.Integer(42),
          BKTValue.Boolean(true),
          BKTValue.Double(42.5),
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
    DOUBLE1(BKTValue.Double(42.0), "42.00"),
    DOUBLE2(BKTValue.Double(42.0), "42.0"),
    DOUBLE3(BKTValue.Double(42.55), "42.55"),
    STRING1(BKTValue.String("hello"), "\"hello\""),
    STRING2(BKTValue.String("test default"), "test default"),
    STRING3(BKTValue.String("42,0"), "42,0"),
    STRING4(BKTValue.String("42.0"), "\"42.0\""),
    STRING5(BKTValue.String(""), "\"\""),
    STRING6(BKTValue.String("null"), "\"null\""),
    BOOLEAN(BKTValue.Boolean(true), "true"),
    INTEGER(BKTValue.Integer(42), "42"),
    STRUCTURE_STRING(
      BKTValue.Structure(mapOf("key" to BKTValue.String("value"))),
      "{\"key\":\"value\"}",
    ),
    STRUCTURE_MIXED(
      BKTValue.Structure(
        mapOf(
          "string" to BKTValue.String("value"),
          "integer" to BKTValue.Integer(42),
          "boolean" to BKTValue.Boolean(true),
          "double" to BKTValue.Double(42.5),
          "nestedList" to
            BKTValue.List(
              listOf(
                BKTValue.String("nestedValue"),
                BKTValue.Integer(100),
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
          BKTValue.Integer(42),
          BKTValue.Boolean(true),
          BKTValue.Double(42.5),
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
