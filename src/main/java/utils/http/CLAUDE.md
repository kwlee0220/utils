# utils.http

OkHttp-based **synchronous** REST client with pluggable body/error deserialization and a fixed error-response dispatch. Everything fails via unchecked exceptions. [utils.rpc.restful](../rpc/restful/) is built on top of it, and ~44 files in the downstream MDT projects use it. User-facing guide: [README.md](README.md).

## Core types

- **`HttpRESTfulClient`** — The client. Built via `builder()` (`httpClient`, `header`, `jsonMapper`, `errorEntityDeserializer`, `remoteException`) or `newDefaultClient()`. Methods: `get`/`post`/`put`/`patch`/`delete` (each with a deserializing form → `T` and a raw form → `String`), `head` → `Headers`, `callAndGetHeaders(Request, deser)` → `Tuple<Headers,T>`, `jsonDeserializer()` → a `JsonNode` deserializer bound to the client's own mapper. Constants `MEDIA_TYPE_JSON`, `MEDIA_TYPE_TEXT`, `STRING_DESER`, `JSON_DESER`; static body helpers `newJsonRequestBody(String)` / `newTextRequestBody(String)`. **Every body-carrying method takes a `RequestBody`** — there is no String-body overload anywhere, by design.
- **`ResponseBodyDeserializer<T>`** (nested, `@FunctionalInterface`) — `T deserialize(Headers, String body)`. Must not be null (`checkNotNullArgument`); it is simply not called when the response has no body. Anything it throws — checked or unchecked — is wrapped in `RESTfulIOException`, except an already-`RESTfulException` which passes through unwrapped. `JacksonResponseBodyDeserializer<>(mapper, cls)` is the Jackson impl.
- **`ErrorEntityDeserializer`** (nested, `@FunctionalInterface`) — `RESTfulErrorEntity deserialize(String body)`. Default is `JacksonErrorEntityDeserializer`.
- **`OkHttpClientUtils`** — `newClient()`, plus `newTrustAllOkHttpClient()` / `newTrustAllOkHttpClientBuilder()` which disable **both** certificate and hostname verification. MITM-vulnerable; test/internal use only.
- **`HttpClientProxy`** — `getHttpClient()` + `getEndpoint()` pairing interface.

## Body-less responses

`parseResponse` never reads a body it cannot have: HEAD responses and 204/205/304 short-circuit to `respBody == null` via `hasNoBody(Response)`. The success/error split is still decided solely by `isSuccessful()`, so 204/205 return `null` while 304 goes down the error path with a null body — deliberately, since this client does no caching and a 304 reaching it means the caller sent conditional headers and needs to hear about it. `toRESTfulClientException` therefore has to tolerate a null/blank body (it does, as its first branch), and `parseResponse(resp, deser)` skips the deserializer entirely on a null body rather than handing it `null` — `JacksonResponseBodyDeserializer` would throw a raw `IllegalArgumentException` from `readValue(null, …)`, escaping the `RESTfulException` hierarchy.

## Exception hierarchy

```
RuntimeException → RESTfulException → RESTfulRemoteException | RESTfulIOException
```

`RESTfulException` is never thrown directly — it exists purely so callers can catch both subtypes at one point. `RESTfulRemoteException.getRemoteErrorEntity()` returns `null` when the exception was built from a message rather than an entity.

**`RESTfulException.getHttpStatusCode()` carries the status code of the error response.** `parseResponse` stamps it on the exception returned by `toRESTfulClientException` just before throwing, so every non-2xx failure knows its own code; anything not born of an HTTP response (connect failure, timeout, body parse error) stays at `UNKNOWN_STATUS_CODE` (`-1`). This is the only reliable discriminator when the *code itself* carries protocol meaning — the exception **type** does not, since a 404 arrives as `RESTfulRemoteException` or `RESTfulIOException` depending purely on the body shape. `utils.rpc.restful.RESTfulAsyncRpcClient.cancelWork()` is the motivating caller: it maps `409 Conflict` to "already finished, nothing to cancel" and lets 404 propagate. The setter is package-private and called once — do not stamp it anywhere else.

