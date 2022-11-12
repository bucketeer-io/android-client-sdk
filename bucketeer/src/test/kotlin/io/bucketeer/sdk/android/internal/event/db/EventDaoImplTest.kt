package io.bucketeer.sdk.android.internal.event.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.mocks.evaluationEvent1
import io.bucketeer.sdk.android.mocks.evaluationEvent2
import io.bucketeer.sdk.android.mocks.goalEvent1
import io.bucketeer.sdk.android.mocks.metricsEvent1
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventDaoImplTest {
  private lateinit var dao: EventDaoImpl
  private lateinit var openHelper: SupportSQLiteOpenHelper
  private lateinit var moshi: Moshi

  @Before
  fun setup() {
    moshi = DataModule.createMoshi()
    openHelper = createDatabase(ApplicationProvider.getApplicationContext())

    dao = EventDaoImpl(openHelper, moshi)
  }

  @After
  fun tearDown() {
    openHelper.close()
  }

  @Test
  fun `addEvent - goal`() {
    dao.addEvent(goalEvent1)

    val actual = dao.getEvents()

    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(goalEvent1)
  }

  @Test
  fun `addEvent - evaluation`() {
    dao.addEvent(evaluationEvent1)

    val actual = dao.getEvents()

    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(evaluationEvent1)
  }

  @Test
  fun `addEvent - metrics`() {
    dao.addEvent(metricsEvent1)

    val actual = dao.getEvents()

    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(metricsEvent1)
  }

  @Test
  fun addEvents() {
    dao.addEvents(listOf(evaluationEvent1, goalEvent1, metricsEvent1, evaluationEvent2))

    val actual = dao.getEvents()

    assertThat(actual).hasSize(4)
    assertThat(actual).containsExactly(
      evaluationEvent1,
      goalEvent1,
      metricsEvent1,
      evaluationEvent2,
    )
  }

  @Test
  fun `delete - all`() {
    val target = listOf(evaluationEvent1, goalEvent1, metricsEvent1, evaluationEvent2)
    dao.addEvents(target)

    val ids = target.map { it.id }

    dao.delete(ids)

    val actual = dao.getEvents()

    assertThat(actual).isEmpty()
  }

  @Test
  fun `delete - some items`() {
    val target = listOf(evaluationEvent1, goalEvent1, metricsEvent1, evaluationEvent2)
    dao.addEvents(target)

    val ids = listOf(evaluationEvent1.id, metricsEvent1.id)

    dao.delete(ids)

    val actual = dao.getEvents()

    assertThat(actual).hasSize(2)

    assertThat(actual[0]).isEqualTo(goalEvent1)
    assertThat(actual[1]).isEqualTo(evaluationEvent2)
  }

  @Test
  fun `getEvents should update sdk_version to the current`() {
    val oldVersion = "0.1.0"
    val newVersion = BuildConfig.SDK_VERSION

    val targets = listOf(
      evaluationEvent1.copy(event = updateSdkVersion(evaluationEvent1.event, oldVersion)),
      goalEvent1.copy(event = updateSdkVersion(goalEvent1.event, oldVersion)),
      metricsEvent1.copy(event = updateSdkVersion(metricsEvent1.event, oldVersion)),
    )

    dao.addEvents(targets)

    val actual = dao.getEvents()

    val versions = actual.map { it.event }
      .map {
        when (it) {
          is EventData.EvaluationEvent -> it.sdk_version
          is EventData.GoalEvent -> it.sdk_version
          is EventData.MetricsEvent -> it.sdk_version
        }
      }

    assertThat(versions).containsExactly(newVersion, newVersion, newVersion)
  }

  private fun updateSdkVersion(eventData: EventData, newSdkVersion: String): EventData {
    return when (eventData) {
      is EventData.EvaluationEvent -> eventData.copy(sdk_version = newSdkVersion)
      is EventData.GoalEvent -> eventData.copy(sdk_version = newSdkVersion)
      is EventData.MetricsEvent -> eventData.copy(sdk_version = newSdkVersion)
    }
  }
}
