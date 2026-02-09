# Analysis: Option 2 - Full Async Refactor

This document outlines the work required to convert the Android SDK to a fully asynchronous architecture (similar to iOS) to support valid non-blocking retries at the Network Layer.

## The "Ripple Effect"
Because Android is currently Synchronous, changing the bottom layer (`ApiClient`) forces a change in every layer above it.

### 1. Network Layer (ApiClient)
**Current**:
```kotlin
fun getEvaluations(...): GetEvaluationsResult
```
**Required Change**:
```kotlin
fun getEvaluations(..., callback: (GetEvaluationsResult) -> Unit)
```
- **Impact**: Breaking change for all implementations and tests.

### 2. Logic Layer (Interactors)
`EvaluationInteractor` and `EventInteractor` rely on sync returns for logic flow (e.g. `if (result.isSuccess) { DB.save() }`).
**Required Change**:
- Rewrite `fetch()` and `sendEvents()` to accept callbacks.
- Logic flow (if/else) must be converted to callback nesting (Warning: "Callback Hell").

### 3. Client Layer (BKTClientImpl)
**Current**:
```kotlin
executor.submit { 
  val result = interactor.fetch() // sync
  return result
}
```
**Required Change**:
- Cannot use simple `executor.submit` anymore because `interactor.fetch` returns void immediately.
- Must implement `CompletableFuture` or `CountDownLatch` bridging to keep the legacy `Future` API working for users.

### 4. Test Infrastructure (The Hidden Cost)
This is the most expensive part.
**Current**:
```kotlin
every { apiClient.getEvaluations(...) } returns GetEvaluationsResult.Success(...)
```
**Required Change**:
All tests using MockK/Mockito must be rewritten to capture callbacks:
```kotlin
every { apiClient.getEvaluations(..., any()) } answers {
  val callback = secondArg<(Result) -> Unit>()
  callback.invoke(GetEvaluationsResult.Success(...))
}
```
- **Scope**: ~20-30 Test files. Hundreds of test cases.

## Summary Comparison

| Feature | Plan 1 (Shared Runner) | Plan 2 (Full Refactor) |
| :--- | :--- | :--- |
| **Logic** | Wrap Sync calls in Async Runner | Rewrite all calls to Async |
| **Files Changed** | ~5 Files | ~40 Files |
| **Risk** | Low (New Code) | High (Rewriting Core Logic) |
| **Test Impact** | Minimal (New Tests) | Massive (Rewrite Old Tests) |
| **Result** | Non-Blocking, Cancelable | Non-Blocking, Cancelable |

## Recommendation
**Stick with Plan 1**.
Plan 1 achieves the exact same architectural goal (Non-blocking, Latest-wins request cancellation) by *wrapping* the synchronous core in a robust Async Runner. Plan 2 achieves it by *destroying and rebuilding* the core.
