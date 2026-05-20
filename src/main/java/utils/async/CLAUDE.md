# utils.async

Async execution primitives. Built on `Execution<T> extends Future<T>` with explicit lifecycle state (`AsyncState`) and listener-based completion handling. Subpackages: [op/](op/) (combinators), [command/](command/) (sub-process orchestration).

## Core types

- **`Execution<T>`** — `Future<T>` extension. Adds `AsyncState`, `whenStarted`/`whenStartedAsync`/`whenFinished`/`whenFinishedAsync` listeners, `map`/`flatMap` chaining, `waitForStarted`/`waitForFinished`, `setTimeout`.
- **`AsyncState`** — `NOT_STARTED` → `STARTING` → `RUNNING` → terminal (`COMPLETED` | `CANCELLED` | `FAILED`). `CANCELLING` is a transition state requested via `cancel(true)`.
- **`AsyncResult<T>`** — Snapshot of a finished/running execution. Returned by `poll()` and `waitForFinished()`.
- **`Result<T>`** (in `utils.func`) — Sum type for completion outcome; used by `getResult()` and `whenFinished` callback (`ifSuccessful`/`ifFailed`/`ifNone`).
- **`Startable`**, **`StartableExecution<T>`** — Marker interfaces for executions with explicit `start()`.
- **`CancellableWork`** — Marker interface that opts in to forced cancel via `cancel(true)` (default `cancel(true)` only works on `NOT_STARTED`).
- **`Cancellable`** — Older marker (kept for backward compatibility).

## Key implementations

- **`EventDrivenExecution<T>`** — Default base. State transitions driven by `notifyStarting/Started/Completed/Failed/Cancelling/Cancelled`. **Almost every other Execution implementation extends this.**
- **`AbstractAsyncExecution<T>`** — Adds `Startable` + `ExecutorAware`. Subclass implements `start()`.
- **`AbstractThreadedExecution<T>`** — `start()` spawns a thread (or submits to injected `Executor`). Subclass implements `executeWork()` (and optionally `initializeThread()`). Provides `run()` for synchronous in-thread execution.
- **`AbstractLoopExecution<T>`** — `executeWork()` is a loop with `initializeLoop` / `iterate(long)` / `finalizeLoop` hooks. Cooperative cancel via `isCancelRequested()`.
- **`PeriodicLoopExecution<T>`** — `AbstractLoopExecution` with a fixed inter-iteration interval. Constructor flag `cumulativeInterval` toggles "cumulative" (loop-start-relative due, with catch-up if an iteration overruns the interval) vs. "non-cumulative" (delay measured from end of the previous iteration). The interval is final after construction. Supports `setTimeout(Duration)` / `setDue(Instant)` for hard loop deadlines.
- **`AbstractPeriodicPoller<R>`** — Specialization of `PeriodicLoopExecution` for "poll until target state observed". Subclass implements `tryPoll()` (returns `Optional<R>`: present = done, empty = continue). Optional `initializePoller`/`finalizePoller` hooks. `finalizePoller` always runs once after a successful `initializePoller`, regardless of termination reason; its `state` arg is the result on normal completion, `null` otherwise. Default cumulative mode is `true`.
- **`PeriodicPoller<T>`** — Concrete builder-based variant of `AbstractPeriodicPoller`. Static factories: `poll(Supplier)` (non-null = done), `pollUntil(BooleanSupplier)`, `pollWhile(BooleanSupplier)`. Builder configures `interval` (cumulative) / `delay` (non-cumulative) — mutually exclusive, last call wins — plus `timeout`/`due` (due wins if both set), `initializer`, and `name`.
- **`AbstractLoopThreadService`** — Long-running service variant (start/stop loop on demand).
- **`CompletableFutureAsyncExecution<T>`** — Bridges `CompletableFuture<T>` into `Execution<T>`. Implements `CancellableWork`.

## Cancellation flow