Transport-layer failures all become `RESTfulIOException`: `ConnectException` ("Failed to connect"), `SocketTimeoutException` ("timed out" — it covers read/write timeouts too, not just connect), `InterruptedIOException`, and any other `IOException`. **The catch order carries meaning**: `SocketTimeoutException extends InterruptedIOException`, so swapping the clauses silently routes every socket timeout through the interrupt-detection branch.

`InterruptedIOException` is split by the caller thread's actual interrupt state, because OkHttp reports a **`callTimeout` expiry as `InterruptedIOException("timeout")`** (`RealCall.timeoutExit`) — indistinguishable by type from a real interrupt. Since OkHttp does not react to `Thread.interrupt()` during blocking socket I/O, a genuinely interrupted thread still carries its flag on arrival; so the flag is read, never set. Interrupted → "RESTful call interrupted"; otherwise → "RESTful call timed out". Unconditionally calling `Thread.currentThread().interrupt()` here (what the code used to do) left a spurious interrupt flag on every call timeout.

## Error-response dispatch — the part to get right

On `!response.isSuccessful()`, `toRESTfulClientException(statusCode, url, body)` parses the body once into a `JsonNode` and discriminates **by which fields are present**, in this order:

| Condition | Format | Resulting exception |
| --- | --- | --- |
| empty body (`MissingNode`) | — | `RESTfulIOException` ("Empty RESTful error response") |
| has `messageType` | `TypedServerErrorMessage` | `toException()` (always a `RESTfulRemoteException`), wrapped by `Throwables.toRuntimeException` |
| has `status` **and** `path` | `SpringBootErrorResponse` | **`RESTfulIOException`** — not a remote exception |
| non-null `code` **or** `message` | `RESTfulErrorEntity` via the injected deserializer | `RESTfulErrorEntity.toRemoteException()` |
| body is not JSON | — | `RESTfulIOException` carrying the raw body |
| right shape, wrong field types | — | `RESTfulIOException` ("Failed to parse RESTful error response") |
| none of the above | — | `RESTfulIOException` ("Unrecognized RESTful error response") |

Order matters: a body carrying both `messageType` and `code` takes the first branch.

**Before the table is consulted, a nested `{"error": {...}}` wrapper is unwrapped — but only when `error` holds an object.** The type check is not cosmetic: Spring Boot's own format puts a reason-phrase *string* in `error` (`"error": "Internal Server Error"`), so unwrapping unconditionally replaces the root with a `TextNode`, every branch below misses, and genuine Spring Boot bodies all land in "Unrecognized" — the `SpringBootErrorResponse` row becomes dead code. Assertions on substrings of the echoed body (`"Internal Server Error"`, the path) do **not** catch this, since the "Unrecognized" message embeds the raw body; assert on `SpringBootErrorResponse.toString()`'s own format instead.

**This method never throws — it only returns.** That is load-bearing, and easy to break: `JsonMapper.convertValue()` signals failure with an unchecked `IllegalArgumentException` (bad `messageType` enum value, a string in `SpringBootErrorResponse.status`, …), so the whole dispatch chain sits inside a `catch (IllegalArgumentException)`. Without it a malformed error body escapes the `RESTfulException` hierarchy entirely — the same failure shape the old `toClientException()` call had.

**HTTP context is on the `RESTfulIOException` branches only.** Every `RESTfulIOException` message above is prefixed with `[<status> <url>]` by `withHttpContext(...)`. Without it a body-less 4xx/5xx produced a message with no status code and no URL at all — nothing to diagnose from. The `RESTfulRemoteException` branches are deliberately left unprefixed: the server already said what went wrong, and the entity is reachable via `getRemoteErrorEntity()`.

**Class loading is not performed.** The `{code, message}` branch calls `RESTfulErrorEntity.toRemoteException()`, which keeps `code`/`message` as data. `RESTfulErrorEntity.toClientException()` — which `Class.forName`s the `code` string and invokes its `(String)` or no-arg constructor via `ReflectionUtils` — exists but is **not** on the client path, matching the "avoid arbitrary class loading" note in the DTO's class Javadoc. It is also unusable as a dispatch branch for a second reason: it **throws instead of returning** when reconstruction fails (unknown class, not a `RuntimeException` subtype, missing constructor, constructor threw), which breaks `toRESTfulClientException`'s return-an-exception contract. A server emitting `{"code":"java.io.IOException"}` used to reach it and blow up with `ClassCastException`, since `IOException` is not a `RuntimeException`.

