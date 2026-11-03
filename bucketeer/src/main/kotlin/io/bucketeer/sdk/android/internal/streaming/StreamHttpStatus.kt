package io.bucketeer.sdk.android.internal.streaming

import io.bucketeer.sdk.android.internal.remote.CLIENT_CLOSED_REQUEST_CODE

/**
 * Retry rules for stream HTTP statuses.
 *
 * There are THREE groups, but only two are named as sets below. The third is the default,
 * defined by being in neither set (easy to miss when adding a status, so read this first).
 *
 * The stream connection's error handling checks them in this order:
 *
 *  1. RETRY FAST ([isRecoverable] returns true)
 *     Backoff retries (1s to 30s, with jitter). For failures that fix themselves in SECONDS:
 *     no response at all, any 5xx (including a 503 from the server's connection limit),
 *     408, 429 and 499.
 *
 *  2. RETRY SLOW (in NEITHER set: the default for any unlisted 4xx, for example 400, 413, 422)
 *     Give up on this attempt at once, fall back to polling, and try streaming again after the
 *     longer recovery interval. For failures that fix themselves in MINUTES, or only when the
 *     app acts (the request body changes on a later attempt).
 *
 *  3. NEVER RETRY ([isTerminal] returns true)
 *     Permanent for the life of this client (bad API key, streaming not served at this URL).
 *     Only destroy and initialize again brings streaming back.
 *
 * To pick a group, ask what would have to CHANGE for a retry to succeed, and how fast that
 * thing changes.
 *
 * WHY 400/413/422 ARE NEITHER: they are decided by the request BODY (user attributes, cache
 * state), which a fast retry of the same request cannot change, so group 1 would just resend
 * the same failing request. They are not terminal either: unlike a fixed API key, method or URL,
 * the body CAN differ on a later attempt (the app calls updateUserAttributes, or the cache
 * refreshes). The SDK cannot tell whether the backend objected to a fixed part of the request
 * or one that can change, so group 2 is the safe middle ground.
 */
internal object StreamHttpStatus {
  // 499 is the "client closed request" status sent during backend deployments. Polling already
  // retries it, so a rollout that polling survives must not stop the stream instead.
  private val RECOVERABLE_4XX = setOf(408, 429, CLIENT_CLOSED_REQUEST_CODE)

  // Decided by parts of the REQUEST that cannot change while this client runs (the API key, the
  // URL, the method or a fixed header), not by passing server state.
  private val TERMINAL =
    setOf(
      401, // Unauthorized: bad API key
      403, // Forbidden: bad API key
      404, // Not Found: same URL, will not appear on its own
      405, // Method Not Allowed: same method every request
      406, // Not Acceptable: same fixed Accept header every request
      410, // Gone: permanent by definition
      414, // URI Too Long: malformed URL, won't fix itself
      415, // Unsupported Media Type: same fixed Content-Type every request
      431, // Request Header Fields Too Large: same headers every request
      451, // Unavailable For Legal Reasons: permanent
    )

  /**
   * true: retry quickly with backoff. A null status means no HTTP response arrived (network
   * error), which is treated as a passing failure. Any status outside 4xx (5xx and anything
   * unusual) is recoverable too.
   */
  fun isRecoverable(status: Int?): Boolean = status == null || status !in 400..499 || status in RECOVERABLE_4XX

  /** true: retrying the same request can never succeed, so stop streaming for this client's lifetime. */
  fun isTerminal(status: Int?): Boolean = status != null && status in TERMINAL
}
