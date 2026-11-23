# utils.rpc.restful

A remote *operation* invoked over HTTP, with its lifecycle surfaced as a `utils.async` execution. Built on [utils.http](../../http/CLAUDE.md); the async client is a `utils.async.AbstractPeriodicPoller`. Subpackage [process/](process/) is the one concrete server: it exposes external commands as operations. User-facing guide: [README.md](README.md).

## Wire format

Three types cross the wire, all Jackson DTOs with `@JsonInclude(NON_NULL)`.

**`RpcRequestMessage`** — `{"inputs": {...}, "outputs": {...}}`. `inputs` is required (`null` → `IllegalArgumentException`); `outputs` is optional and names where the operation should put its results. Both maps are defensively copied.

Any *other* top-level field is preserved verbatim in `getExtraFields()` via `@JsonAnySetter`/`@JsonAnyGetter` and written back at the top level on re-serialization. That is what lets a caller thread server-specific fields through this DTO without subclassing it. A missing or misspelled `inputs` is not absorbed as an extra field: the creator receives `null` and rejects it, so the payload fails to deserialize. An operation with no inputs must still send `"inputs": {}`.

**`RpcResponseMessage`** — `{"session_endpoint": ..., "status": ..., "outputs": {...}, "error": {...}}`.

| field | when | notes |
|---|---|---|
| `session_endpoint` | async only | where to poll / cancel this operation |
| `status` | always | `RpcState`: `RUNNING` / `COMPLETED` / `FAILED` / `CANCELLED` |
| `outputs` | required for `COMPLETED` | a `COMPLETED` response without it is rejected; in other states an absent `outputs` becomes `{}`. `getOutputs()` never returns null |
| `error` | `FAILED` only | a `utils.http.RESTfulErrorEntity` |

