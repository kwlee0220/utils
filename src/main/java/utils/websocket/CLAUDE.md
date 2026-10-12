# utils.websocket

WebSocket connection wrapped as a `StateChart`. Built on top of `utils.statechart` to model connection lifecycle (open / connected / receiving / error / closed) as state transitions driven by domain signals.

## Core types

- **`WebSocketContext<C extends WebSocketContext<C>>`** — `extends StateContext<C>`. Carries server URL and back-reference to the chart. CRTP self-bound for domain-specific subclasses (e.g., `class MyCtx extends WebSocketContext<MyCtx>`).
- **`WebSocketStateChart<C>`** — `extends StateChart<C>`. Owns a `WebSocketListener<C>`, optional ping/pong keep-alive config, and the active `WebSocket` connection. Provides `sendText` / `sendBinary` (async + `*Sync` blocking variants) and `close()` / `close(statusCode, reason)`. Overrides `handleSignal` to add fall-back termination for unhandled `ErrorMessage` / `ConnectionClosed`.
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
- **`CompletedState<C>`** (`SinkState`) — `enter()` closes the connection via `chart.close(NORMAL_CLOSURE, "normal closure")`.
- **`CancelledState<C>`** (`SinkState`) — same close behavior, used as cancel sink.
- **`ErrorState<C>`** (`ExceptionState`) — same, with reason `"closure due to a error"`; holds the failure cause.

## Fall-back termination (`handleSignal`)

If user-defined transitions don't handle `ErrorMessage` or `ConnectionClosed`, `WebSocketStateChart.handleSignal` automatically:
- `ErrorMessage` → `fail(cause)`
- `ConnectionClosed` → `cancel(true)`

This prevents the chart from dangling in a non-terminal state when the WebSocket disconnects unexpectedly.

## Closing the connection

`close()` / `close(statusCode, reason)` on the chart is the single close path. It is **best-effort**: no WebSocket registered, already closed, or a failing `sendClose` are all swallowed and logged at debug, so a close failure can never flip a state transition or a termination notification into a failure. Repeated calls are therefore safe. It **blocks** until the close frame is sent (not until the peer answers).

Every terminal path closes the connection, via a `whenFinished` hook registered in the chart constructor — the same idiom the listener uses to cancel its ping timer. This matters because `complete()` / `fail()` / `cancel(true)` call `notifyXXX` directly and **never enter a state**, and `StateChart.cancelWork()` only runs `exitInGuard()`; without the hook, those paths (including this class's own `ErrorMessage → fail(cause)` fall-back, e.g. a pong timeout) would leave the socket open. The reason string comes from the terminal `Result`: `"closure due to a error"` when failed, `"normal closure"` otherwise.

The three sink states in `States` still close on `enter()`, which runs *before* the chart's `complete()`/`fail()` (see `StateChart.handleSignal`), so their per-state reason strings survive and the hook's later call is a no-op. They delegate to `chart.close(...)` rather than touching the `WebSocket` directly.

## Keep-alive

- `setPingInterval(Duration)` + `setPongTimeout(Duration)` — both must be set, and set **before the connection opens**; the timer starts in `onOpen` and is never reconfigured afterwards. Both reject `null`/zero/negative durations at the chart level (the listener's own setters do not validate — they are the internal delegates).
- Ping timer starts on `onOpen` and sends a ping every interval. **Each ping arms its own pong watchdog**, cancelled when that pong arrives. Missing pong → `ErrorMessage` (carrying `RuntimeTimeoutException`) is signalled and pinging stops.
- **Do not cancel the previous watchdog when sending the next ping.** That was the original code, and it silently disabled detection whenever `pongTimeout > pingInterval`: every scheduled watchdog got cancelled by the next ping before it could fire. The watchdog's own `lastPongTime >= pingSentAt` check already tolerates a late pong, so the cancel was never needed. Regression test: `WebSocketListenerTest.pongTimeout_fires_when_longer_than_pingInterval`.
- Detection latency is therefore up to `pingInterval + pongTimeout`, not `pongTimeout`.
- **Keep-alive stops permanently after the first pong timeout** (and on chart termination, via the `whenFinished` hook — both go through `stopPing()`). `startPing` runs only in `onOpen` and this class does not support reconnection, so a chart that survives its `ErrorMessage` (user-defined transition instead of the fall-back) runs on with no keep-alive.
- `onPing` **does not send a pong** — the JDK replies automatically. Calling `sendPong(message)` there sends a duplicate *and* reads a buffer the JDK may already have reclaimed (the returned `null` says "reclaim now" while `sendPong` is still async).
- `onPong` logs the round-trip time against the **most recent** ping, so the number understates RTT when more than one ping is outstanding. The ping payload is a fixed 4 bytes, so pongs cannot be matched to their ping.

## Connection lifecycle

1. `new WebSocketStateChart<>(context)` — creates listener.
2. (Optional) `setPingInterval` + `setPongTimeout` for keep-alive.
3. User defines states (typically with `OpenWebSocket` as initial) and registers final states.
4. `chart.start()` — kicks the chart; `OpenWebSocket.enter()` initiates connection.
5. On `Connected` signal, framework calls `chart.setWebSocket(ws)` (one-shot — second call throws `IllegalStateException`).
6. While running, `sendText` / `sendBinary` deliver messages.
7. On `ConnectionClosed` / `ErrorMessage` (or user-driven `complete`/`fail`/`cancel`), chart transitions to terminal — and the connection is closed on the way out, whichever path was taken.

## Conventions

- `setWebSocket(ws)` is `protected`, called once by `OpenWebSocket`. External code should not call it. The reference is never cleared — `getWebSocket()` keeps returning the last connection after close and after chart termination.
- `sendText` / `sendBinary` require `isRunning()` AND `webSocket != null`. Otherwise `IllegalStateException`.
- `*Sync` variants block on the future and wrap `ExecutionException` in `utils.RuntimeExecutionException`. `InterruptedException` propagates.
- Reconnection is NOT supported on the same chart instance — create a new `WebSocketStateChart`.
- Korean Javadoc dominant.
