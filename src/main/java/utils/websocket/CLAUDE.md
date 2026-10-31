# utils.websocket

WebSocket connection wrapped as a `StateChart`. Built on top of `utils.statechart` to model connection lifecycle (connecting / connected / receiving / error / closed) as state transitions driven by domain signals. User guide: [README.md](README.md).

## Renames on 2026-10-31 (outright, no aliases — callers are utils and mdt only; RCK migrated)

| Old | New | Why |
|---|---|---|
| `States.OpenWebSocket` | `States.WebSocketConnectingState` | it connects and waits; the prefix avoids a clash with application states (RCK had its own `Connecting`, since renamed `Registering`) |
| `States.CancelledState` | `States.StoppedState` | reaching it ends the chart **`COMPLETED`**, not `CANCELLED` — the old name misled |
| `States.ErrorState` | `States.FailedState` | matches `CompletedState` / `StoppedState` |
| `Signals.ErrorMessage` | `Signals.WebSocketError` | it is an error (JDK error, pong timeout, text too long), not a received message |
| `Signals.TextMessage.getMessage()` | `getText()` | same name as `WebSocketObservable.TextMessage.getText()` |
| `WebSocketListener.m_stateMachine`, `WebSocketContext.m_machine`, params `machine` | `m_chart` / `chart` | the type is `StateChart` |
| `WebSocketListener.m_pendingPongTimeout` | `m_latestPongTimeoutTask` | only the newest of several watchdogs |
| `getWebSocketListener()` | removed (third round); package-private `listener()` for the package/tests | the connecting state uses per-attempt listeners and other packages cannot register a socket, so it had no use |
| chart `close()` / `close(code, reason)` / `closeAsync(...)` | `closeConnection()` / `closeConnection(code, reason)` / `closeConnectionAsync(...)` | the chart is an `Execution`; `close` read as "end the chart" |

## Core types

- **`WebSocketContext<C extends WebSocketContext<C>>`** — implements `StateContext<C>`. Carries server URL and back-reference to the chart (`m_chart`, volatile: `WebSocketConnectingState` reads it from callback threads). `setStateChart` is `synchronized` (check-then-set was not atomic; same contract as `AbstractStateContext`).
- **`WebSocketStateChart<C>`** — `extends StateChart<C>`. Owns the `WebSocketListener<C>`, keep-alive config, connection settings, and the registered `WebSocket`. Send API: `sendText(text)` / `sendText(text, last)` / `sendBinary(...)` + `*Sync` variants (the one-arg forms = `last=true`, added 2026-10-31). Close API: `closeConnection()` / `closeConnection(code, reason)` (blocks for the frame only) and `closeConnectionAsync()` / `closeConnectionAsync(code, reason)` (completes when the closing handshake ends or the connection is aborted; never exceptionally). Overrides `onUnhandledSignal` for the fall-back.
  - **Connection settings must be set before `start()`** (`checkNotStarted()` → ISE, 2026-10-31): `setPingInterval`, `setPongTimeout`, `setHttpClient`, `setConnectTimeout`, `setWebSocketBuilderCustomizer`. They are read once when connecting / starting the ping, so later changes were partial or ignored. `setCloseTimeout` and `setMaxTextMessageLength` stay changeable while running (read on each use).
  - `setWebSocketBuilderCustomizer(Consumer<WebSocket.Builder>)` — applied by the connecting state after `connectTimeout`, right before `buildAsync` (handshake headers, subprotocols). Exceptions propagate out of the state's `enter()`.
