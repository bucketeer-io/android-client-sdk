package io.bucketeer.sdk.android.internal.model.jsonadapter

import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.squareup.moshi.JsonAdapter
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.MetricsEventType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class MetricsEventTypeAdapterTest {

  lateinit var adapter: JsonAdapter<MetricsEventType>

  @Before
  fun setup() {
    adapter = DataModule.createMoshi().adapter(MetricsEventType::class.java)
  }

  @Test
  fun fromJson(@TestParameter type: MetricsEventType) {
    val result = adapter.fromJson(type.value.toString())

    assertThat(result).isEqualTo(type)
  }

  @Test
  fun toJson(@TestParameter type: MetricsEventType) {
    val result = adapter.toJson(type)

    assertThat(result).isEqualTo(type.value.toString())
  }
}
