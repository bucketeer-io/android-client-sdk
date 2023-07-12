package io.bucketeer.sdk.android.internal.database.migration

import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import io.bucketeer.sdk.android.internal.Constants
import io.bucketeer.sdk.android.internal.evaluation.db.EvaluationEntity
import io.bucketeer.sdk.android.internal.event.EventEntity

class Migration2to3 : Migration {

  override fun migrate(db: SupportSQLiteDatabase, sharedPreferences: SharedPreferences) {
    // Due to changes in the API endpoint, we must delete all the data stored in the SQLite because
    // the data model has changed from snake case to camel case.
    // Reference: https://github.com/bucketeer-io/android-client-sdk/pull/63
    db.delete(EvaluationEntity.TABLE_NAME, null, null)
    db.delete(EventEntity.TABLE_NAME, null, null)

    // We also must delete the user evaluation id to force the server returns all the evaluations again.
    // Otherwise, it won't send the evaluations until the user changes the flag on the admin console.
    sharedPreferences.edit().remove(Constants.PREFERENCE_KEY_USER_EVALUATION_ID).commit()
  }
}
