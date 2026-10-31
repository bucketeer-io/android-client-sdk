# E2E tests

These tests run the SDK on a device or emulator against a real Bucketeer backend. The `e2e` workflow (`.github/workflows/e2e.yml`) runs them on CI, split into shards.

## Running them

```bash
./gradlew :bucketeer:connectedDebugAndroidTest
```

Settings, read from `local.properties` or the environment:

| `local.properties` | Environment (CI secret) | Used by |
|---|---|---|
| `api_endpoint` | `API_ENDPOINT` (`E2E_API_ENDPOINT`) | Every E2E test |
| `api_key` | `API_KEY` (`E2E_API_KEY`) | Every E2E test (client key) |
| `public_api_key` | `PUBLIC_API_KEY` (`E2E_PUBLIC_API_KEY`) | The streaming patch tests only: a key that can update flags through `PATCH /v1/feature` |

Backend setup the tests expect, for the `android` tag:

- `feature-android-e2e-string`: default `value-1`; a rule that serves `value-2` (reason `RULE`) when the user's `app_version` attribute is `0.0.1`.
- `feature-android-e2e-sse-string` and `feature-android-e2e-sse-direct-string`: string flags written only by the streaming patch tests, one flag per test (proxy and direct). Each run writes a fresh value, and no test reads a previous value back, so they are never restored.

## Streaming tests

`BKTClientStreamingProxyTests` and `BKTClientStreamingDirectTests` cover the stream (`BKTConfig.Builder.enableStreaming(true)`): flag updates pushed by the backend over Server-Sent Events.

### What they check

1. The stream opens after `initialize`.
2. When the initial fetch times out, the evaluations still arrive, over the stream.
3. After `updateUserAttributes`, the stream reconnects and the new attributes are evaluated over it (`value-2`, reason `RULE`, the listener sees `value-2`).
4. A flag change made on the backend reaches the open stream.

### Why the tests need to see the network

With streaming on, a value can reach the app in two ways: over the stream, or through a REST `get_evaluations` fetch (the initial fetch, or the polling that starts at once when the stream fails). An app sees the same value either way. To prove the stream delivered it, a test has to count the `get_evaluations` requests: exactly one (the initial fetch) means nothing else fetched.

`BKTConfig` has no way to plug in an HTTP client, and records the SDK writes about itself don't prove anything happened on the network:

- **Metrics events**: the SDK drops a `get_evaluations` metrics event when one is already stored, so two fetches can leave one event.
- **Log lines**: a log line shows the SDK said something happened, not that it happened.

### The proxy server

`RecordingProxy` is a small HTTP server that the test starts on `127.0.0.1`. The SDK is pointed at it through the public `apiEndpoint` setting. The proxy forwards every request to the real backend, passes each reply back as it arrives (the stream piece by piece), and records each request's path and reply status.

A stream counts as open only while its reply is 200, with no error, and has not ended. A stream the backend ended, or one the SDK closed to reconnect, no longer counts.

This keeps the client exactly as an app gets it: it is created by the public `BKTClient.initialize`, and nothing inside it is replaced. Only its address changes.

The proxy lives in `bucketeer/src/sharedTest`, shared by the unit and instrumented tests. Its own tests, `RecordingProxyTest`, run in the unit suite against MockWebServer as the backend, so they need no keys. The E2E tests never use a fake backend.

The test APK allows plain HTTP to `127.0.0.1` only (`src/androidTest/res/xml/e2e_network_security_config.xml`). The published library is not affected.

### Two classes

| | `BKTClientStreamingProxyTests` | `BKTClientStreamingDirectTests` |
|---|---|---|
| Connects to | The proxy, which forwards to the backend | The backend directly |
| Checks | Values, plus the request count and the stream's reply | Only what an app can see: values, reasons, listener calls |
| Tests | 1, 2, 3, 4 | 3, 4 |
| Cannot see | The SDK's own connection to the real gateway: through the proxy the SDK talks plain HTTP to a local server, which forwards over its own connection, so TLS, HTTP/2 and the gateway's chunking are not exercised | Where a value came from. If the stream failed at once, the polling fallback would fetch and these tests could still pass; the proxy class catches that |

The two classes cover each other's blind spots. Tests 3 and 4 are copied between them on purpose, so each class reads on its own.

Tests 1 and 2 have no direct version: an app cannot see the stream open, and the request count is what proves test 2.

### Known limits

- **The direct attribute test can pass without the reconnect.** It cannot wait for the stream to open, so its attribute update often comes first, and the stream then opens with the new attributes anyway. The proxy version waits for the stream first and counts a second stream request.
- **The direct patch test can race the stream opening.** A flag change written before the stream opens may not arrive, and the test fails with its own message. The proxy version waits for the stream's reply first.
- **The direct class can pass through the polling fallback** (see the table). The proxy class checks the same cases with the request count.
- **Through the proxy, replies have no `Content-Length`** (the proxy ends each reply by closing the connection), so the SDK's response-size metric is -1 in proxy runs.

### Timing

Each wait has its own timeout and failure message (`StreamingTimeout`): 20 s for the stream to open, 20 s for a value, 10 s for the test's own flag update. With streaming off the E2E config polls every 10 minutes, so no wait can pass through a poll.

### App lifecycle

The tests start no activity, so the app never reaches the foreground on its own, and the SDK opens the stream only in the foreground. The streaming classes set `ProcessLifecycleOwner` to STARTED before each test and back to CREATED after it.

### Shared state

Each test runs in its own process (the test orchestrator, with `clearPackageData`), and the streaming classes also clear the database and preferences after each test. The proxy class starts a new proxy for each test and stops it after `BKTClient.destroy()`.

The E2E backend is shared between CI runs, and the patch tests write flags on it. An update listener reads the current value only when it is called, so two writes to the same flag close together can hide one of the values. The `e2e` workflow therefore runs in one concurrency group (`android-e2e-backend`): one run at a time, every other run waits its turn, and none is cancelled. Within one run, the two patch tests run at the same time in different shards, so each writes its own flag.
