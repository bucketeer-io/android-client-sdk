package io.bucketeer.sdk.android.internal.util

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.contains(other: JSONObject): Boolean {
  for (key in other.keys()) {
    if (!this.has(key)) return false

    val valueThis = this.get(key)
    val valueOther = other.get(key)

    when {
      valueThis is JSONObject && valueOther is JSONObject -> {
        if (!valueThis.contains(valueOther)) return false
      }
      valueThis is JSONArray && valueOther is JSONArray -> {
        if (!valueThis.contains(valueOther)) return false
      }
      valueThis != valueOther -> return false
    }
  }
  return true
}

fun JSONArray.contains(other: JSONArray): Boolean {
  if (this.length() != other.length()) return false

  for (i in 0 until other.length()) {
    val valueThis = this.get(i)
    val valueOther = other.get(i)

    when {
      valueThis is JSONObject && valueOther is JSONObject -> {
        if (!valueThis.contains(valueOther)) return false
      }
      valueThis is JSONArray && valueOther is JSONArray -> {
        if (!valueThis.contains(valueOther)) return false
      }
      valueThis != valueOther -> return false
    }
  }
  return true
}
