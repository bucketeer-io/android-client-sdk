package io.bucketeer.sample

import android.app.Application
import androidx.lifecycle.LifecycleObserver
import com.facebook.stetho.Stetho

class App :
  Application(),
  LifecycleObserver {
  override fun onCreate() {
    super.onCreate()
    Stetho.initializeWithDefaults(this)
  }

  companion object {
    internal const val TAG = "BucketeerSample"
  }
}
