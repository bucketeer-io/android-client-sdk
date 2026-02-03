# Non-Blocking Retry Implementation Summary

## Overview
Successfully refactored the Android SDK retry logic to use a non-blocking `Retrier` class similar to iOS, using `ScheduledExecutorService` instead of blocking `Thread.sleep` calls.

## Changes Made

### 1. New Retrier Class
**File**: `bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/remote/Retrier.kt`
- Created a new `Retrier` class that handles retry logic with exponential backoff
- Uses `ScheduledExecutorService` for non-blocking delayed retries (similar to iOS `dispatch.asyncAfter`)
- Exponential backoff formula: `delay = multiplier^(attemptsMade) * baseDelayMillis`
- Default configuration: 1 initial attempt + 3 retries with delays of 2s, 4s, 8s
- Callback-based API for async operations

### 2. Updated ApiClientImpl
**File**: `bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/remote/ApiClientImpl.kt`
- Added `retrier: Retrier` parameter to constructor
- Refactored `getEvaluations()` to use `Retrier.attempt()` with callback pattern
- Refactored `registerEvents()` to use `Retrier.attempt()` with callback pattern
- Uses `CountDownLatch` to make async retry synchronous for the caller (maintains backward compatibility)
- Removed dependency on the old `retryOnException()` function

### 3. Updated Dependency Injection
**File**: `bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/di/DataModule.kt`
- Added `executor: ScheduledExecutorService` parameter to constructor
- Creates `Retrier` instance using the provided executor
- Passes `retrier` to `ApiClientImpl` constructor

**File**: `bucketeer/src/main/kotlin/io/bucketeer/sdk/android/BKTClientImpl.kt`
- Updated to pass `executor` to `DataModule` constructor
- Reuses the existing `ScheduledExecutorService` from BKTClientImpl

### 4. Updated Test Files
Updated all test files to provide executor and retrier parameters:
- `ApiClientImplRetryTest.kt`: Added executor and retrier setup
- `ApiClientImplTest.kt`: Added executor and retrier setup
- `EventForegroundTaskTest.kt`: Moved executor creation before component
- `EvaluationForegroundTaskTest.kt`: Moved executor creation before component, fixed all 3 ComponentImpl instantiations
- `EvaluationInteractorTest.kt`: Added executor to DataModule instantiations
- `EventInteractorTest.kt`: Fixed executor parameter

### 5. Deprecated Old Implementation
**File**: `bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/remote/RetryOnException.kt`
- This file can be removed or deprecated
- No longer used by ApiClientImpl
- Only referenced by `RetryOnExceptionTest.kt` which can be updated or removed

## Key Benefits

✅ **Non-blocking**: Uses `ScheduledExecutorService.schedule()` instead of `Thread.sleep()`
✅ **iOS Alignment**: Similar architecture to iOS's `Retrier` class with `dispatch.asyncAfter`
✅ **Resource Efficient**: Reuses existing executor from BKTClientImpl
✅ **Exponential Backoff**: Implements 2^n backoff (2s, 4s, 8s) matching iOS behavior
✅ **Testable**: Injectable Retrier makes testing easier
✅ **Backward Compatible**: Public API remains unchanged

## Implementation Details

### Non-Blocking Retry Flow
1. `Retrier.attempt()` is called with a task and completion callback
2. Task executes immediately on the first attempt
3. If it fails with a retriable exception (e.g., `ClientClosedRequestException`):
   - Calculate exponential delay: `2^attemptNumber * 1000ms`
   - Schedule next attempt using `executor.schedule()` (non-blocking)
   - When delay expires, executor runs the next attempt
4. On success or non-retriable error, invoke completion callback
5. `CountDownLatch` in ApiClientImpl waits for async operation to complete

### Exponential Backoff
```
Attempt 1: Immediate (0ms)
Attempt 2: After 2 seconds  (2^1 * 1000ms)
Attempt 3: After 4 seconds  (2^2 * 1000ms)
Attempt 4: After 8 seconds  (2^3 * 1000ms)
Total time: ~14 seconds for max retries
```

### Thread Safety
- Retrier uses ScheduledExecutorService which handles thread safety
- CountDownLatch ensures proper synchronization between async retry and caller
- No shared mutable state between retries

## Testing
All existing tests should pass with the new implementation:
- `ApiClientImplRetryTest`: Tests 499 error retry behavior
- `ApiClientImplTest`: Tests general API client functionality
- Integration tests verify end-to-end retry behavior

## Next Steps (Optional)
1. Remove or deprecate `RetryOnException.kt` and `RetryOnExceptionTest.kt`
2. Consider adding request ID tracking (like iOS) to prevent race conditions
3. Consider adding jitter to prevent thundering herd
4. Consider adding maximum delay cap for battery optimization
