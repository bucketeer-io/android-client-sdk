package io.bucketeer.sdk.android.internal.evaluation.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import io.bucketeer.sdk.android.internal.database.createDatabase
import io.bucketeer.sdk.android.internal.database.getString
import io.bucketeer.sdk.android.internal.database.select
import io.bucketeer.sdk.android.internal.di.DataModule
import io.bucketeer.sdk.android.internal.model.Evaluation
import io.bucketeer.sdk.android.mocks.evaluation1
import io.bucketeer.sdk.android.mocks.evaluation2
import io.bucketeer.sdk.android.mocks.evaluation3
import io.bucketeer.sdk.android.mocks.user1
import io.bucketeer.sdk.android.mocks.user2
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EvaluationDaoImplTest {

  private lateinit var dao: EvaluationDaoImpl
  private lateinit var openHelper: SupportSQLiteOpenHelper
  private lateinit var moshi: Moshi

  @Before
  fun setup() {
    moshi = DataModule.createMoshi()
    openHelper = createDatabase(ApplicationProvider.getApplicationContext())

    dao = EvaluationDaoImpl(openHelper, moshi)
  }

  @After
  fun tearDown() {
    openHelper.close()
  }

  @Test
  fun `put - insert`() {
    dao.put(user1.id, listOf(evaluation1))

    val c = getEvaluations()

    c.use {
      it.moveToFirst()

      assertThat(it.getString(EvaluationEntity.COLUMN_FEATURE_ID))
        .isEqualTo(evaluation1.feature_id)

      val jsonStr = it.getString(EvaluationEntity.COLUMN_EVALUATION)
      val evaluation = moshi.adapter(Evaluation::class.java).fromJson(jsonStr)
      assertThat(evaluation).isEqualTo(evaluation1)

      assertThat(it.moveToNext()).isFalse()
    }
  }

  @Test
  fun `put - update`() {
    val sourceEvaluation = evaluation1
    val updatedValue = "updated value"
    val updatedEvaluation = sourceEvaluation.copy(
      variation_value = updatedValue,
      variation = sourceEvaluation.variation.copy(value = updatedValue),
    )

    dao.put(user1.id, listOf(sourceEvaluation))

    val beforeC = getEvaluations()
    beforeC.use {
      it.moveToFirst()

      assertThat(it.getString(EvaluationEntity.COLUMN_FEATURE_ID))
        .isEqualTo(evaluation1.feature_id)

      val jsonStr = it.getString(EvaluationEntity.COLUMN_EVALUATION)
      val evaluation = moshi.adapter(Evaluation::class.java).fromJson(jsonStr)
      assertThat(evaluation).isEqualTo(sourceEvaluation)

      assertThat(it.moveToNext()).isFalse()
    }

    dao.put(user1.id, listOf(updatedEvaluation))

    val afterC = getEvaluations()
    afterC.use {
      it.moveToFirst()

      assertThat(it.getString(EvaluationEntity.COLUMN_FEATURE_ID))
        .isEqualTo(evaluation1.feature_id)

      val jsonStr = it.getString(EvaluationEntity.COLUMN_EVALUATION)
      val evaluation = moshi.adapter(Evaluation::class.java).fromJson(jsonStr)
      assertThat(evaluation).isEqualTo(updatedEvaluation)

      assertThat(it.moveToNext()).isFalse()
    }
  }

  @Test
  fun `get - empty if target user has no item`() {
    val actual = dao.get(user1.id)

    assertThat(actual).isEmpty()
  }

  @Test
  fun `get - single item`() {
    dao.put(user1.id, listOf(evaluation1))

    val actual = dao.get(user1.id)

    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(evaluation1)
  }

  @Test
  fun `get - multiple item`() {
    dao.put(user1.id, listOf(evaluation1, evaluation2))

    val actual = dao.get(user1.id)

    assertThat(actual).hasSize(2)
    assertThat(actual[0]).isEqualTo(evaluation1)
    assertThat(actual[1]).isEqualTo(evaluation2)
  }

  @Test
  fun `deleteAllAndInsert - insert`() {
    dao.deleteAllAndInsert(user1.id, listOf(evaluation1))

    val actual = dao.get(user1.id)

    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(evaluation1)
  }

  @Test
  fun `deleteAllAndInsert - delete old items`() {
    dao.deleteAllAndInsert(user1.id, listOf(evaluation1))
    dao.deleteAllAndInsert(user1.id, listOf(evaluation2))

    val actual = dao.get(user1.id)

    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(evaluation2)
  }

  @Test
  fun `deleteAll - should not update other user's item`() {
    dao.deleteAllAndInsert(user1.id, listOf(evaluation1))
    dao.deleteAllAndInsert(user2.id, listOf(evaluation3))

    val actual1 = dao.get(user1.id)
    val actual2 = dao.get(user2.id)

    assertThat(actual1).hasSize(1)
    assertThat(actual1[0]).isEqualTo(evaluation1)

    assertThat(actual2).hasSize(1)
    assertThat(actual2[0]).isEqualTo(evaluation3)
  }

  private fun getEvaluations(): Cursor {
    val columns = arrayOf(
      EvaluationEntity.COLUMN_FEATURE_ID,
      EvaluationEntity.COLUMN_USER_ID,
      EvaluationEntity.COLUMN_EVALUATION,
    )
    return openHelper.readableDatabase.select(
      EvaluationEntity.TABLE_NAME,
      columns,
    )
  }
}
