# utils.rpc.restful

A remote *operation* invoked over HTTP, with its lifecycle surfaced as a `utils.async` execution. Built on [utils.http](../../http/CLAUDE.md); the async client is a `utils.async.AbstractPeriodicPoller`. Subpackage [process/](process/) is the one concrete server: it exposes external commands as operations. User-facing guide: [README.md](README.md).

## Wire format

Three types cross the wire, all Jackson DTOs with `@JsonInclude(NON_NULL)`.

**`RpcRequestMessage`** — `{"inputs": {...}, "outputs": {...}}`. `inputs` is required (`null` → `IllegalArgumentException`); `outputs` is optional and names where the operation should put its results. Both maps are defensively copied.

Any *other* top-level field is preserved verbatim in `getExtraFields()` via `@JsonAnySetter`/`@JsonAnyGetter` and written back at the top level on re-serialization. That is what lets a caller thread server-specific fields through this DTO without subclassing it — but it also means a **misspelled `inputs` is silently accepted** as an extra field, and the operation then runs with no inputs.

**`RpcResponseMessage`** — `{"session_endpoint": ..., "status": ..., "outputs": {...}, "error": {...}}`.

| field | when | notes |
|---|---|---|
| `session_endpoint` | async only | where to poll / cancel this operation |
| `status` | always | `RpcState`: `RUNNING` / `COMPLETED` / `FAILED` / `CANCELLED` |
| `outputs` | **always** | `{}` when there is nothing; never absent, never null |
| `error` | `FAILED` only | a `utils.http.RESTfulErrorEntity` |