**Typed exceptions come back only through an explicit allowlist.** `Builder.remoteException(code, factory)` maps one error `code` to a factory that receives the error message (`message` of `{code, message}`, or `text` of the `messageType` format — both branches consult it) and returns the exception to throw. Unregistered codes stay `RESTfulRemoteException`; a factory that throws or returns `null` falls back to that too, so `toRESTfulClientException` still never throws. A registered exception that is not a `RESTfulException` does not get the HTTP status code stamped. Why it exists: before the switch to `toRemoteException()`, callers caught server exceptions by type (`ResourceAlreadyExistsException`, `ResourceNotFoundException`, …) and those catches went dead silently — e.g. MDT's `mdt add wfmodel --force` stopped overwriting. Downstream MDT code registers its API exceptions once in `mdt.client.MDTRESTfulClients.builder()`; new MDT HTTP clients should be built from it, not from `HttpRESTfulClient.builder()`.

## Default-header application differs by path

- URL-based methods build the request via `newRequestBuilder(url)`, which `addHeader`s every default header **unconditionally**.
- `callAndGetHeaders(Request, deser)` goes through `applyDefaultHeaders(req)`, which adds a default header **only if the request lacks it** — caller-supplied headers win.

Keep this in mind when adding new call methods; pick the helper that matches the intended precedence.

## Conventions

- Argument validation uses `utils.Preconditions.checkNotNullArgument`.
- `HttpRESTfulClient` implements `utils.LoggerSettable`; every call logs at DEBUG (request line, then `code`/`headers`/`body`). Response bodies are logged in full — beware of secrets in debug logs.
- All three error DTOs carry `@JsonInclude(NON_NULL)` and `@JsonIgnoreProperties(ignoreUnknown = true)`. `SpringBootErrorResponse` and `TypedServerErrorMessage` need it because servers add `trace`/`exception` fields depending on configuration. `RESTfulErrorEntity` needs it because servers attach details next to `code`/`message` (the robot twin's 409 `RESOURCE_BUSY` carries `resource`/`operation`/`occupied_by`/`estimated_remaining_ms`); without it such a body failed to deserialize and surfaced as `RESTfulIOException` ("Failed to parse RESTful error response"), losing the `code` callers dispatch on. The extra fields are dropped, not preserved.
- Korean Javadoc dominant; preserve language when modifying.
- Tests live in `src/test/java/utils/http/` and use OkHttp `mockwebserver` (`HttpRESTfulClientTest`, `HttpRESTfulClientCallTest`, `OkHttpClientUtilsTest`, plus one per error DTO).

## Common gotchas

- **No String-body overloads — the media type is always named at the call site.** `post(url, String, deser)` / `put(url, String, deser)` once wrapped the string in `MEDIA_TYPE_TEXT`, so a serialized JSON string went out as `Content-Type: text/plain; charset=utf-8` and drew a 415. `postJson`/`putJson` were tried as the fix and then dropped too: a String parameter that silently picks a media type is the shape of the bug, whichever type it picks. What survives is `newJsonRequestBody(String)` / `newTextRequestBody(String)` (or `RequestBody.create(...)`), where the choice is visible in the caller. Re-adding a String-body overload reopens the trap. Removal was compile-time-safe: `String` does not convert to `RequestBody`, so any missed call site fails to compile rather than silently changing behavior.
- **`JSON_DESER` ignores `builder().jsonMapper(...)`** — it is a static constant holding its own `JsonMapper`. Use the instance method `jsonDeserializer()` when the configured mapper matters.
- **Spring Boot servers surface 404/500 as `RESTfulIOException`**, not `RESTfulRemoteException` — exception type is not a reliable "network vs. server-rejected" discriminator. Catch `RESTfulException` when both matter.
- **Interrupts are downgraded to `RESTfulIOException`** ("RESTful call interrupted") with the flag left as-is; broadly swallowing that exception loses the cancellation signal. Check `Thread.interrupted()` if cancellation matters — do not infer it from the exception type, which a `callTimeout` shares.
- **Response bodies are always fully materialized** via `body().string()`. Stream large downloads through `getHttpClient()` directly.
- The client is synchronous throughout; there is no async call path in this package. Async RPC lives in `utils.rpc.restful.RESTfulAsyncRpcClient`.
