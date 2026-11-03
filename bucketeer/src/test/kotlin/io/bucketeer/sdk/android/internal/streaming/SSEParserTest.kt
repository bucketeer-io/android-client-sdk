package io.bucketeer.sdk.android.internal.streaming

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SSEParserTest {
  private fun bytes(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

  // One parser, fed every chunk in order.
  private fun parse(vararg chunks: ByteArray): List<SSEEvent> {
    val parser = SSEParser()
    return chunks.flatMap { parser.append(it) }
  }

  // Splits the UTF-8 bytes of text into chunks of at most size bytes.
  private fun chunked(
    text: String,
    size: Int,
  ): Array<ByteArray> =
    bytes(text)
      .toList()
      .chunked(size)
      .map { it.toByteArray() }
      .toTypedArray()

  @Test
  fun `named event is parsed with its data`() {
    val events = parse(bytes("event: put\ndata: {\"a\":1}\n\n"))

    assertThat(events).containsExactly(SSEEvent("put", "{\"a\":1}"))
  }

  @Test
  fun `block without event line gets the message name`() {
    val events = parse(bytes("data: plain\n\n"))

    assertThat(events).containsExactly(SSEEvent("message", "plain"))
  }

  @Test
  fun `empty event name falls back to message`() {
    val events = parse(bytes("event:\ndata: x\n\n"))

    assertThat(events).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `explicit message event keeps the message name`() {
    val events = parse(bytes("event: message\ndata: x\n\n"))

    assertThat(events).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `comments and the empty heartbeat emit nothing`() {
    val parser = SSEParser()

    assertThat(parser.append(bytes(":\n\n"))).isEmpty()
    assertThat(parser.append(bytes(": ping\n\n"))).isEmpty()
    // Positive control: the same parser still emits a real event afterwards.
    assertThat(parser.append(bytes("data: x\n\n"))).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `multiple data lines are joined with a newline`() {
    val events = parse(bytes("data: line1\ndata: line2\n\n"))

    assertThat(events).containsExactly(SSEEvent("message", "line1\nline2"))
  }

  @Test
  fun `CRLF framing parses the same as LF`() {
    val lf = parse(bytes("event: put\ndata: a\ndata: b\n\ndata: c\n\n"))
    val crlf = parse(bytes("event: put\r\ndata: a\r\ndata: b\r\n\r\ndata: c\r\n\r\n"))

    assertThat(lf)
      .containsExactly(SSEEvent("put", "a\nb"), SSEEvent("message", "c"))
      .inOrder()
    assertThat(crlf).isEqualTo(lf)
  }

  @Test
  fun `bare CR framing is supported`() {
    val events = parse(bytes("data: a\r\r"))

    assertThat(events).containsExactly(SSEEvent("message", "a"))
  }

  @Test
  fun `CRLF split across two chunks is one line break`() {
    val events = parse(bytes("data: a\r"), bytes("\ndata: b\r\n\r\n"))

    assertThat(events).containsExactly(SSEEvent("message", "a\nb"))
  }

  @Test
  fun `blank line split across two chunks still ends the block`() {
    val events = parse(bytes("data: a\n"), bytes("\ndata: b\n\n"))

    assertThat(events)
      .containsExactly(SSEEvent("message", "a"), SSEEvent("message", "b"))
      .inOrder()
  }

  @Test
  fun `mixed payload parses the same whole, in 3-byte and in 1-byte chunks`() {
    val payload =
      "event: put\ndata: {\"a\":1}\n\n" +
        "data: plain1\ndata: plain2\n\n" +
        "event: patch\r\ndata: {\"b\":2}\r\n\r\n" +
        ": heartbeat\n\n" +
        "event: evaluations\ndata: {\"c\":3}\n\n"

    val whole = parse(bytes(payload))
    val threeBytes = parse(*chunked(payload, 3))
    val oneByte = parse(*chunked(payload, 1))

    assertThat(whole)
      .containsExactly(
        SSEEvent("put", "{\"a\":1}"),
        SSEEvent("message", "plain1\nplain2"),
        SSEEvent("patch", "{\"b\":2}"),
        SSEEvent("evaluations", "{\"c\":3}"),
      ).inOrder()
    assertThat(threeBytes).isEqualTo(whole)
    assertThat(oneByte).isEqualTo(whole)
  }

  @Test
  fun `multi-byte UTF-8 split one byte at a time is decoded whole`() {
    val events = parse(*chunked("data: 日本€\n\n", 1))

    assertThat(events).containsExactly(SSEEvent("message", "日本€"))
  }

  @Test
  fun `event name is reset after each block`() {
    val events = parse(bytes("event: put\n\n"), bytes("data: x\n\n"))

    assertThat(events).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `unterminated block is not emitted until its blank line`() {
    val parser = SSEParser()

    assertThat(parser.append(bytes("data: x\n"))).isEmpty()
    assertThat(parser.append(bytes("\n"))).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `unknown fields are ignored`() {
    val events = parse(bytes("id: 1\nretry: 10\ndata\ndata: x\n\n"))

    assertThat(events).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `invalid UTF-8 does not throw`() {
    val chunk = bytes("data: a") + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + bytes("\n\n")

    val events = parse(chunk)

    assertThat(events).hasSize(1)
    assertThat(events[0].data).contains("�")
  }

  @Test
  fun `offset and length select a slice of the array`() {
    val before = bytes("data: before\n\n")
    val slice = bytes("data: x\n\n")
    val array = before + slice + bytes("data: after\n\n")

    val events = SSEParser().append(array, offset = before.size, length = slice.size)

    assertThat(events).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `offset without length reads to the end of the array`() {
    val before = bytes("data: before\n\n")
    val array = before + bytes("data: x\n\n")

    val events = SSEParser().append(array, offset = before.size)

    assertThat(events).containsExactly(SSEEvent("message", "x"))
  }

  @Test
  fun `invalid offset or length is rejected without changing parser state`() {
    val parser = SSEParser()
    val array = bytes("data: x\n\n")
    // Ends with a bare CR, so the parser is waiting to skip a following LF.
    assertThat(parser.append(bytes("data: a\r"))).isEmpty()

    assertThrows(IllegalArgumentException::class.java) { parser.append(array, 0, -1) }
    assertThrows(IllegalArgumentException::class.java) { parser.append(array, 0, array.size + 1) }
    assertThrows(IllegalArgumentException::class.java) { parser.append(array, -1, 1) }
    assertThrows(IllegalArgumentException::class.java) { parser.append(array, array.size + 1) }

    // Positive control: the pending CRLF and data line survived the rejected calls.
    assertThat(parser.append(bytes("\ndata: b\n\n"))).containsExactly(SSEEvent("message", "a\nb"))
  }

  @Test
  fun `empty chunk between CR and LF keeps one line break`() {
    val events = parse(bytes("data: a\r"), ByteArray(0), bytes("\ndata: b\n\n"))

    assertThat(events).containsExactly(SSEEvent("message", "a\nb"))
  }

  @Test
  fun `one megabyte data line in 1 KB chunks arrives whole`() {
    val data = "x".repeat(1024 * 1024)

    val events = parse(*chunked("data: $data\n\n", 1024))

    assertThat(events).hasSize(1)
    assertThat(events[0].data).isEqualTo(data)
  }

  @Test
  fun `only one leading space or tab is removed`() {
    val events = parse(bytes("data:  two\n\ndata:\tx\n\n"))

    assertThat(events)
      .containsExactly(SSEEvent("message", " two"), SSEEvent("message", "x"))
      .inOrder()
  }
}
