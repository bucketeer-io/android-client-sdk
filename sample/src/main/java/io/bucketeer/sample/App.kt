package io.bucketeer.sample

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleObserver
import com.facebook.stetho.Stetho
import io.bucketeer.sdk.android.BKTClient
import io.bucketeer.sdk.android.BKTConfig
import io.bucketeer.sdk.android.BKTUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class App : Application(), LifecycleObserver {

  private val sharedPref by lazy {
    getSharedPreferences(
      Constants.PREFERENCE_FILE_KEY,
      Context.MODE_PRIVATE,
    )
  }

  override fun onCreate() {
    super.onCreate()
    Stetho.initializeWithDefaults(this)
    initBucketeer()
  }

  private fun initBucketeer() {
    val config = BKTConfig.builder()
      .apiKey(BuildConfig.API_KEY)
      .endpoint(BuildConfig.API_URL)
      .featureTag(getTag())
      .eventsMaxQueueSize(10)
      .pollingInterval(TimeUnit.SECONDS.toMillis(20))
      .backgroundPollingInterval(TimeUnit.SECONDS.toMillis(60))
      .eventsFlushInterval(TimeUnit.SECONDS.toMillis(20))
      .build()

    val user = BKTUser.builder()
      .id(getUserId())
      .build()

    val future = BKTClient.initialize(this, config, user)

    MainScope().launch {
      val result = withContext(Dispatchers.IO) {
        future.get()
      }
      println("result: $result")
      Toast.makeText(this@App, "User Evaluations has been updated", Toast.LENGTH_LONG).show()
    }
  }

  private fun getTag(): String {
    return sharedPref.getString(
      Constants.PREFERENCE_KEY_TAG,
      Constants.DEFAULT_TAG,
    ) ?: Constants.DEFAULT_TAG
  }

  private fun getUserId(): String {
    return sharedPref.getString(
      Constants.PREFERENCE_KEY_USER_ID,
      Constants.DEFAULT_USER_ID,
    ) ?: Constants.DEFAULT_USER_ID
  }

  companion object {
    internal const val TAG = "BucketeerSample"
  }
}
