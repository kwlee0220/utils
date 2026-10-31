# utils.thread

Concurrency utilities sitting between `java.util.concurrent` and [utils.async](../async/CLAUDE.md): the canonical lock+condition wrapper, recurring schedulers, executor wrappers, and inter-thread hand-off channels. User-facing guide: [README.md](README.md).

**`Guard` is the load-bearing type here** — 12 files inside this library and ~30 in the downstream MDT projects use it. The rest are standalone utilities with little or no in-library usage; treat them as available tools, not as assumed infrastructure.

## Guard — the standard lock+condition pattern

One `ReentrantLock` + one `Condition`, created via `Guard.create()`. Two usage modes plus a low-level escape hatch.

- **Work mode** — `run(Runnable)`, `get(Supplier)`, `accept(Consumer,T)` and their `Checked*X` variants (`runChecked`, `getChecked`, `acceptChecked`). Acquires the lock, runs the body, and calls `signalAll()` in `finally` before unlocking. **`get()` signals too**, even though it is nominally a read — fine everywhere except a hot read path.
- **Condition-wait mode** — `awaitCondition(pred)` → `AwaitCondition`, or `awaitCondition(pred, due|Duration|(timeout,unit))` → `TimedAwaitCondition`. Terminals: `andReturn()`, `andRun(task)`, `andRunChecked(task)`, `andGet(supplier)`, `andGetChecked(supplier)`. The timed variants throw `TimeoutException` (immediately if `timeout == 0` and the condition is unmet).
- **`preAction(Runnable)`** — chains a one-shot action that runs under the lock just before the condition is first evaluated (e.g. send the request, then wait for the response). If it throws, `signalAll()` fires and the exception propagates.
- **Low-level** — `lock()`/`unlock()`, `signalAll()`, `awaitSignal()`, `awaitSignal(Date|Duration)`, `awaitSignalNanos(long)`. Caller owns lock pairing and signal placement.

Predicate polarity: `Guard.awaitCondition(pred)` loops `while (!pred)` — it waits **until** the predicate is true.

`awaitCondition(...).andReturn()` waits but does **not** signal; `andRun`/`andGet` signal after the task. Mutating shared state after `andReturn()` leaves waiters asleep — use `andRun` instead.

Convention across the repo: a `private final Guard m_guard = Guard.create();` field with `@GuardedBy("m_guard")` on the state it protects. `EventDrivenExecution` exposes its guard as the protected `m_aopGuard`.

## ConditionVariable — inverted polarity

`ConditionVariable(Condition, Supplier<Boolean> waitCond)` loops `while (waitCond)` — the predicate is a **wait** condition, the opposite of `Guard.awaitCondition`. `await()`, `await(time, unit)`, `awaitUntil(deadline)`; the caller holds the lock. Prefer `Guard` in new code; mixing the two polarities silently produces "returns immediately" or "waits forever" bugs.

## Recurring execution

`RecurringWork` = `onStarted(schedule)` / `perform()` / `onStopped()`. `InterruptableWork` adds `interrupt()` for work that provides its own cancel path.

Two `RecurringSchedule` implementations:

| Impl | Threading | Timing |
| --- | --- | --- |
| `RecurringScheduleThread` | dedicated thread | Static factories `newFixedRateSchedule` / `newFixedDelaySchedule` (with or without `initialDelay`). Fixed-rate subtracts elapsed `perform()` time from the interval, logs `late schedule` on overrun, and compensates sleep overhead via `utils.LongVariableSmoother`. Fixed-delay sleeps the full interval after each run. |
| `RecurringScheduleImpl` | shared `CamusExecutor` pool | Built through `CamusExecutor.createScheduleWithFixedRate/Delay`, not constructed directly. |

Shared control surface: `getState()` (`STARTING`/`WORKING`/`IDLE`/`STOPPING`/`STOPPED`), `getFailureCause()`, `getInterval()`/`setInterval(millis)` (live-updatable), `performNow()` (wakes an IDLE schedule for an immediate iteration), `start()`, `stop(mayInterruptIfRunning)`, `waitForStopped()`/`waitForStopped(timeout)`.

`stop()` returns before `onStopped()` necessarily ran — chain `waitForStopped()` when cleanup must be complete. Calling `waitForStopped()` from inside `perform()` on your own schedule throws `RuntimeException` (deadlock guard).

## Executors

