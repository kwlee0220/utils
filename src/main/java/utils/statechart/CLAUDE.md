# utils.statechart

Hierarchical state chart built on top of `utils.async.AbstractAsyncExecution`. State transitions are signal-driven; the chart itself is a runnable execution that completes / fails / cancels via terminal states.

## Core types

- **`StateContext<C extends StateContext<C>>`** — CRTP self-bound. Domain-specific data carrier for the chart, with back-reference to the chart. `setStateChart(this)` is called once by the `StateChart` constructor; subsequent calls must throw `IllegalStateException`.
- **`StateChart<C>`** — `extends AbstractAsyncExecution<C> implements CancellableWork`. Holds states, initial state, final-state set, current state. Driven by `handleSignal(Signal)`. Lifecycle (`start` / `complete` / `fail` / `cancel(true)`) inherited from parent.
- **`State<C>`** — Marker interface with `getPath()`, `getContext()`, `enter()`, `exit()`, `selectTransition(Signal)`. Default `enter`/`exit` are no-op.
- **`Signal`** — Empty marker for events. Application defines concrete subtypes.
- **`Transition<C>`** — `getTargetStatePath()` (Optional — empty = self-transition) + `execute(context, signal)` action. Created via `Transitions.create` / `noop` / `stay` factories — direct implementation discouraged.
- **`TransitionAction<C>`** — `BiConsumer<C, Signal>`.

## State implementations

- **`AbstractState<C>`** — Base. Stores path, context, path-segments (split by `.`). `toString()` is `State[path]`.
- **`SingleOutState<C>`** — Reacts to exactly one trigger Signal (matched via `equals`); returns a static or supplier-provided transition. Other signals → `Optional.empty()`.
- **`SinkState<C>`** — Final-state base. `exit()` and `selectTransition` throw `UnsupportedOperationException`.
- **`ExceptionState<C>`** — `extends SinkState<C>`. Holds a `failureCause`. When chart's `traverse` reaches an `ExceptionState` that's a final state, `fail(cause)` is called instead of `complete()`. The cause must be set by the inbound transition's action before traverse calls `enter`.
- **`DefaultCompositeState<C>`** — Holds an initial sub-state and forwards `selectTransition` to the current sub-state. `enter` requires not-yet-entered, `exit` requires entered (otherwise throws `IllegalStateException`).

## Transition factories (`Transitions`)

- **`Transitions.create(targetPath, action)`** — Move to target + run action.
- **`Transitions.noop(targetPath)`** — Move to target only (no action).
- **`Transitions.stay()`** — Self-transition. `targetStatePath` is empty; `execute` is NOT called by the chart's traverse logic, and the current state's `exit`/`enter` are skipped. Singleton.

**Note on self-transition design**: there is no built-in way to "execute action while staying" — `stay()` skips the action; specifying the current state path explicitly causes `exit` + `enter` round-trip. For "side effect without leaving", update context via the signal-handling code path instead.

## StateChart lifecycle

1. `new StateChart(context)` — auto-attaches itself to the context via `setStateChart`.
2. `addState(State)` × N — register all states (must be in `NOT_STARTED`).
3. `setInitialState(path)` and `addFinalState(path)` × N (initial must NOT be in finals).
4. `start()` — locks `m_guard`, calls `notifyStarting`, runs `initialState.enter()`, transitions to `RUNNING`, calls `notifyStarted`. Failure during `enter()` → `notifyFailed(cause)` + `IllegalStateException`.
5. `handleSignal(signal)` — when running, asks current state for a transition; if matched, runs `traverse` (exit → action → enter); if target ∈ `m_finalStates`, auto `complete()` (or `fail(cause)` for `ExceptionState`).
6. `cancel(true)` → `cancelWork()` exits current state and returns `true`. Note: parent `cancel` does NOT block until terminal state is reached; use `waitForFinished()` for that.

## Conventions

- All transitions and listener invocations are serialized through `m_guard` (a separate `Guard` from parent's `m_aopGuard`).
- `traverse(...)` catches all `Exception` and routes to `notifyFailed`. `Error` propagates uncaught.
- `addState` / `setInitialState` / `addFinalState` reject calls after the chart has started (`isNotStarted()` check).
- `getState(path)` returns `null` for unknown paths (NOT IllegalArgumentException).
- Argument validation uses `utils.Preconditions.checkNotNull` / `checkNotNullArgument` / `checkArgument`. Korean Javadoc dominant.

## Common gotchas

- Final-state assertion: `start()` rejects if `m_initialState` is in `m_finalStates`. The chart must make at least one transition.
- `handleSignal(signal)` is silent (returns `Optional.empty()`) when chart is not running — useful for "fire and forget" but can mask logic bugs.
- `Transition.execute(...)` runs while `m_guard` is held. Long-running work in actions blocks signal processing.
- `ExceptionState` requires the inbound transition's action to populate `setFailureCause(cause)` BEFORE traverse calls `enter`; otherwise `fail(null)` is called.
