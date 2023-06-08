package io.bucketeer.sdk.android.internal.event

import com.google.common.truth.Truth
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import io.bucketeer.sdk.android.internal.model.Event
import io.bucketeer.sdk.android.internal.model.metricsEventUniqueKey
import io.bucketeer.sdk.android.mocks.internalErrorMetricsEvent1
import io.bucketeer.sdk.android.mocks.metricsEvent1
import io.bucketeer.sdk.android.mocks.sizeMetricsEvent1
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class MetricEventsUniqueKeyTest {

  @Suppress("unused")
  enum class ErrorTestCase(
    val input: Event,
    val expected: String,
  ) {
    ResponseLatency( metricsEvent1, "GET_EVALUATIONS::type.googleapis.com/bucketeer.event.client.LatencyMetricsEvent"),
    ResponseSize(sizeMetricsEvent1, "GET_EVALUATIONS::type.googleapis.com/bucketeer.event.client.SizeMetricsEvent"),
    InternalError(internalErrorMetricsEvent1, "GET_EVALUATIONS::type.googleapis.com/bucketeer.event.client.InternalServerErrorMetricsEvent"),
  }

  @Test
  fun toMetricEventType(@TestParameter case: ErrorTestCase) {
    val result = case.input.metricsEventUniqueKey()
    Truth.assertThat(result).isEqualTo(case.expected)
  }
}
