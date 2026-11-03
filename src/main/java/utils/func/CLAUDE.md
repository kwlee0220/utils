# utils.func

Functional building blocks used by almost every other package. User guide (Korean): [README.md](README.md) — type selection, `FOption`/`Nullables`, `Try`/`Result`, lazy values, checked lambdas, naming and exception rules. The package was reshaped by a review on 2026-11-03 (tracking doc `docs/reviews/func-review.md`, git-ignored and local only); the outcomes are recorded here.

## Contracts worth keeping

- **Exception rule.** `Exception`s are captured (`Try.of/run/accept/map/flatMap`, `Result.map/filter/flatMap`, `Checked*.try*`, `Unchecked*` wrappers' failure handlers); `Error`s always propagate and are never passed to a `FailureHandler`. `Checked*X` (where `X` may be any `Throwable`) catch `Throwable` and rethrow `Error`. Checked causes are wrapped once, as plain `RuntimeException` (`Throwables.toRuntimeException`) — `Unchecked.*OrRTE`, `Try.getUnchecked`, `Result.getUnchecked` (no `ExecutionException` layer).
- **Null rule.** `FOption.of(null)` is a *present* `null`; only `empty()` is absent. `Nullables` treats `null` as absent. `Lazy`/`UnsafeLazy` reject a `null` supplier result (`IllegalStateException`); `TimedLazy` caches `null` and `peek()` returns `FOption.of(null)` for it. `Result.failure(null)` is allowed (`EventDrivenExecution.notifyFailed(null)` produces it); `Try.failure(null)` is rejected.
- **Argument validation.** Arguments that are always used (consumer of `ifPresent`/`ifNotNull`, `map`'s mapper) are checked always; fallback arguments (supplier of `getOrElse`/`orElseGet`, action of `ifAbsent`/`ifNull`) only when used. Always `utils.Preconditions` (→ `IllegalArgumentException`).
- **Value semantics.** `FOption`, `Result`, `Either`, `Try` define `equals`/`hashCode` (`Success` by value, `Failure` by cause — exceptions don't override `equals`, so effectively the same exception object). `utils.stream.MappingResult` still has none.
- **Naming.** `getOrThrow(Supplier<X>)` throws the supplied exception; an `…OrThrow` suffix means "takes a `Checked*X` lambda" (`mapOrThrow`, `ifPresentOrThrow`, `getOrElseThrow`, `ifNotNullOrThrow`). `Nullables` follows JDK `orElse`/`orElseGet`. Overloads that differ only by `T` vs `Supplier`/`Runnable` vs `Consumer` must be avoided for method-reference callers — that is why `Nullables.ifNotNullRun` is not an `ifNotNull` overload. `getOrElse(T)`/`getOrElse(Supplier)` overloads on `FOption`/`Try`/`Result` were kept deliberately (`getOrElse(null)` is ambiguous; use `getOrNull()`).
- **`Lazy.wrap`** is a JDK dynamic proxy (interfaces only, proxy class cached, non-public interfaces OK). Object methods do not trigger the supplier (`equals` = proxy identity). Don't go back to ByteBuddy/cglib for it.
- **`TimedLazy.get()`** re-checks under the lock, so concurrent expiry reloads once; `reload()` always reloads.

## Usage and rename cost

There are no aliases; renames are migrated across utils, mdt-*, marmot*, jarvey* (marmot/jarvey can't be built with gradle here — use javac over each root's sources with the gradle-resolved external classpath and diff against a pre-change error list; grep alone misses same-named methods on other types). Heavily used surface (approximate call sites outside utils.func): `Result` (~430), `FOption` (everywhere), `Nullables` (~150, all `Nullables.x(` qualified), `Unchecked.*` (~115), `Lazy.of` (~55), `Funcs.*` (~55 files), `MultipleCases` (~40), `Either` (~40, mostly marmot), `Nullables.transform` (~43, marmot).

## Renames and removals on 2026-11-03 (no aliases)

- `FOption`: `flatMapNullable`→`mapNotNull`, `transform`→`fold`, `orElse(FOption)`/`orElse(Supplier)`→`or`; removed `gtz`, `getUnchecked` (use `getOrNull`), `peek` (use `ifPresent`); static nullable helpers moved to `Nullables`.
- `Nullables` (new) absorbed `FOption` statics and all of `Optionals` (deleted): `getOrElse`→`orElse`/`orElseGet`, `ifPresent`/`accept`→`ifNotNull`, `run`→`ifNotNullRun`, `acceptOrThrow`→`ifNotNullOrThrow`, `ifAbsent`→`ifNull`, `mapOrSupply`→`mapOrElseGet`; `getOrElseThrow` kept.
- `Result`: `none/isNone/ifNone`→`cancelled/isCancelled/ifCancelled`, `getOrElseThrow`→`getOrThrow`, `asTry`→`toTry`. `Try`: static `get`→`of`, `lift`→`liftSupplier`/`liftRunnable`/`liftFunction`.
- `FailureHandlers.throwSneakly`→`throwSneakily`, `Counting/CollectingErrorHandler`→`Counting/CollectingFailureHandler`, `FailureCase.getFailureCause`→`getCause`. `MultipleCases.switching/ifCase/consume`→`of/when/then`. `Lazy.of(T)`→`ofValue`, `unload`→`invalidate`, `set` returns `void`. `TimedLazy.peekUnchecked`→`peekOrNull`. `Either<T1,T2>`→`Either<L,R>`; `left()`/`right()` return `FOption`.
- Removed: `FuncList`, `TailCall`, `ReloadableLazy`, `Optionals`, `UnsafeLazy.wrap`, value-returning `Unchecked*.ignore`, `Checked*` conversion defaults (`toSneakyThrowFunction`, `toSneakyThrowSupplier`, `toRunnable`, `fromConsumer` — use `Unchecked*`), unused `Funcs` methods (`all`, `foldLeft`, `reduce`, `slice`, `union`, joins, `argmax`/`argmin`).

## Tests

JUnit 5. `ExceptionPolicyTest` (exception rule), `FuncVarianceTest` (compile-time variance checks), `LazyTest`, `TimedLazyTest`, `FOptionTest`, `NullablesTest`, `ResultTest`, `TryTest`, `MultipleCasesTest`, `FuncsTest`/`FuncTest`, `UncheckedWrapperTest`, `FailureHandlersTest`.
