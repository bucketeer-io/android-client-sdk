package io.bucketeer.sdk.android.internal.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasonTypeTest {
  @Test
  fun testReasonTypeFromValidValues() {
    // Test successful evaluation reasons
    assertEquals(ReasonType.TARGET, ReasonType.from("TARGET"))
    assertEquals(ReasonType.RULE, ReasonType.from("RULE"))
    assertEquals(ReasonType.DEFAULT, ReasonType.from("DEFAULT"))
    assertEquals(ReasonType.OFF_VARIATION, ReasonType.from("OFF_VARIATION"))
    assertEquals(ReasonType.PREREQUISITE, ReasonType.from("PREREQUISITE"))

    // Test error evaluation reasons
    assertEquals(ReasonType.ERROR_NO_EVALUATIONS, ReasonType.from("ERROR_NO_EVALUATIONS"))
    assertEquals(ReasonType.ERROR_FLAG_NOT_FOUND, ReasonType.from("ERROR_FLAG_NOT_FOUND"))
    assertEquals(ReasonType.ERROR_WRONG_TYPE, ReasonType.from("ERROR_WRONG_TYPE"))
    assertEquals(ReasonType.ERROR_USER_ID_NOT_SPECIFIED, ReasonType.from("ERROR_USER_ID_NOT_SPECIFIED"))
    assertEquals(ReasonType.ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED, ReasonType.from("ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED"))
    assertEquals(ReasonType.ERROR_EXCEPTION, ReasonType.from("ERROR_EXCEPTION"))
    assertEquals(ReasonType.ERROR_CACHE_NOT_FOUND, ReasonType.from("ERROR_CACHE_NOT_FOUND"))

    // Test deprecated CLIENT reason type
    @Suppress("DEPRECATION")
    assertEquals(ReasonType.CLIENT, ReasonType.from("CLIENT"))
  }

  @Test
  fun testReasonTypeFromInvalidValue() {
    // Test that invalid values default to ERROR_EXCEPTION
    assertEquals(ReasonType.ERROR_EXCEPTION, ReasonType.from("INVALID_VALUE"))
    assertEquals(ReasonType.ERROR_EXCEPTION, ReasonType.from(""))
    assertEquals(ReasonType.ERROR_EXCEPTION, ReasonType.from("UNKNOWN"))
    assertEquals(ReasonType.ERROR_EXCEPTION, ReasonType.from("random_string"))
  }

  @Test
  fun testAllReasonTypesCanBeParsed() {
    // Verify all enum values (except deprecated CLIENT) can be parsed and roundtrip correctly
    ReasonType.values().forEach { reasonType ->
      if (reasonType != ReasonType.CLIENT) {
        assertEquals(reasonType, ReasonType.from(reasonType.name))
      }
    }
  }

  @Test
  fun testReasonTypeEnumValues() {
    // Verify all expected reason types are present in the enum
    val expectedValues =
      setOf(
        ReasonType.TARGET,
        ReasonType.RULE,
        ReasonType.DEFAULT,
        @Suppress("DEPRECATION")
        ReasonType.CLIENT,
        ReasonType.OFF_VARIATION,
        ReasonType.PREREQUISITE,
        ReasonType.ERROR_NO_EVALUATIONS,
        ReasonType.ERROR_FLAG_NOT_FOUND,
        ReasonType.ERROR_WRONG_TYPE,
        ReasonType.ERROR_USER_ID_NOT_SPECIFIED,
        ReasonType.ERROR_FEATURE_FLAG_ID_NOT_SPECIFIED,
        ReasonType.ERROR_EXCEPTION,
        ReasonType.ERROR_CACHE_NOT_FOUND,
      )

    val actualValues = ReasonType.values().toSet()
    assertEquals(expectedValues, actualValues)
  }
}
