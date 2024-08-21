@file:Suppress("ClassName")

package io.bucketeer.sdk.android.internal.evaluation

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTValue
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class GetVariationValueTest_String(
  private val variationValue: String,
  private val defaultValue: String,
  private val expectedValue: String,
) {
  companion object {
    @ParameterizedRobolectricTestRunner.Parameters(name = "getVariation String: {0} -> {1}")
    @JvmStatic
    fun testData(): List<*> =
      listOf(
        arrayOf("1", "", "1"),
        arrayOf("-1", "", "-1"),
        arrayOf("1.0", "", "1.0"),
        arrayOf("string", "", "string"),
        arrayOf("true", "", "true"),
        arrayOf("false", "", "false"),
        arrayOf("""{}""", "", "{}"),
      )
  }

  @Test
  fun test() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue)
    assertThat(actual).isEqualTo(expectedValue)
  }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class GetVariationValueTest_Int(
  private val variationValue: String,
  private val defaultValue: Int,
  private val expectedValue: Int,
) {
  companion object {
    @ParameterizedRobolectricTestRunner.Parameters(name = "getVariation Int/Long: {0} -> {1}")
    @JvmStatic
    fun testData(): List<*> =
      listOf(
        arrayOf("1", 0, 1),
        arrayOf("-1", 0, -1),
        arrayOf("1.0", 0, 1),
        arrayOf("1.0a", 0, 0),
        arrayOf("not int", 0, 0),
      )
  }

  @Test
  fun `test - Int`() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue)
    assertThat(actual).isEqualTo(expectedValue)
  }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class GetVariationValueTest_Float_Double(
  private val variationValue: String,
  private val defaultValue: Float,
  private val expectedValue: Float,
) {
  companion object {
    @ParameterizedRobolectricTestRunner.Parameters(name = "getVariation Float/Long: {0} -> {1}")
    @JvmStatic
    fun testData(): Collection<*> =
      listOf(
        arrayOf("1", 0f, 1f),
        arrayOf("-1", 0f, -1f),
        arrayOf("1.0", 0f, 1.0f),
        arrayOf("not float", 0f, 0f),
      )
  }

  @Test
  fun `test - Float`() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue)
    assertThat(actual).isEqualTo(expectedValue)
  }

  @Test
  fun `test - Double`() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue.toDouble())
    assertThat(actual).isEqualTo(expectedValue.toDouble())
  }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class GetVariationValueTest_Boolean(
  private val variationValue: String,
  private val defaultValue: Boolean,
  private val expectedValue: Boolean,
) {
  companion object {
    @ParameterizedRobolectricTestRunner.Parameters(
      name = """getVariation Boolean: ("{0}", {1}) -> {2}""",
    )
    @JvmStatic
    fun testData(): Collection<*> =
      listOf(
        arrayOf("true", false, true),
        arrayOf("false", true, false),
        arrayOf("true", true, true),
        arrayOf("TRUE", false, true),
        arrayOf("truea", false, false),
        arrayOf("not bool", false, false),
        arrayOf("not bool", true, true),
        arrayOf("1", false, false),
        arrayOf("1.0", false, false),
        arrayOf("{}", false, false),
      )
  }

  @Test
  fun test() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue)
    assertThat(actual).isEqualTo(expectedValue)
  }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class GetVariationValueTest_Json(
  private val variationValue: String,
  private val defaultValue: String,
  private val expectedValue: String,
) {
  companion object {
    private const val JSON1 = """{ "key": "value"}"""

    @ParameterizedRobolectricTestRunner.Parameters(
      name = """getVariation Json: ("{0}", {1}) -> {2}""",
    )
    @JvmStatic
    fun testData(): Collection<*> =
      arrayOf(
        arrayOf(JSON1, "{}", JSON1),
        arrayOf("true", JSON1, JSON1),
        arrayOf("true", "{}", "{}"),
        arrayOf("not bool", "{}", "{}"),
        arrayOf("1", "{}", "{}"),
        arrayOf("1.0", "{}", "{}"),
        arrayOf("{}", "{}", "{}"),
      ).toList()
  }

  @Test
  fun test() {
    val expected = JSONObject(expectedValue)
    val actual = buildEvaluation(variationValue).getVariationValue(JSONObject(defaultValue))

    assertThat(actual.keys().asSequence().toList())
      .isEqualTo(expected.keys().asSequence().toList())

    assertThat(
      actual
        .keys()
        .asSequence()
        .toList()
        .map { actual[it] },
    ).isEqualTo(
      expected
        .keys()
        .asSequence()
        .toList()
        .map { expected[it] },
    )
  }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class GetVariationValueTest_BKTValue(
  private val variationValue: String,
  private val defaultValue: BKTValue,
  private val expectedValue: BKTValue,
) {
  companion object {
    private const val JSON1 = """{ "key": "value"}"""

    @ParameterizedRobolectricTestRunner.Parameters(
      name = """getVariation Json: ("{0}", {1}) -> {2}""",
    )
    @JvmStatic
    fun testData(): Collection<*> =
      arrayOf(
        arrayOf(JSON1, BKTValue.Structure(mapOf()), BKTValue.Structure(mapOf("key" to BKTValue.String("value")))),
        arrayOf("true", BKTValue.Boolean(false), BKTValue.Boolean(true)),
        arrayOf("false", BKTValue.Boolean(true), BKTValue.Boolean(false)),
        arrayOf("not bool", BKTValue.String("{}"), BKTValue.String("not bool")),
        arrayOf("", BKTValue.String("{}"), BKTValue.String("")),
        arrayOf("null", BKTValue.String("{}"), BKTValue.String("null")),
        arrayOf("1", BKTValue.Integer(0), BKTValue.Integer(1)),
        arrayOf("1.0", BKTValue.Double(200.01), BKTValue.Double(1.0)),
        arrayOf("{}", BKTValue.Structure(mapOf("key" to BKTValue.String("value"))), BKTValue.Structure(mapOf())),
      ).toList()
  }

  @Test
  fun test() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue)
    assertThat(actual).isEqualTo(expectedValue)
  }
}

private fun buildEvaluation(value: String): Evaluation =
  Evaluation(
    id = "evaluation_id_value",
    featureId = "feature_id_value",
    featureVersion = 1,
    userId = "user_id_value",
    variationId = "variation_id",
    variationName = "variation name",
    variationValue = value,
    reason = Reason(ReasonType.DEFAULT),
  )
