package io.bucketeer.sdk.android.internal.event

import org.junit.Test

class EventCreatorsKtTest {
    @Test
    fun testLabelTimeoutValueShouldInDoubleFormat() {
      val input = 15000L
      val expectedDouble = 15.0
      val timeoutString = input.toStringInDoubleFormat()
      assert(timeoutString == expectedDouble.toString())
      assert(timeoutString == "15.0")

      val input2 = 1512334557L
      val expectedDouble2 = 1512334.557
      val timeoutString2 = input2.toStringInDoubleFormat()
      assert(timeoutString2 == expectedDouble2.toString())
      assert(timeoutString2 == "1512334.557")
    }
}
