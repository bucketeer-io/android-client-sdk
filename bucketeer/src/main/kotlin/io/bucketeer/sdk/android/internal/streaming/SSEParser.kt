package io.bucketeer.sdk.android.internal.streaming

import java.io.ByteArrayOutputStream

/** One complete Server-Sent Event. */
internal data class SSEEvent(
  /** Value of the `event:` line, or "message" when the block has none (SSE standard default). */
  val name: String,
  /** All `data:` lines of the block, joined with "\n". */
  val data: String,
)

/**
 * Turns raw bytes from an SSE response into complete events.
 *
 * Not thread-safe: one parser per connection attempt, used from one thread.
 *
 * Buffers bytes, not text. A network chunk can end in the middle of a multi-byte UTF-8
 * character (for example the 3 bytes of "日"), and decoding such a partial chunk would turn
 * both halves into replacement characters. Line break bytes (0x0A, 0x0D) never appear inside a
 * multi-byte UTF-8 character, so the unfinished line is kept as bytes and decoded only once the
 * whole line has arrived. Each byte is looked at once, so a large payload arriving in many small
 * chunks is not rescanned.
 */
internal class SSEParser {
  /** Bytes of the current line, not yet terminated. */
  private val lineBuffer = ByteArrayOutputStream()

  /**
   * Set when the previous chunk ended with a bare "\r": a "\n" at the start of the next chunk
   * is the second half of that same CRLF and must be skipped, not read as an empty line.
   */
  private var skipNextLF = false

  /** `event:` value seen so far in the current block, if any. */
  private var eventName: String? = null

  /** `data:` lines seen so far in the current block. */
  private val dataLines = mutableListOf<String>()

  /** Feeds the next network chunk. Returns the events that became complete (often none). */
  fun append(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
  ): List<SSEEvent> {
    if (length == 0) return emptyList()

    val events = mutableListOf<SSEEvent>()
    val end = offset + length
    var index = offset

    if (skipNextLF) {
      skipNextLF = false
      if (bytes[index] == LF) index++
    }

    var lineStart = index
    while (index < end) {
      val byte = bytes[index]
      if (byte == LF || byte == CR) {
        lineBuffer.write(bytes, lineStart, index - lineStart)
        processLine()?.let { events.add(it) }
        lineBuffer.reset()

        var next = index + 1
        if (byte == CR) {
          if (next < end && bytes[next] == LF) {
            next++
          } else if (next == end) {
            // The "\r" is the last byte of this chunk: a "\n" at the start of the next chunk
            // belongs to this same line break.
            skipNextLF = true
          }
        }
        index = next
        lineStart = index
      } else {
        index++
      }
    }

    // Bytes after the last line break are an unfinished line, kept for the next chunk.
    if (lineStart < end) {
      lineBuffer.write(bytes, lineStart, end - lineStart)
    }
    return events
  }

  /**
   * Handles one complete line (without its line break). A blank line ends the block and returns
   * its event if the block had at least one `data:` line. Other lines update the current block.
   */
  private fun processLine(): SSEEvent? {
    if (lineBuffer.size() == 0) return endBlock()

    val line = lineBuffer.toByteArray()
    // A line starting with ":" is a comment (the server's heartbeat).
    if (line[0] == COLON) return null

    // Invalid bytes become U+FFFD instead of throwing; the later JSON decode rejects them.
    val text = String(line, Charsets.UTF_8)
    val data = fieldValue(text, "data:")
    if (data != null) {
      dataLines.add(data)
    } else {
      val name = fieldValue(text, "event:")
      if (name != null) eventName = name.ifEmpty { null }
    }
    // Any other field (id:, retry:, or a line without a colon) is ignored: the backend sends none.
    return null
  }

  private fun endBlock(): SSEEvent? {
    val event =
      if (dataLines.isEmpty()) {
        null
      } else {
        SSEEvent(name = eventName ?: DEFAULT_EVENT_NAME, data = dataLines.joinToString("\n"))
      }
    eventName = null
    dataLines.clear()
    return event
  }

  /**
   * Returns the value after [field] (for example "data:") with one leading space or tab removed,
   * as the SSE standard says, or null when [line] does not start with [field].
   */
  private fun fieldValue(
    line: String,
    field: String,
  ): String? {
    if (!line.startsWith(field)) return null
    val value = line.substring(field.length)
    return if (value.startsWith(' ') || value.startsWith('\t')) value.substring(1) else value
  }

  companion object {
    const val DEFAULT_EVENT_NAME = "message"

    private const val LF: Byte = 0x0A // "\n"
    private const val CR: Byte = 0x0D // "\r"
    private const val COLON: Byte = 0x3A // ":"
  }
}