- **`WebSocketListener<C>`** (package-private) — JDK callbacks → signals; ping timer; text joining; `startReceiving(ws)`; `peerClosed()` future.
- **`WebSocketCloser`** (package-private, 2026-10-31) — the single closing procedure (see Closing).
- **`SharedResources`** (package-private, 2026-10-31) — lazily-created shared `HttpClient` (chart + Observable default), the connection-callback executor (cached daemon pool, threads `websocket-callback-N`), and the keep-alive scheduler (one daemon thread `websocket-scheduler`, `setRemoveOnCancelPolicy(true)`).
- **`WebSocketSignal`**, **`Signals`** — signal base and subtypes (Javadoc'd 2026-10-31).
- **`States`** — `WebSocketConnectingState`, `CompletedState`, `StoppedState`, `FailedState`.
- **`WebSocketObservable`** — RxJava adapter, one connection per subscription (see Observable).
- **`WebSocketClosedException`** — peer's close `statusCode`/`reason`. `isNormalClosure(code)`: 1000, 1001, **1005** are normal (1005 = peer sent a close frame without a code — a plain close). Used by the chart fall-back and the Observable.

## Signals

| Signal | Carries | Emitted by |
|--------|---------|-----------|
| `Connected` | WebSocket | `WebSocketConnectingState` after registering the socket |
| `ConnectionFailed` | Throwable cause (non-null since 2026-10-31) | `WebSocketConnectingState` on connection failure |
| `TextMessage` | WebSocket, String (`getText()`, non-null) | `WebSocketListener.onText` after joining fragments, up to `getMaxTextMessageLength()`. `toString()` shows at most 200 chars + length (the chart's DEBUG "received signal" log used to print whole messages) |
| `BinaryMessage` | WebSocket, byte[] (non-null), boolean last | `onBinary` **per fragment** — deliberate (decided 2026-10-31): states can stream large binaries; no accumulation, so no cap |
| `WebSocketError` | WebSocket, Throwable (non-null) | `onError`, pong timeout, text over max length. Fall-back: fail |
| `ConnectionClosed` | WebSocket, int statusCode, String reason | `onClose`. Fall-back: normal code → cancel, otherwise fail with `WebSocketClosedException` |

## Connecting (`WebSocketConnectingState`)

- Constructor `(path, context, connectedStatePath, failedStatePath)`; chart = `context.getStateChart()` via private `chart()` (ISE naming the path when unattached).
- `enter()`: `chart.getHttpClient().newWebSocketBuilder()` (+ `connectTimeout` when non-null, default 30 s) `.buildAsync(uri, chart.getWebSocketListener())`, result handled with `whenCompleteAsync(..., SharedResources.callbackExecutor())`. **Not the common pool**: the JDK completes the future on `ForkJoinPool.commonPool()` (`HttpClientImpl` forces `ASYNC_POOL`), and the callback runs the `Connected` transition and the next state's `enter()` — user code that may block (sync sends, close waits). Until 2026-10-31 it ran on the common pool.
- **Attempt number** `m_attempt` (AtomicLong) is bumped by `enter()` **and `exit()`**; a callback acts only if its attempt is still current. So a result arriving after the state was left (e.g. via an `on(...)` transition) is not delivered to another state.
- `onConnected(ws, attempt)` order: chart done → `closeAbandoned(ABANDONED_CLOSE_REASON = "chart finished before connected")`; attempt stale → `closeAbandoned(STATE_LEFT_CLOSE_REASON = "connecting state left before connected")`; a socket already registered → `closeAbandoned(RECONNECTION_CLOSE_REASON)` + fail with ISE (reconnection unsupported); `setWebSocket(ws)`; done/stale again → `chart.listener().abandon(ws)` + `closeAbandoned(...)`; else `handleSignal(Connected)` **then `chart.listener().startReceiving(ws)`**.
- The "done/stale again" branch used `chart.closeConnection()` until the third review: the peer's close reply then reached the chart as `ConnectionClosed` (closeConnection requests demand) and the fall-back cancelled a chart that had merely left the connecting state. `abandon(ws)` makes the listener (and every per-attempt listener) drop all callbacks of that socket. The registration itself cannot be undone, so later sends fail. Test: `connection_registered_while_leaving_state_does_not_cancel_chart` (a chart subclass leaves the state inside `setWebSocket`).
- `onConnectFailed`: if chart done or attempt stale → log and drop; else `ConnectionFailed(unwrapped cause)`.
- The callback body is wrapped in `try/catch(Throwable)` → ERROR log, close an unregistered socket, `failQuietly(e)`.
- `selectTransition`: `Connected` → connected path; `ConnectionFailed` → failed path (sets the cause on a `FailureState` target); anything else → the `on(...)` table.
- Checks are lock-free on purpose (callback threads must not take the chart lock: a state holding it while waiting on the connection would deadlock); double close / double `stopPing` are harmless.

## Receiving starts after `Connected` (2026-10-31)

`WebSocketListener.onOpen` only logs — it does **not** `request(1)` and does **not** start pinging. `startReceiving(ws)` (called by the connecting state after `Connected` has been processed) does both. Reasons:
- With `request(1)` in `onOpen`, a server greeting could reach the chart before `Connected` and be dropped in the connecting state (README used to document this as a limitation). Now the JDK holds it until the request.
- `onOpen` also fires for sockets that are never registered (late, stale, reconnection); starting the ping there would replace the registered socket's timer (`startPing` calls `stopPing` first) and could fail a running chart with a spurious pong timeout.

Callback filtering has two layers:
- **Per-attempt listener** (2026-10-31, second review): the connecting state passes `chart.listener().forAttempt(() -> isCurrent(attempt))` to `buildAsync`, not the chart listener itself. It forwards a callback only if the socket is the registered one, or — while nothing is registered — if the attempt is still current. So an `onError` from an attempt the state already left (window: after `onOpen`, before the result callback runs `closeAbandoned`) no longer fails the chart, while the current attempt's early error (before registration) still reaches it and fails the chart via the fall-back. `isForeign` alone could not tell those two apart.
- The chart listener itself still ignores callbacks from a socket other than the registered one (`isForeign`), for code that passes `getWebSocketListener()` directly.

`m_peerClosed` completes only for the **registered** socket (`isRegistered`); an error on a not-yet-registered socket used to complete it, so closing the socket registered later would abort instead of waiting for the peer's reply.

Consequence: messages are not received until the `Connected` target state's `enter()` returns — don't wait for server messages inside that `enter()`.

## Closing (`WebSocketCloser`, 2026-10-31)

One procedure for every path (chart `closeConnection`/`closeConnectionAsync`, `closeAbandoned`, Observable): send the close frame → wait for the peer's close (`peerClosed`) → if the whole thing exceeds the timeout, `abort()`. Send failure → abort at once **unless** `ws.isOutputClosed()` (a close was already sent; keep waiting for the reply). `peerClosed == null` (abandoned sockets: no demand, so the reply can't be received) → abort right after sending. Returns `Closing(frameSent, closed)`; neither completes exceptionally; the timeout completes both. Reason: `sendClose()` only closes output; input stays open until the peer replies, and the JDK has no handshake timeout — an unresponsive peer used to leave the socket open forever (and `closeAbandoned` only aborted after the send future completed, so a stuck send never aborted).

Chart specifics:
- `closeConnection(code, reason)` blocks only until `frameSent` (bounded by the timeout); the reply wait/abort continues in the background. It runs from terminal-state `enter()` and the `whenFinished` hook under the chart lock, so it must not wait for the round trip. Interrupt → restore flag + abort.
- `closeConnectionAsync(...)` returns a copy of `closed`.
- Before closing, the chart calls `ws.request(1)` so the peer's close can be delivered even if receiving has not started yet (closing during the `Connected` transition). Extra demand is harmless.
- `peerClosed` = the listener's future, completed in `onClose` (before the signal is handled — a closer may be waiting) and `onError`.
- `closeConnection()` is best-effort and idempotent; every terminal path closes via the `whenFinished` hook (reason `"closure due to an error"` when failed, else `"normal closure"`); the sink states close on `enter()` first, so their reasons survive.
- **`closeConnection()` does not end the chart by itself, but the peer's reply usually does**: the reply arrives as `ConnectionClosed` → fall-back cancel/fail unless a transition handles it (e.g. `stay()`). When the peer closes first, the fall-back runs on the JDK `onClose` thread and the hook's reply goes out from inside `onClose`.

## Fall-back termination (`onUnhandledSignal`)

Called under the chart lock while running, when no active state selected a transition:
- `WebSocketError` → `super.fail(cause)`
- `ConnectionClosed`: normal code → `super.cancel(true)`, else `super.fail(new WebSocketClosedException(code, reason))`.

`super.` on purpose: subclass overrides of `cancel()`/`fail()` must not swallow the fall-back. RCK overrides `cancel()` (sends a stop command first); the fall-back bypasses that, which is fine because the connection is already closed.

## Keep-alive

- `setPingInterval` + `setPongTimeout` — both required, before `start()` (ISE afterwards); pinging starts in `startReceiving` (was `onOpen` until 2026-10-31). The listener's fields are volatile.
- **Shared scheduler** (third review): the ping task (`scheduleAtFixedRate`, exceptions caught — one would suppress all later runs) and each pong watchdog run on `SharedResources.scheduler()`; until then each chart created its own `java.util.Timer` thread. Since the shared scheduler cannot cancel "all tasks of this listener", every task carries the **ping generation** (`m_pingGeneration`, bumped by `startPing` and `stopPing`) and does nothing when stale. `stopPing` cancels the periodic task and the latest watchdog. The pong-timeout signal is handed to `SharedResources.callbackExecutor()` so chart processing never blocks the single scheduler thread.
- Each ping arms its own pong watchdog; **do not cancel the previous watchdog when sending the next ping** (that disabled detection whenever `pongTimeout > pingInterval`; test `pongTimeout_fires_when_longer_than_pingInterval`). Detection latency is up to `pingInterval + pongTimeout`.
- First pong timeout → `stopPing()` then `WebSocketError(RuntimeTimeoutException)` (on a callback-pool thread); keep-alive never resumes.
- `onPing` does not send a pong (JDK replies; a manual `sendPong` duplicated it and read a reclaimed buffer). `onPong` logs RTT at DEBUG against the most recent ping.

## Text accumulation limit

`onText` joins fragments; if the joined length would exceed `getMaxTextMessageLength()` (default 16M chars) it drops the buffer (new `StringBuilder`), signals one `WebSocketError(IllegalStateException)`, and discards the rest of that message (`m_discardingText`) — a chart that handles the error resumes with the next message. Unsynchronized on purpose: the JDK calls receive methods serially.

## Millisecond durations

`WebSocketStateChart.checkMillisDuration(d, name)` (package-private static, also used by the Observable's `closeTimeout`) validates `setPingInterval`, `setPongTimeout`, `setCloseTimeout`: non-null, **≥ 1 ms**, and `toMillis()` must not overflow (e.g. `ChronoUnit.FOREVER`). Scheduling uses `toMillis()`, so a sub-millisecond value became 0 ms: the pong watchdog expired immediately (a pong timeout on every ping), `scheduleAtFixedRate(…, 0)` threw inside the connection callback (the chart failed right after connecting with "period <= 0"), and close aborted without waiting. Until 2026-10-31 only positivity was checked. `setConnectTimeout` is passed to the JDK as a `Duration` and keeps the plain positivity check.

## Sending

`sendText`'s DEBUG log uses `Signals.TextMessage.abbreviate` (200 chars + length) like `TextMessage.toString()`.

JDK accepts one outstanding text/binary send; violating it fails the **returned future** with `IllegalStateException` (silently dropped if ignored). The chart does not serialize sends — documented in Javadoc/README 4.1. `sendBinary` wraps the array without copying.

## Observable (`WebSocketObservable`)

- Builder: `httpClient` (default = `SharedResources` client, lazily; `null` rejected — the builder used to create a client eagerly per builder), `connectTimeout` (30 s, `null` = none), `closeTimeout` (5 s), `webSocketBuilderCustomizer` (headers/subprotocols), `collectText/BinaryFragments` (true), `maxText/BinaryMessageLength` (16M; only when collecting). Over the cap → buffer dropped, `onError(IllegalStateException)`, connection closed.
- Close mapping: normal code → `onComplete`, else `onError(WebSocketClosedException)`; the reply close is always sent with 1000 (the received code, e.g. 1005/1006, may be illegal for a client to send).
- `subscribeActual` wraps builder setup + `buildAsync` in `try/catch(Throwable)` → `disposable.onError(e)`. Anything thrown after `onSubscribe` (e.g. a customizer calling `header(null, …)`) otherwise went to the RxJava global error handler and the subscription never terminated. Restricted handshake headers (`Sec-WebSocket-*`) are not affected: the JDK reports them as a failed future.
- All closes go through `WebSocketCloser` with the disposable's `peerClosed` (completed by `onClose`/`onError`, also after dispose).

## Conventions

- `setWebSocket(ws)` is `protected`, called once by the connecting state; never cleared.
- `sendText`/`sendBinary` require `isRunning()` and a registered socket (ISE `"WebSocketStateChart is not running"` / `"WebSocket is not connected"`).
- Reconnection is not supported on one chart instance.
- Connection settings are read when used (connect on the connecting state's `enter()`, close on each close) — set them before `start()`.
- Korean Javadoc dominant.

## Tests

- `WebSocketStateChartTest` — argument checks, close paths, fall-back, timeouts, shared client, connection flow (`ControlledClient` = mock `HttpClient` whose `buildAsync` future the test completes; `captureAttemptListener` grabs the per-attempt listener: receiving starts after `Connected`, callback thread name, stale attempt, error on an abandoned vs. current attempt, abandoned socket aborted even if its close send hangs), and MockWebServer scenarios (`startConnectedChart` + `ClosingServer`: happy path, greeting right after upgrade, user `closeConnection()` vs. peer reply, peer-initiated close, `closeConnectionAsync`, reconnection).
- `WebSocketListenerTest` — keep-alive via `startReceiving` (incl. pings on `websocket-scheduler`, no `java.util.Timer` threads, pinging stops with the chart), `onOpen` does nothing, fragments, text cap, foreign-socket filtering (`RecordingChart` overrides `handleSignal`).
- `WebSocketStateChartTest` also covers: settings rejected after start, builder customizer order, keep-alive against a real answering server (stays running) and a silent raw server (fails with `RuntimeTimeoutException`).
- `WebSocketCloserTest` — every abort/no-abort branch of the closing procedure.
- `WebSocketObservableTest` — close-code mapping, receipt, dispose, size caps, builder validation, fragment collection on/off, connect timeout, handshake header via the customizer.
- `RawWebSocketServer` (test helper) — accepts one connection and writes unmasked frames itself: fragmented messages, a server that never answers pings, a server that never answers the upgrade. Use it for what OkHttp MockWebServer cannot do (it never fragments and always answers pings).
- MockWebServer server listeners must answer the client's close in `onClosing` (`ws.close(code, null)`); otherwise the client aborts after the close timeout and `MockWebServer.close()` gives up waiting ("Gave up waiting for queue to shut down").
- mdt-instance `RCKSimulationTest` (JUnit 4) exercises the RCK stop/cancel ordering against a fake RCK server.
