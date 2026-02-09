# Decision Record: Android 499 Retry Strategy

## Context
We are implementing non-blocking retry logic for 499 errors to match the iOS SDK behavior.
On iOS, this was achieved by modifying the **Network Layer** directly.
On Android, we chose the **Shared Scheduler Wrapper** approach instead of the **Full Async Refactor**.

## Comparison of Approaches

| Feature | iOS Implementation (Reference) | Android "Shared Scheduler Wrapper" | Android "Full Async Refactor" |
| :--- | :--- | :--- | :--- |
| **Strategy** | **Delay Callback** | **Schedule Retry** | **Rewrite to Callbacks** |
| **Mechanism** | `DispatchQueue.asyncAfter` | `Executor.schedule` | `Callback` interfaces |
| **Blocking?** | No (Native Async) | No (Background Schedule) | No (Native Async) |
| **Changes** | **Minimal** (Network Layer only) | **Minimal** (~5 new files) | **Massive** (~40 existing files) |
| **Test Impact** | None | Minimal (New tests) | **Destructive** (Rewrite 100+ tests) |

### Why iOS was "Easy"
The iOS SDK's Network Layer is natively **Asynchronous**.
It uses completion handlers (callbacks).
*   **iOS Logic**: "If 499, don't call the callback yet. Wait 1 second (non-blocking), then try again."
*   **Code**: `DispatchQueue.main.asyncAfter(deadline: .now() + 1) { self.request(...) }`
*   **Result**: The thread is never blocked.

### Why Android is "Hard"
The Android SDK's Network Layer is natively **Synchronous**.
It returns a value immediately (`return Result`).
*   **Android Logic**: To wait inside this function, we MUST use `Thread.sleep(1000)`.
*   **Result**: The thread IS blocked.

## Decision: Shared Scheduler Wrapper
To get the iOS "Non-blocking Delay" behavior on Android without rewriting the entire SDK to use callbacks (The "Full Async Refactor"), we wrap the synchronous calls in a **Scheduler**.

1.  **Fail Fast**: The synchronous call fails immediately on 499 (No sleep).
2.  **Schedule Retry**: The wrapper catches the 499 and schedules a new task for 1 second later (`Executor.schedule`).

This mimics the iOS `asyncAfter` behavior perfecty, but does so *outside* the synchronous network layer, avoiding a massive refactor.
