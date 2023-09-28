package io.bucketeer.sdk.android.internal.evaluation.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.bucketeer.sdk.android.internal.Constants
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EvaluationSharedPrefsImplTest {

  @Test
  fun testGetAndSetData() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val sharedPreferences = context.getSharedPreferences(
      Constants.PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    )
    val sharedPrefsDao = EvaluationSharedPrefsImpl(sharedPreferences)
    sharedPrefsDao.evaluatedAt = "1000"
    sharedPrefsDao.currentEvaluationsId = "test01"
    sharedPrefsDao.featureTag = "tag1"
    sharedPrefsDao.userAttributesUpdated = true

    assert(sharedPrefsDao.evaluatedAt == "1000")
    assert(sharedPrefsDao.currentEvaluationsId == "test01")
    assert(sharedPrefsDao.featureTag == "tag1")
    assert(sharedPrefsDao.userAttributesUpdated)

    sharedPrefsDao.evaluatedAt = "1001"
    sharedPrefsDao.currentEvaluationsId = "test02"
    sharedPrefsDao.featureTag = "tag2"
    sharedPrefsDao.userAttributesUpdated = false

    assert(sharedPrefsDao.evaluatedAt == "1001")
    assert(sharedPrefsDao.currentEvaluationsId == "test02")
    assert(sharedPrefsDao.featureTag == "tag2")
    assert(sharedPrefsDao.userAttributesUpdated.not())
  }
}
