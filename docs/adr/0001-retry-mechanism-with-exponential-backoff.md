# ADR 0001: Retry Mechanism with Exponential Backoff

## Status
Accepted

## Context
The Android SDK needs to handle transient HTTP failures, specifically 499 status codes (Client Closed Request), which can occur when clients cancel requests or when there are network issues. Previously, the SDK used linear backoff for retries, which was inconsistent with the iOS SDK's exponential backoff approach.

## Decision
We will implement a retry mechanism with exponential backoff that:

1. **Retry Strategy**: Exponential backoff with base delay of 1 second and multiplier of 2
   - First retry: 2 seconds delay
   - Second retry: 4 seconds delay
   - Third retry: 8 seconds delay
   - Maximum 3 retries (4 total attempts)

2. **Retry Condition**: Only retry on HTTP 499 status codes

3. **Request Tracking**: Implement request ID tracking to prevent race conditions where stale responses could overwrite newer data

4. **Cross-Platform Consistency**: Match the iOS SDK implementation exactly for consistent behavior across platforms

## Consequences

### Positive
- **Better Server Protection**: Exponential backoff reduces load on servers during outages compared to linear backoff
- **Cross-Platform Consistency**: iOS and Android SDKs now have identical retry behavior (14 seconds total retry time)
- **Race Condition Prevention**: Request ID tracking ensures that only the latest request's response is processed
- **Improved Reliability**: Handles transient 499 errors gracefully without overwhelming the server

### Negative
- **Longer Wait Times**: Users may wait up to 14 seconds (vs. previous 6 seconds with linear backoff) for all retries to complete
- **Increased Complexity**: Request ID tracking adds complexity to the API client implementation
- **Breaking Change**: Applications relying on the previous 6-second timeout behavior may need adjustment

### Neutral
- **Testing Requirements**: Requires comprehensive unit tests for retry logic and race condition scenarios
- **Documentation**: Need to clearly document retry behavior for SDK users

## Implementation Notes

### Key Components
1. **Retrier**: Stateless utility class handling retry logic with exponential backoff
2. **ApiClientImpl**: Updated to use Retrier and track request IDs per API endpoint
3. **Request ID Tracking**: Thread-safe storage of current request IDs using ReentrantReadWriteLock

### Alignment with iOS
The implementation matches the iOS SDK:
- Same exponential backoff formula: `2^attemptsMade * 1 second`
- Same maximum attempts: 4 (1 initial + 3 retries)
- Same retry condition: HTTP 499 only
- Same request cancellation mechanism via request IDs

## Alternatives Considered

### 1. Keep Linear Backoff
**Rejected**: Would maintain cross-platform inconsistency and is less server-friendly during outages.

### 2. Configurable Backoff Strategy
**Deferred**: While valuable for testing, adding configuration complexity was deemed unnecessary for the initial implementation. Can be added later if needed.

### 3. Add Jitter to Backoff
**Deferred**: While jitter helps prevent thundering herd problems, it was not in the iOS implementation. Can be added to both SDKs in a future update for consistency.

### 4. Shorter Maximum Delay
**Rejected**: Would break cross-platform consistency. The 14-second total delay is acceptable for handling transient failures.

## References
- iOS Implementation: https://github.com/bucketeer-io/ios-client-sdk/compare/main...feat/retry-on-499-status-code
- Related Issue: [Link to issue if applicable]
- Code Review: CODE_REVIEW_c2a6537.md