The fields are named `status` and `session_endpoint`; a peer that sends `state`/`sessionEndpoint` is not understood. A missing `status` fails loudly (`checkNotNullArgument`); a missing `session_endpoint` does not — it stays null and surfaces much later as a bare NPE inside `RESTfulAsyncRpcClient.concatUrl`. Unknown fields are ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`), so a server may add fields freely.

**Getter and field must carry the same `@JsonProperty`.** The field name (`m_state`) does not match the getter's implicit name (`state`), so without an explicit annotation on `getState()` Jackson splits them into two properties and emits **both** `status` and `state`. A round-trip test cannot catch this — assert on the emitted field names (`RpcResponseMessageTest.testStatusFieldName`).

`RpcState` is a plain enum. `RUNNING` never appears in a synchronous response.

## Client side

- **`RESTfulRpcClient`** — synchronous. `call()` POSTs and blocks: `COMPLETED` → outputs, `FAILED` → `ExecutionException` whose cause is the `RESTfulRemoteException` from `getJavaException()`, `CANCELLED` → `CancellationException`, `RUNNING` → `UnexpectedRpcStateException` (an `IllegalStateException` carrying the response, so the caller can attach to the already-started session or cancel it; a sync endpoint must not return it).
- **`RESTfulAsyncRpcClient`** — `extends AbstractPeriodicPoller`. `initializePoller()` POSTs to the operation endpoint and expects `RUNNING` (→ remember `session_endpoint`) or `FAILED` (→ `ExecutionException`). A terminal `COMPLETED`/`CANCELLED` start response (a sync operation called asynchronously) is kept as the result: the first `tryPoll()` returns it without a GET, there is no session, and `cancelWork()` returns `false`. `Builder.setSessionEndpoint(endpoint)` attaches to an already-started session: no POST, `operationEndpoint`/`requestMessage` become optional, and polling/cancel use that session. `tryPoll()` then GETs **the session endpoint itself** — there is no `/state` or any other sub-path, and the server exposes no such route. `cancelWork()` DELETEs the same URL and reads the **HTTP status code** first: a 2xx means the cancel was accepted, `409 Conflict` (already terminal, or the operation refused the cancel) → `false`, and everything else — `404` for a session that never existed or was already reclaimed — propagates as `RESTfulException`. A 2xx body may carry either `CANCELLED` (`200`, cancel confirmed) or `RUNNING` (`202`, cancel accepted but the remote is still stopping — `RESTfulAsyncRpcServer` sends it when the operation outlives its `getCancelWait()`, and the robot twin sends it with `phase: CANCELING` because a decelerating arm keeps its session until it settles); both → `true`. Any terminal state other than `CANCELLED` in a 2xx is an `AssertionError`: the server must express "could not cancel" as 409. Note the `RUNNING` case: the local execution ends `CANCELLED` at the next poll boundary while the remote may still be moving — `true` means "accepted", per the `CancellableWork` contract, not "stopped". Note also that `AssertionError` is an `Error`, which `EventDrivenExecution.cancel()`'s `catch (Exception)` does not catch, so it reaches the `cancel()` caller.

  **Cancel before the session exists.** The start POST runs inside `initializePoller()`, after the poller is already `RUNNING`, so there is a window with no session URL. In that window `cancelWork()` records the cancel (`m_cancelPending`) and returns `true`. As soon as `initializePoller()` receives the session, it sends the DELETE (`deliverPendingCancel()`; a failure is only logged) and polling continues to the remote's final state. If the start is rejected instead, the record is dropped. `m_sessionUrl`/`m_cancelPending` are guarded by `m_sessionLock`. `getSessionUrl()` returns the session URL and `getSessionEndpoint()` the session path (the server's `session_endpoint`, or the attached endpoint) — pass the latter to another client's `setSessionEndpoint` to reattach. Both are `null` before the session exists, when the start failed, or when the start response was already terminal. `m_sessionEndpoint` is guarded by `m_sessionLock` too.

## Server side

`RESTfulAsyncRpcServer` is abstract; subclasses supply `createExecution` / `allocateSession` / `releaseSession` / `getExecutionOutput`, and may override `getStartTimeout()` (5s) / `getSessionRetainTimeout()` (30s) / `getCancelWait()` (2s).

`start(reqMsg)` holds the lifecycle wiring:

1. `createExecution(reqMsg)`, then `allocateSession(execution)` — the session is allocated **before** the execution starts, but registered in the session map only from the `whenStarted` listener.
2. `whenFinished` → `releaseSession(session)`, plus an `AsyncExecutions.delay(...)` that drops the session from the map after `getSessionRetainTimeout()`.
3. `execution.start()` + `waitWhileStarting(startTimeout)`. On timeout the execution is cancelled and a `FAILED` response with a **null** session endpoint is returned.
4. If the `whenStarted` listener never fired, the execution must be `FAILED` — asserted with `checkState`.

**Terminal sessions are retained, not read-once.** `buildResponseMessage` never removes a session; a finished session stays in the map until the `whenFinished` delay removes it `getSessionRetainTimeout()` after completion, and every `status()` in between returns the same terminal result. This is what makes "cancel arrives just after completion" safe: `cancel()` reports the terminal state (→ 409) without deleting it, and the client's next poll still gets the result. It also covers a client that lost a response and retries.

**`status`/`cancel` return `null` for an unknown session endpoint** instead of throwing; the HTTP layer maps that to 404. `cancel()` returns `RpcCancelResult(response, accepted)`, because the state alone cannot tell "accepted, still stopping" from "refused, still running":
- running → `cancel(true)`; accepted if it returns `true` or the execution is now `CANCELLING`, then wait at most `getCancelWait()` (keep it below the client's cancel timeout — mdtpy uses 3 s). `false` while still running = **refused** (`accepted=false`). A non-`EventDrivenExecution` `StartableExecution` may refuse; `EventDrivenExecution` returns `false` on `RUNNING` only when it became terminal or `cancelWork()` threw (→ `FAILED`).
- already `CANCELLING` → do **not** call `cancel(true)` again: `EventDrivenExecution.cancel()` blocks on a `CANCELLING` execution until it changes or `cancelTimeout` (3 s) passes, which would exceed the client's cancel timeout. Just wait `getCancelWait()`; `accepted=true`.
- already terminal → that state; `accepted` only for `CANCELLED`.

**The HTTP layer owns the status codes**: `isCancelled()` → 200, `isStopping()` → 202, everything else (refused, or terminal `COMPLETED`/`FAILED` — including a cancel that was accepted but lost the race to completion) → 409 (mdt-operation-http `OperationController.cancelSession`). Keep that split: the server class stays transport-agnostic.

**Public helpers for hosts** (used by mdt-operation-http):
- `hasSessions()`: any registered session, running or terminal within its retain time. Used to decide when a replaced server can be dropped.
- `cancelAllSessions(timeout)`: `cancel(true)` on every running session, then waits up to `timeout` in total. The finished executions go through `releaseSession`, so subclasses clean up. Call it on shutdown.

**The start response is always `RUNNING` once the execution has started** (or `FAILED` when it could not start). This holds even if the execution already finished during the start wait. `start()` returns `RpcResponseMessage.running(endpoint)` and never `buildResponseMessage`, so outputs are collected only through `status()`. mdtpy accepts only `RUNNING`/`FAILED` there and raises `RuntimeError` otherwise. `RESTfulAsyncRpcClient` also accepts a terminal start response (for the robot twin's sync operations), but that is a client tolerance, not permission for this server to send one. Keep it that way so the start response has the same shape for every operation.

`AsyncRpcSession` pairs a session endpoint with its execution and is `AutoCloseable` (`close()` → `IOUtils.closeQuietly(execution)`).

## utils.rpc.restful.process

`RESTfulCommandExecutionServer` runs an external command per request, described by a JSON `CommandExecutionDescriptor` (`commandLine`, `workingDirectory`, `outputVariables`, `timeout`, `startTimeout`, `sessionRetainTimeout`).

- Each request gets a session id `<UUID>-<seq>` — the UUID is fixed per server instance, the sequence number is a JVM-wide counter — used for both its directory `session-<UUID>-<seq>` under the working directory (deleted by `releaseSession`) and its endpoint `/api/v1/sessions/<UUID>-<seq>` (`SESSION_PREFIX = "/api/v1/sessions/"`). The UUID is what keeps endpoints unique across restarts: the counter restarts at 1 with the process, so with a sequence-only endpoint a client still polling a pre-restart endpoint could receive another operation's result. Keep the UUID in the endpoint.
- `getDescriptor()` returns the loaded descriptor (do not mutate it). `getWorkingDirectory()` returns the resolved working directory, which is the descriptor file's directory when none is given.
- Operation inputs become `CommandVariable`s and reach the command through substitution — `${name:path}` materializes the value into a file and passes its absolute path. Outputs are read back from the files the command wrote. `CommandVariableSerDe` / `DefaultCommandVariableSerDe` own that mapping. Inputs go through `deserialize`, output variables through `deserializeOutput` (a default method that delegates to `deserialize`). An output that shares a name with an input reuses the input variable. Override `deserializeOutput` when an output location must not be read up front: mdt-operation-http does this so a remote `File` reference becomes an upload target instead of being downloaded.
- Substitution goes through `utils.async.command.CommandExecution`, so **an unresolved `${X}` fails the operation** instead of reaching the shell as a literal token — see [utils.async](../../async/CLAUDE.md) and the user guide [../../async/command/README.md](../../async/command/README.md).

## Conventions

- Argument validation uses `utils.Preconditions`. DTO constructors validate with `checkNotNullArgument` / `checkArgument`, so a bad payload surfaces from Jackson as `ValueInstantiationException` (a `JsonMappingException`), not as the raw `IllegalArgumentException` — the DTO's `@throws` tags describe direct calls only.
- Korean Javadoc dominant.
- Tests live in `src/test/java/utils/rpc/restful/` and run against OkHttp `mockwebserver`.

## Common gotchas

- **`RpcResponseMessage.getJavaException()` throws unless the state is `FAILED`** (`checkState`), and never returns null. All three call sites dereference it without a null check, which is correct — do not "fix" them by adding one.
- `RESTfulAsyncRpcClient` builds the poll URL with `concatUrl(baseUrl, session_endpoint)`, which joins them with exactly one `/` whether or not either side has it. The server must return a path relative to that base, not a full URL.
- `newDeserializer(JsonMapper)` serves the sync client's `HttpRESTfulClient` path; the async client parses responses with its own mapper directly. Jackson annotation changes on the DTO affect both, but only one path runs through the injected `ErrorEntityDeserializer`.
