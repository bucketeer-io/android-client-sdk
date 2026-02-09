# Implementation Plan - Non-Blocking Retry for 499

## User Review Required
> [!IMPORTANT]
> **Decision Point**: You asked "What if we follow what iOS did (Network Layer Only)?"
> 
> **The Problem**: Android's `ApiClient` is **Synchronous** (`return Result`). iOS's is **Asynchronous** (callback).
> - To "fix it in the network layer" like iOS, we must change `ApiClient` from Synchronous to Asynchronous.
> - This forces `EvaluationInteractor`, `EventInteractor`, and `BKTClientImpl` to also become Asynchronous.
> - This is a **Massive Refactor** (changing function signatures across the entire SDK).

## Strategy: Shared Async Retry with Cancellation (Stateless Interactors)
To satisfy user request for a wrapper class, consistent behavior, and keeping Interactors stateless:
1. **New Component**: `NetworkCancellationRunner`. Encapsulates the "Check ID -> Run -> Schedule Retry" loop AND holds the `currentRequestId` state.
2. **Component Integration**: `Component` will expose two runners: `evaluationCancellationRunner` and `eventCancellationRunner`.
3. **Consistency**: Both `fetchEvaluations` and `flush` use their respective runners.

## Proposed Changes
### Helper Logic
#### [NEW] [SettableFuture.kt](file:///Users/ryan/dev/Projects/bucketeer-io/android-client-sdk/bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/util/SettableFuture.kt)
- Create `SettableFuture` for manual completion.

#### [NEW] [NetworkCancellationRunner.kt](file:///Users/ryan/dev/Projects/bucketeer-io/android-client-sdk/bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/remote/NetworkCancellationRunner.kt)
- Generic class that:
    - Holds `AtomicReference<String?> currentRequestId`.
    - Handles async scheduling with cancellation checks against this ID.

### Dependency Injection
#### [MODIFY] [Component.kt](file:///Users/ryan/dev/Projects/bucketeer-io/android-client-sdk/bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/di/Component.kt)
- Add `evaluationCancellationRunner` and `eventCancellationRunner` to the interface and implementation.

### Network Layer
#### [MODIFY] [ApiClientImpl.kt](file:///Users/ryan/dev/Projects/bucketeer-io/android-client-sdk/bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/remote/ApiClientImpl.kt)
- Remove `retryOnException`. Fail fast.

### Client Layer (Manual)
#### [MODIFY] [BKTClientImpl.kt](file:///Users/ryan/dev/Projects/bucketeer-io/android-client-sdk/bucketeer/src/main/kotlin/io/bucketeer/sdk/android/BKTClientImpl.kt)
- Use `component.evaluationCancellationRunner` for `fetchEvaluations`.
- Use `component.eventCancellationRunner` for `flush`.

### Scheduler Layer (Background)
#### [MODIFY] [EvaluationForegroundTask.kt](file:///Users/ryan/dev/Projects/bucketeer-io/android-client-sdk/bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/scheduler/EvaluationForegroundTask.kt)
- Call `component.evaluationCancellationRunner.updateRequestID` before running.
- Catch 499 and reschedule.

#### [MODIFY] [EventForegroundTask.kt](file:///Users/ryan/dev/Projects/bucketeer-io/android-client-sdk/bucketeer/src/main/kotlin/io/bucketeer/sdk/android/internal/scheduler/EventForegroundTask.kt)
- Call `component.eventCancellationRunner.updateRequestID` before running.
- Catch 499 and reschedule.

## Verification Plan
1. **Unit Tests**:
   - `ApiClientImplRetryTest`: Verify 499 returns immediately (1 attempt).
   - `EvaluationForegroundTaskTest`: Verify 499 triggers short retry (2s).