`status` and `session_endpoint` were once named `state` and `sessionEndpoint`. **There is no alias** — the rename is a hard break, and old and new peers cannot interoperate. A missing `status` fails loudly (`checkNotNullArgument`); a missing `session_endpoint` does not — it stays null and surfaces much later as a bare NPE inside `RESTfulAsyncRpcClient.concatUrl`. Unknown fields are ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`), so a server may add fields freely.

**Getter and field must carry the same `@JsonProperty`.** The field name (`m_state`) does not match the getter's implicit name (`state`), so without an explicit annotation on `getState()` Jackson splits them into two properties and emits **both** `status` and `state`. A round-trip test cannot catch this — assert on the emitted field names (`RpcResponseMessageTest.testStatusFieldName`).

`RpcState` is a plain enum. `RUNNING` never appears in a synchronous response.

## Client side

- **`RESTfulRpcClient`** — synchronous. `call()` POSTs and blocks: `COMPLETED` → outputs, `FAILED` → `ExecutionException` (or the restored `RESTfulIOException`, rethrown as-is), `CANCELLED` → `CancellationException`, `RUNNING` → `IllegalStateException` (a sync endpoint must not return it).
- **`RESTfulAsyncRpcClient`** — `extends AbstractPeriodicPoller`. `initializePoller()` POSTs to the operation endpoint and expects `RUNNING` (→ remember `session_endpoint`) or `FAILED` (→ `ExecutionException`); anything else is an `AssertionError`. `tryPoll()` then GETs **the session endpoint itself** — there is no `/state` or any other sub-path, and the server exposes no such route. `cancelWork()` DELETEs the same URL and reads the **HTTP status code** first: a 2xx means the cancel was accepted, `409 Conflict` (already terminal, or the operation refused the cancel) → `false`, and everything else — `404` for a session that never existed or was already reclaimed — propagates as `RESTfulException`. A 2xx body may carry either `CANCELLED` (`200`, cancel confirmed — what `RESTfulAsyncRpcServer` sends) or `RUNNING` (`202` + `phase: CANCELING`, cancel accepted but the remote is still stopping — what the robot twin sends, since a decelerating arm keeps its session until it settles); both → `true`. Any terminal state other than `CANCELLED` in a 2xx is an `AssertionError`: the server must express "could not cancel" as 409. Note the `RUNNING` case: the local execution ends `CANCELLED` at the next poll boundary while the remote may still be moving — `true` means "accepted", per the `CancellableWork` contract, not "stopped". Note also that `AssertionError` is an `Error`, which `EventDrivenExecution.cancel()`'s `catch (Exception)` does not catch, so it reaches the `cancel()` caller.

## Server side

`RESTfulAsyncRpcServer` is abstract; subclasses supply `createExecution` / `allocateSession` / `releaseSession` / `getExecutionOutput`, and may override `getStartTimeout()` (5s) / `getSessionRetainTimeout()` (30s).

`start(reqMsg)` holds the lifecycle wiring:

1. `createExecution(reqMsg)`, then `allocateSession(execution)` — the session is allocated **before** the execution starts, but registered in the session map only from the `whenStarted` listener.
2. `whenFinished` → `releaseSession(session)`, plus an `AsyncExecutions.delay(...)` that drops the session from the map after `getSessionRetainTimeout()`.
3. `execution.start()` + `waitWhileStarting(startTimeout)`. On timeout the execution is cancelled and a `FAILED` response with a **null** session endpoint is returned.
4. If the `whenStarted` listener never fired, the execution must be `FAILED` — asserted with `checkState`.

**Terminal sessions are read-once.** `buildResponseMessage` removes the session from the map as soon as it reports `COMPLETED`/`FAILED`/`CANCELLED`, so a second `status()` on the same endpoint returns `null` (→ 404 at the HTTP layer) rather than repeating the result. The retain timeout only covers the case where the client never comes back at all.

**`status`/`cancel` return `null` for an unknown session endpoint** instead of throwing; the HTTP layer maps that to 404. `cancel()` itself never signals "refused" — it returns whatever state the session settled into, and **the HTTP layer is what turns a non-`CANCELLED` outcome into 409**. Keep that split: the server class stays transport-agnostic, the dispatcher owns the status codes.

`AsyncRpcSession` pairs a session endpoint with its execution and is `AutoCloseable` (`close()` → `IOUtils.closeQuietly(execution)`).

## utils.rpc.restful.process

`RESTfulCommandExecutionServer` runs an external command per request, described by a JSON `CommandExecutionDescriptor` (`commandLine`, `workingDirectory`, `outputVariables`, `timeout`, `startTimeout`, `sessionRetainTimeout`).

- Each request gets its own directory `session-<UUID>-<seq>` under the working directory — the UUID is fixed per server instance, the sequence number per request — and `releaseSession` deletes it. Session endpoints are `/api/v1/sessions/<seq>`. (The class Javadoc says `<baseUrl>/sessions/<seq>`; the constant is `SESSION_PREFIX = "/api/v1/sessions/"`.)
- Operation inputs become `CommandVariable`s and reach the command through substitution — `${name:path}` materializes the value into a file and passes its absolute path. Outputs are read back from the files the command wrote. `CommandVariableSerDe` / `DefaultCommandVariableSerDe` own that mapping.
- Substitution goes through `utils.async.command.CommandExecution`, so **an unresolved `${X}` fails the operation** instead of reaching the shell as a literal token — see [utils.async](../../async/CLAUDE.md) and the user guide [../../async/command/README.md](../../async/command/README.md).

## Conventions

- Argument validation uses `utils.Preconditions`. DTO constructors validate with `checkNotNullArgument` / `checkArgument`, so a bad payload surfaces from Jackson as `ValueInstantiationException` (a `JsonMappingException`), not as the raw `IllegalArgumentException` — the DTO's `@throws` tags describe direct calls only.
- Korean Javadoc dominant.
- Tests live in `src/test/java/utils/rpc/restful/` and run against OkHttp `mockwebserver`.

## Common gotchas

- **`RpcResponseMessage.getJavaException()` throws unless the state is `FAILED`** (`checkState`), and never returns null. All three call sites dereference it without a null check, which is correct — do not "fix" them by adding one.
- `RESTfulAsyncRpcClient` builds the poll URL as `baseUrl + session_endpoint`, so the server must return an endpoint that is absolute *relative to that base*, not a full URL.
- `newDeserializer(JsonMapper)` serves the sync client's `HttpRESTfulClient` path; the async client parses responses with its own mapper directly. Jackson annotation changes on the DTO affect both, but only one path runs through the injected `ErrorEntityDeserializer`.
