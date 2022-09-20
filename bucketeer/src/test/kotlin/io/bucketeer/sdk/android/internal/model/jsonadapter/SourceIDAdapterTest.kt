package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.SourceID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SourceIDAdapterTest {

  lateinit var adapter: JsonAdapter<SourceID>

  @Before
  fun setup() {
    adapter = DataModule.createMoshi().adapter(SourceID::class.java)
  }

  @Test
  fun fromJson(@TestParameter sourceID: SourceID) {
    val result = adapter.fromJson(sourceID.value.toString())

    assertThat(result).isEqualTo(sourceID)
  }

  @Test
  fun toJson(@TestParameter sourceID: SourceID) {
    val result = adapter.toJson(sourceID)

    assertThat(result).isEqualTo(sourceID.value.toString())
  }
}
