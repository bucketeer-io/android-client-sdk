@file:Suppress("ClassName")

package io.bucketeer.sdk.android.internal.evaluation

import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.internal.model.Reason
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.Variation
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
    fun testData(): List<*> {
      return listOf(
        arrayOf("1", "", "1"),
        arrayOf("-1", "", "-1"),
        arrayOf("1.0", "", "1.0"),
        arrayOf("string", "", "string"),
        arrayOf("true", "", "true"),
        arrayOf("false", "", "false"),
        arrayOf("""{}""", "", "{}"),
      )
    }
  }

  @Test
  fun test() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue)
    assertThat(actual).isEqualTo(expectedValue)
  }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
class GetVariationValueTest_Int_Long(
  private val variationValue: String,
  private val defaultValue: Int,
  private val expectedValue: Int,
) {
  companion object {
    @ParameterizedRobolectricTestRunner.Parameters(name = "getVariation Int/Long: {0} -> {1}")
    @JvmStatic
    fun testData(): List<*> {
      return listOf(
        arrayOf("1", 0, 1),
        arrayOf("-1", 0, -1),
        arrayOf("1.0", 0, 0),
        arrayOf("1.0a", 0, 0),
        arrayOf("not int", 0, 0),
      )
    }
  }

  @Test
  fun `test - Int`() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue)
    assertThat(actual).isEqualTo(expectedValue)
  }

  @Test
  fun `test - Long`() {
    val actual = buildEvaluation(variationValue).getVariationValue(defaultValue.toLong())
    assertThat(actual).isEqualTo(expectedValue.toLong())
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
    fun testData(): Collection<*> {
      return listOf(
        arrayOf("1", 0f, 1f),
        arrayOf("-1", 0f, -1f),
        arrayOf("1.0", 0f, 1.0f),
        arrayOf("not float", 0f, 0f),
      )
    }
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
    fun testData(): Collection<*> {
      return listOf(
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
    fun testData(): Collection<*> {
      return arrayOf(
        arrayOf(JSON1, "{}", JSON1),
        arrayOf("true", JSON1, JSON1),
        arrayOf("true", "{}", "{}"),
        arrayOf("not bool", "{}", "{}"),
        arrayOf("1", "{}", "{}"),
        arrayOf("1.0", "{}", "{}"),
        arrayOf("{}", "{}", "{}"),
      ).toList()
    }
  }

  @Test
  fun test() {
    val expected = JSONObject(expectedValue)
    val actual = buildEvaluation(variationValue).getVariationValue(JSONObject(defaultValue))

    assertThat(actual.keys().asSequence().toList())
      .isEqualTo(expected.keys().asSequence().toList())

    assertThat(actual.keys().asSequence().toList().map { actual[it] })
      .isEqualTo(expected.keys().asSequence().toList().map { expected[it] })
  }
}

private fun buildEvaluation(value: String): Evaluation {
  return Evaluation(
    id = "evaluation_id_value",
    feature_id = "feature_id_value",
    feature_version = 1,
    user_id = "user_id_value",
    variation_id = "variation_id",
    variation_value = value,
    variation = Variation(
      id = "variation_id_value",
      value = "value",
    ),
    reason = Reason(ReasonType.DEFAULT),
  )
}
