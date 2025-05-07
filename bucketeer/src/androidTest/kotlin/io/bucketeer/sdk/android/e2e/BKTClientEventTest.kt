package io.bucketeer.sdk.android.e2e

import android.content.Context
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTClientImpl
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTUser
import io.bucketeer.sdk.android.BKTValue
import io.bucketeer.sdk.android.BuildConfig
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.database.OpenHelperCallback
import io.bucketeer.sdk.android.internal.di.ComponentImpl
import io.bucketeer.sdk.android.internal.model.EventData
import io.bucketeer.sdk.android.internal.model.EventType
import io.bucketeer.sdk.android.internal.model.ReasonType
import io.bucketeer.sdk.android.internal.model.SourceID
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BKTClientEventTest {
  private lateinit var context: Context
  private lateinit var config: BKTConfig
  private lateinit var user: BKTUser

  @Before
  @UiThreadTest
  fun setup() {
    context = ApplicationProvider.getApplicationContext()

    config =
      BKTConfig
        .builder()
        .apiKey(BuildConfig.API_KEY)
        .apiEndpoint(BuildConfig.API_ENDPOINT)
        .featureTag(FEATURE_TAG)
        .appVersion("1.2.3")
        .build()

    user =
      BKTUser
        .builder()
        .id(USER_ID)
        .build()

    val result = BKTClient.initialize(context, config, user).get()
    assertThat(result).isNull()
  }

  @After
  @UiThreadTest
  fun tearDown() {
    BKTClient.destroy()
    context.deleteDatabase(OpenHelperCallback.FILE_NAME)
    context
      .getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun track() {
    val client = BKTClient.getInstance() as BKTClientImpl
    val eventDao = (client.component as ComponentImpl).dataModule.eventSQLDao

    Thread.sleep(100)

    assertThat(eventDao.getEvents()).hasSize(2)

    client.track(GOAL_ID, GOAL_VALUE)

    Thread.sleep(100)

    assertThat(eventDao.getEvents()).hasSize(3)

    val result = client.flush().get()

    assertThat(result).isNull()

    assertThat(eventDao.getEvents()).isEmpty()
  }

  @Test
  fun testEvaluationEvents() {
    val client = BKTClient.getInstance() as BKTClientImpl
    assertThat(client.stringVariation(FEATURE_ID_STRING, "test")).isEqualTo("value-1")
    assertThat(client.intVariation(FEATURE_ID_INT, 0)).isEqualTo(10)
    assertThat(client.doubleVariation(FEATURE_ID_DOUBLE, 0.1)).isEqualTo(2.1)
    assertThat(client.intVariation(FEATURE_ID_DOUBLE, 20)).isEqualTo(2)
    assertThat(client.booleanVariation(FEATURE_ID_BOOLEAN, false)).isEqualTo(true)
    client.jsonVariation(FEATURE_ID_JSON, JSONObject()).let { json ->
      val keys = json.keys().asSequence().toList()
      val values = keys.map { json.get(it) }
      assertThat(keys).isEqualTo(listOf("key"))
      assertThat(values).isEqualTo(listOf("value-1"))
    }
    client.objectVariation(FEATURE_ID_JSON, BKTValue.Structure(mapOf())).let { obj ->
      assertThat(obj).isEqualTo(BKTValue.Structure(mapOf("key" to BKTValue.String("value-1"))))
    }

    val eventDao = (client.component as ComponentImpl).dataModule.eventSQLDao

    Thread.sleep(2000)
    val events = eventDao.getEvents()
    assertThat(events).hasSize(9)
    assertThat(
      events.any {
        val type = it.type
        val event = it.event
        return@any type == EventType.EVALUATION &&
          event is EventData.EvaluationEvent &&
          event.reason.type == ReasonType.DEFAULT &&
          event.sourceId == SourceID.ANDROID &&
          event.sdkVersion == BuildConfig.SDK_VERSION
      },
    ).isTrue()

    val result = client.flush().get()
    assertThat(result).isNull()
    assertThat(eventDao.getEvents()).isEmpty()
  }

  @Test
  fun testDefaultEvaluationEvents() {
    val client = BKTClient.getInstance() as BKTClientImpl
    val evaluationStorage = (client.component as ComponentImpl).dataModule.evaluationStorage
    evaluationStorage.deleteAllAndInsert("", listOf(), "0")

    assertThat(client.stringVariation(FEATURE_ID_STRING, "test")).isEqualTo("test")
    assertThat(client.intVariation(FEATURE_ID_INT, 0)).isEqualTo(0)
    assertThat(client.doubleVariation(FEATURE_ID_DOUBLE, 0.1)).isEqualTo(0.1)
    assertThat(client.booleanVariation(FEATURE_ID_BOOLEAN, false)).isEqualTo(false)
    client.jsonVariation(FEATURE_ID_JSON, JSONObject()).let { json ->
      val keys = json.keys().asSequence().toList()
      val values = keys.map { json.get(it) }
      assertThat(keys).isEqualTo(listOf<String>())
      assertThat(values).isEqualTo(listOf<String>())
    }
    client.objectVariation(FEATURE_ID_JSON, BKTValue.Structure(mapOf())).let { obj ->
      assertThat(obj).isEqualTo(BKTValue.Structure(mapOf()))
    }

    val eventDao = client.component.dataModule.eventSQLDao

    Thread.sleep(2000)
    val events = eventDao.getEvents()
    assertThat(events).hasSize(8)
    assertThat(
      events.any {
        val type = it.type
        val event = it.event
        return@any type == EventType.EVALUATION &&
          event is EventData.EvaluationEvent &&
          event.reason.type == ReasonType.CLIENT
      },
    ).isTrue()

    val result = client.flush().get()
    assertThat(result).isNull()
    assertThat(eventDao.getEvents()).isEmpty()
  }
}