- **`CamusExecutor`** (impl `CamusExecutorImpl`) — `Executor` plus `submit(Runnable|Callable)`, `schedule(task, delay[, unit])`, `createScheduleWithFixedRate/Delay(...)`, `stop(shutdownBaseExecutor)`. Wraps an injected `ExecutorService` or creates its own.
- **`ExecutorAware`** — `getExecutor`/`setExecutor`; the container injects after property setup and before `Initializable.initialize()`.
- **`AsyncConsumer<T> implements Consumer<T>`** — pushes consumption onto a pool. `singleWorker(consumer[, executor])`, `multipleWorkers(statelessConsumer, nThreads)`, `from(statelessConsumer, executor)`. `accept()` is fire-and-forget; `schedule()` returns a `CompletableFuture<Void>`. The multi-worker factories require a **stateless** consumer.
- **`SingleWaiterExecutor`** — at most one running `StartableExecution` plus a single pending slot. A new `submit` while one is running `cancel(true)`s whatever was pending and takes its place (latest-wins). Not an `Executor`; fire-and-forget, so track completion via the execution's own finish callbacks.
- **`CallerRunsSerialExecutor`** — `Executor` that runs tasks one at a time in FIFO order **without its own thread**: whichever thread enqueues while no one is draining becomes the dispatcher and drains the queue, including tasks other threads add meanwhile. `execute` (fire-and-forget, failures only logged), `submit(Callable)` / `submit(Runnable)` (→ `CompletableFuture`), `invokeOrDefer` (waits, unless waiting would deadlock — the caller is the dispatcher, or holds the optional `outerLock` while another thread drains — in which case it only enqueues; returns an `Invocation`: `isDeferred()`, `completion()`, and `get()` which, for a non-deferred call, rethrows the task's **original** exception — a `CompletionException` the task threw itself is not unwrapped, which is why `Task` records the failure beside the future. Deferred ≠ not run: resending runs the task twice). Cancelling a returned future does not stop the task; a task whose future is already done when it reaches the head of the queue is skipped (best-effort). `runAsDispatcher` (advanced; bypasses the queue: waits for the dispatcher role, runs the task on the caller thread, then drains what queued meanwhile; rejects a caller holding `outerLock`. **Can starve** — the dispatcher only lets go when the queue is empty, and nothing hands the role to a waiter. **Called from the dispatcher it runs inline, nested inside the current task**, ahead of everything queued). Javadoc lists the costs (dispatcher can be held long, no thread affinity, non-async future callbacks registered before completion run on the dispatcher — but callbacks registered after completion, or fired by a caller completing/cancelling the future, run elsewhere, so never assume callbacks are inside the serial order) and the differences from Guava `MoreExecutors.newSequentialExecutor(directExecutor())` — use Guava when plain fire-and-forget ordering is enough. `StateChart`'s signal queue is built on it (outer lock = the chart lock; `postSignal` returns `submit(...).copy()` so callers cancelling or timing out the future cannot drop a signal — do the same wherever a submitted task must run regardless of what the caller does with its future).
- **`Timer`** — daemon-thread timer backing `Execution.setTimeout`; usually reached through `Executions.getTimer()`. **Its queue is a `MinMaxPriorityQueue.maximumSize(64)`** — past 64 concurrent timers the furthest-out deadline is silently evicted. Not a general-purpose scheduler.

## Inter-thread hand-off

- **`LengthOneOverridingQueue<T>`** — length-1, latest-wins: `add` overwrites an unconsumed item with no notification. `add` rejects `null` (it is the empty sentinel). `poll()` blocks; `poll(Duration)` throws `TimeoutException`; `peek()` is non-destructive and may return `null`. Use for coalescible values (sensor samples, progress); use a `BlockingQueue` when no event may be lost.
- **`CompletionFStream<T> extends SuppliableFStream<Result<T>>`** — submit executions, consume results in **completion order**. Note the element type is `Result<T>`, not `T`; failures and cancellations arrive as stream elements, not exceptions. `submit` auto-starts a `StartableExecution` and throws `IllegalStateException` after close/`endOfSupply`. Completion callbacks fill the buffer **synchronously on the worker thread**, so a full buffer blocks workers — size the buffer against the pool.
- **`InterThreadShareSupplier<T> implements CheckedSupplier<T>`** — call coalescing, **not memoization**. Concurrent `get()` calls share one in-flight computation (result or exception); non-overlapping calls each recompute. `setWaitTimeout(Duration)` bounds the shared wait (unbounded by default). For real caching use `utils.func.Lazy` / `ReloadableLazy` / `TimedLazy`.

## Conventions

- Argument validation uses `utils.Preconditions`. `ConditionVariable` still imports Guava `Preconditions` — new code should not.
- Fields protected by a `Guard` carry `@GuardedBy("m_guard")`.
- Classes that log implement `utils.LoggerSettable` (`RecurringScheduleImpl`, `RecurringScheduleThread`, `SingleWaiterExecutor`, `InterThreadShareSupplier`).
- Korean Javadoc dominant; preserve language when modifying.
- Tests exist only for `Guard`, `CompletionFStream` and `CallerRunsSerialExecutor` (`src/test/java/utils/thread/`). Changes elsewhere in this package are unguarded by tests.

## Common gotchas

- `signalAll()` called outside the lock throws `IllegalMonitorStateException`. Work mode and `andRun`/`andGet` handle it for you.
- `andReturn()` does not signal — see above.
- `Guard.get()` signals on every read.
- `RecurringSchedule.stop()` is a request, not a join.
- `Timer` caps at 64 pending timers.
- `LengthOneOverridingQueue` drops events by design.
