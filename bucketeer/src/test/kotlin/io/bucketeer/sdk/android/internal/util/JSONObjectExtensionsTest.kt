package io.bucketeer.sdk.android.internal.util
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JSONObjectExtensionsTest {
  @Test
  fun testJSONObjectContains() {
    val jsonA = JSONObject("""{"key1": "value1", "key2": "value2"}""")
    val jsonB = JSONObject("""{"key1": "value1", "key2": "value2", "key3": "value3"}""")
    val jsonC = JSONObject("""{"key1": "value1", "key2": "differentValue"}""")
    val jsonD = JSONObject("""{"key4": "value4"}""")
    val jsonE = JSONObject("""{"nested": {"key1": "value1"}}""")
    val jsonF = JSONObject("""{"nested": {"key1": "value1", "key2": "value2"}}""")
    val jsonG = JSONObject("""{"array": [1, 2, 3]}""")
    val jsonH = JSONObject("""{"array": [1, 2, 3, 4]}""")
    val jsonI = JSONObject("""{"boolean": true, "number": 123, "null": null}""")
    val jsonJ = JSONObject("""{"boolean": true, "number": 123, "null": null, "extra": "value"}""")
    val jsonK = JSONObject("""{"array": [{"nested": {"key1": "value1"}}]}""")
    val jsonL = JSONObject("""{"array": [{"nested": {"key1": "value1", "key2": "value2"}}]}""")

    assertTrue(jsonB.contains(jsonA)) // jsonB contains jsonA
    assertFalse(jsonA.contains(jsonB)) // jsonA does not contain jsonB
    assertFalse(jsonB.contains(jsonC)) // jsonB does not contain jsonC due to different value
    assertFalse(jsonB.contains(jsonD)) // jsonB does not contain jsonD as it lacks key4

    // Nested JSONObjects
    assertTrue(jsonF.contains(jsonE)) // jsonF contains jsonE
    assertFalse(jsonE.contains(jsonF)) // jsonE does not contain jsonF

    // JSONArrays within JSONObjects
    assertTrue(jsonG.contains(JSONObject("""{"array": [1, 2, 3]}"""))) // jsonG contains [1, 2, 3]
    assertFalse(jsonG.contains(JSONObject("""{"array": [1, 2]}"""))) // jsonG does not contain [1, 2]
    assertFalse(jsonG.contains(jsonH)) // jsonG's array does not contain [1, 2, 3, 4]

    // Mixed types
    assertTrue(jsonJ.contains(jsonI)) // jsonJ contains jsonI with boolean, number, and null
    assertFalse(jsonI.contains(jsonJ)) // jsonI does not contain jsonJ due to extra key

    // Nested arrays of objects
    assertTrue(jsonL.contains(jsonK)) // jsonL contains jsonK
    assertFalse(jsonK.contains(jsonL)) // jsonK does not contain jsonL
  }
}
