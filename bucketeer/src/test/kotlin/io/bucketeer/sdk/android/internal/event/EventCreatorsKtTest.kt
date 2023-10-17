package io.bucketeer.sdk.android.internal.event

import org.junit.Test

class EventCreatorsKtTest {
  @Test
  fun testLabelTimeoutValueShouldInDoubleFormat() {
    assert(15000L.toStringInDoubleFormat() == "15.0")
    assert(1512334557L.toStringInDoubleFormat() == "1512334.557")
    assert(500L.toStringInDoubleFormat() == "0.5")
    assert(432L.toStringInDoubleFormat() == "0.432")
    assert(51L.toStringInDoubleFormat() == "0.051")
  }
}