1. External thread calls `cancel(true)`.
2. `notifyCancelling()` transitions `RUNNING → CANCELLING` (or returns immediately if already past RUNNING).
3. If the execution implements `CancellableWork`, `cancelWork()` is called *outside* the lock.
4. `cancel(true)` returns `true`. **Terminal state transition is NOT awaited** — it happens asynchronously when the worker thread exits its loop and calls `notifyCancelled` / `notifyFailed` / `notifyCompleted`.
5. Use `waitForFinished()` to await the terminal state.

`cancel(false)` only works on `NOT_STARTED` (or when `CancellableWork`). For RUNNING + non-`CancellableWork` non-interruptible, returns `false`.

`checkCancelled()` and `isCancelRequested()` on `EventDrivenExecution` are the cooperative-cancel helpers used inside loop bodies.

## Concurrency primitives

- **`FStreamExecutor`** — Bridges `utils.stream.FStream` into async execution.
- **`AsyncUtils`**, **`Executions`** — Static factories and helpers (e.g. `Executions.getTimer()` exposes the shared timer used by `setTimeout`).

Generic concurrency primitives previously living here have moved to `utils.thread`:

- **`utils.thread.Guard`** — `ReentrantLock` + `Condition` wrapper. The `m_aopGuard` field on `EventDrivenExecution` (protected, reentrant) is the canonical state-protection lock. `awaitCondition(predicate).andReturn()` / `.andRunChecked(task)` is the standard wait pattern.
- **`utils.thread.Timer`** — Schedules timeouts on `Execution` via `setTimeout`.
- **`utils.thread.SingleWaiterExecutor`** — Latest-wins single-slot executor (one running + one pending; new submit replaces and cancels pending).
- **`utils.thread.LengthOneOverridingQueue`** — Single-slot blocking queue where new offers overwrite the pending element.
- **`utils.thread.AsyncConsumer`** — Async consumer helper.

## Subpackages

- **[utils.async.op](op/)** — Combinators (`SequentialAsyncExecution`, `ConcurrentAsyncExecution`, `FoldedAsyncExecution`, `TimedAsyncExecution`, `BackgroundedAsyncExecution`, `DelayedAsyncExecution`, `FlatMapAsyncExecution`) + `AsyncExecutions` static factory façade.
- **[utils.async.command](command/)** — Sub-process orchestration: `CommandExecution` (builder-based, variable substitution, stdin/stdout/stderr redirection, timeout, SIGTERM-then-SIGKILL termination via `utils.ProcessTree`), `ProgramService`, `ServiceShutdownHook`.

## Conventions

- Subclasses access state via `m_aopGuard` (protected on `EventDrivenExecution`). State transitions are serialized through this lock; `getInAsyncExecutionGuard(Supplier)` / `runInAsyncExecutionGuard(Runnable)` for external state-coupled operations.
- `whenStarted` listeners fire only when the execution actually reached RUNNING. `NOT_STARTED → CANCELLED` (cancel before start) skips them.
- `whenFinished` always fires on terminal transition.
- `Async`-suffixed variants run the listener via `CompletableFuture.runAsync`. Non-async variants run synchronously inside the lock.
- Argument validation uses `utils.Preconditions.checkNotNullArgument` / `checkArgument`. Avoid Guava `Preconditions` in new code.
- Korean Javadoc dominant; preserve language when modifying.

## Common gotchas

- `notifyStarting` is **idempotent** for STARTING (returns `true` even if already STARTING). Don't rely on it as a "first call wins" guard for parallel `start()`.
- `notifyCompleted`/`notifyFailed`/`notifyCancelled` accept `RUNNING` and `CANCELLING` (so a cancel-in-progress can still be completed/failed). They reject `NOT_STARTED`/`STARTING` for `notifyCompleted`.
- `notifyCancelling` and `notifyCancelled` wait while state is `STARTING` (with `cancelTimeout`, default 3s) — calling them from within the same thread that holds STARTING leads to deadlock-like timeout.
- `AbstractLoopExecution` does NOT implement `CancellableWork` — cooperative-cancel via `isCancelRequested()` is the mechanism. Worker exits its loop when it sees the cancel request at the next iteration boundary.
