//uncomment for test FCM realtime update

//package io.bucketeer.sample
//
//import android.util.Log
//import com.google.firebase.messaging.FirebaseMessagingService
//import com.google.firebase.messaging.RemoteMessage
//import io.bucketeer.sdk.android.BKTClient
//
//class MyFirebaseMessagingService : FirebaseMessagingService() {
//  // [START receive_message]
//  override fun onMessageReceived(remoteMessage: RemoteMessage) {
//    Log.d(TAG, "From: ${remoteMessage.from}")
//
//    remoteMessage.data.also { data ->
//      Log.d(TAG, "Message data payload: ${remoteMessage.data}")
//      val isFeatureFlagUpdated = data["bucketeer_feature_flag_updated"]
//      if (isFeatureFlagUpdated == "true") {
//        // Feature flag changed
//        Log.d(TAG, "Bucketeer feature flag changed")
//
//        runCatching {
//          // Make sure BKTClient has been initialize before access it
//          val client = BKTClient.getInstance()
//          val future = client.fetchEvaluations(3000)
//          val error = future.get()
//          if (error == null) {
//            val showNewFeature = client.stringVariation(Constants.FCM_FEATURE_FLAG_ID, "")
//            Log.d(TAG, "Bucketeer feature flag new value: $showNewFeature")
//          } else {
//            // Handle the error
//          }
//        }
//      }
//    }
//
//    remoteMessage.notification?.let {
//      Log.d(TAG, "Message Notification Body: ${it.body}")
//    }
//  }
//
//  override fun onNewToken(token: String) {
//    Log.d(TAG, "Refreshed token: $token")
//  }
//
//  companion object {
//    private const val TAG = "MyFirebaseMsgService"
//  }
//}
