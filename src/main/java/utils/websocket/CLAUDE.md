# utils.websocket

WebSocket connection wrapped as a `StateChart`. Built on top of `utils.statechart` to model connection lifecycle (open / connected / receiving / error / closed) as state transitions driven by domain signals.

## Core types

- **`WebSocketContext<C extends WebSocketContext<C>>`** — `extends StateContext<C>`. Carries server URL and back-reference to the chart. CRTP self-bound for domain-specific subclasses (e.g., `class MyCtx extends WebSocketContext<MyCtx>`).
- **`WebSocketStateChart<C>`** — `extends StateChart<C>`. Owns a `WebSocketListener<C>`, optional ping/pong keep-alive config, and the active `WebSocket` connection. Provides `sendText` / `sendBinary` (async + `*Sync` blocking variants). Overrides `handleSignal` to add fall-back termination for unhandled `ErrorMessage` / `ConnectionClosed`.
- **`WebSocketListener<C>`** (package-private) — `WebSocket.Listener` implementation. Translates JDK callbacks into `Signal`s on the chart. Manages ping timer and pong timeout.
- **`WebSocketSignal`** — Marker base for signals carrying a `WebSocket` reference.
- **`Signals`** — Concrete signal subtypes (data carriers).
- **`States`** — Concrete state implementations for connection lifecycle.
- **`WebSocketObservable`** — RxJava-style adapter (419 lines, see source for usage).

## Signals

| Signal | Carries | Emitted by |
|--------|---------|-----------|
| `Connected` | WebSocket | `OpenWebSocket.enter()` on successful `buildAsync` completion |
| `ConnectionFailed` | Throwable cause | `OpenWebSocket.enter()` on connection failure (no WebSocket) |
| `TextMessage` | WebSocket, String | `WebSocketListener.onText` after fragments accumulated (last=true) |
| `BinaryMessage` | WebSocket, byte[], boolean last | `WebSocketListener.onBinary` per fragment |
| `ErrorMessage` | WebSocket, Throwable | `WebSocketListener.onError`. Fall-back: `fail(cause)` |
| `ConnectionClosed` | WebSocket, int statusCode, String reason | `WebSocketListener.onClose`. Fall-back: `cancel(true)` |

## States (`States`)

- **`OpenWebSocket<C>`** (`AbstractState`) — `enter()` triggers `HttpClient.newWebSocketBuilder().buildAsync(uri, listener)`. On completion, signals `Connected` (success) or `ConnectionFailed` (failure). `selectTransition` routes `Connected` → target state, `ConnectionFailed` → fail state.
- **`CompletedState<C>`** (`SinkState`) — `enter()` sends `WebSocket.NORMAL_CLOSURE`.
- **`CancelledState<C>`** (`SinkState`) — same close behavior, used as cancel sink.
- **`ErrorState<C>`** (`ExceptionState`) — sends close on entry, holds the failure cause.

## Fall-back termination (`handleSignal`)

If user-defined transitions don't handle `ErrorMessage` or `ConnectionClosed`, `WebSocketStateChart.handleSignal` automatically:
- `ErrorMessage` → `fail(cause)`
- `ConnectionClosed` → `cancel(true)`

This prevents the chart from dangling in a non-terminal state when the WebSocket disconnects unexpectedly.

## Keep-alive

- `setPingInterval(Duration)` + `setPongTimeout(Duration)` — both must be set before chart start for pings to begin.
- Ping timer starts on `onOpen`, sends ping every interval, arms a pong timeout per ping. If pong doesn't arrive within timeout, an `ErrorMessage` (with `RuntimeTimeoutException`) is signaled and the timer is cancelled.
- Timer is auto-cancelled when the chart enters terminal state (registered via `whenFinished` in the listener constructor — fires once, not accumulated across reconnections).
- `setPingInterval` and `setPongTimeout` reject `null`/zero/negative durations.

## Connection lifecycle

1. `new WebSocketStateChart<>(context)` — creates listener.
2. (Optional) `setPingInterval` + `setPongTimeout` for keep-alive.
3. User defines states (typically with `OpenWebSocket` as initial) and registers final states.
4. `chart.start()` — kicks the chart; `OpenWebSocket.enter()` initiates connection.
5. On `Connected` signal, framework calls `chart.setWebSocket(ws)` (one-shot — second call throws `IllegalStateException`).
6. While running, `sendText` / `sendBinary` deliver messages.
7. On `ConnectionClosed` / `ErrorMessage` (or user-driven `complete`/`fail`/`cancel`), chart transitions to terminal.

## Conventions

- `setWebSocket(ws)` is `protected`, called once by `OpenWebSocket`. External code should not call it.
- `sendText` / `sendBinary` require `isRunning()` AND `webSocket != null`. Otherwise `IllegalStateException`.
- `*Sync` variants block on the future and wrap `ExecutionException` in `utils.RuntimeExecutionException`. `InterruptedException` propagates.
- Reconnection is NOT supported on the same chart instance — create a new `WebSocketStateChart`.
- Korean Javadoc dominant.
