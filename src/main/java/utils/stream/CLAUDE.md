# utils.stream

Pull-based, resource-aware functional stream API. `FStream<T> extends Iterable<T>, AutoCloseable`. Parallels `java.util.stream` but is lazier, closes deterministically, integrates with `FOption`/`Tuple`/`KeyValue`, and adds async parallel mapping. User-facing guide: [README.md](README.md).

## Core types

- **`FStream<T>`** — The interface. Nearly the entire API is `default` methods on it; only `next()` and `close()` are abstract. `next()` returns `FOption<T>`; empty means end-of-stream and is idempotent. Calling `next()` after `close()` throws `IllegalStateException` (end-of-stream ≠ closed).
- **`KeyValueFStream<K,V> extends FStream<KeyValue<K,V>>`** — Key/value specialization: `keys`/`values`, `filterKey`/`filterValue`, `mapKey`/`mapValue` (one-arg `Function` only) and `mapKeyWithValue`/`mapValueWithKey` (`BiFunction`; renamed from the `mapKey`/`mapValue` overloads on 2026-11-02 because overloaded method refs like `mapKey(Objects::toString)` were ambiguous — no alias), `mapKeyValue`, `BiFunction` overloads of `map`/`flatMap`/`forEach` (still overloaded: `forEach(Objects::requireNonNull)` stays ambiguous), `groupByKey`, `sortByKey`, `match`/`flatMatch`, `toMap`/`toListMap`. `DefaultKeyValueFStream` is the wrapper impl; `KeyValueFStreams` holds helpers.
- **`KeyedGroups<K,V>`** — `Map<K,List<V>>` wrapper produced by `groupByKey()`. `fstream()` → `KeyValueFStream<K,List<V>>`, `ungroup()` → `KeyValueFStream<K,V>`. Backed by a `LinkedHashMap` (groups in first-appearance order). `asMap()` (live view; values wrapped with `Maps.transformValues(..., unmodifiableList)`), `keySet()`, `get()`, `getOrDefault()` are read-only; only `add`/`remove` mutate. `fstream()` deliberately exposes the **internal** lists — marmot `InMemoryKeyedRecordSetFactory.sortValues` sorts them in place. `size()` = total value count, `groupCount()` = number of keys. Null values are allowed. Before 2026-11-02 `asMap()`/`get()` leaked the internal `HashMap`/lists.
- **`IntFStream` / `LongFStream` / `FloatFStream` / `DoubleFStream` / `BooleanFStream`** — Primitive variants (elements are still boxed). Each adds `mapToObj`, `toArray`, and type-appropriate aggregates (`sum`/`average`, `maxValue`/`minValue`, `andAll`/`orAll`). Reached via `mapToInt`/`mapToLong`/… or `FStream.of(int[])`, `FStream.range`. Since 2026-11-02 the four numeric ones share one shape: `of(prim...)`, `from(Boxed[])` (renamed from the ambiguous `of(Boxed...)`, no alias; marmot's 6 call sites were updated), `from(Iterable)`, `downcast(FStream)`, `maxValue`/`minValue`, `sum`, `average`, `toArray`; Int and Long also have `range`. `FloatFStream.sum()` accumulates in `double`. Null elements make the numeric ops throw NPE (documented, not handled).
- **`SuppliableFStream<T>`** — Thread-safe bounded buffer bridging push producers to pull consumers. Implements `TimedFStream` (`next(timeout, unit)`) and `utils.Suppliable`.
- **`AsyncExecutionOptions`** — Immutable options for the async operators (`setXXX` returns a new instance). Defaults: `keepOrder=false`, `workerCount=max(1, cpus-2)`, `executor=null`, `timeoutMillis=-1`.
- **`FStreams`** — Public class, but the only public member is **`AbstractFStream<T>`**, the base class for custom streams. Everything else in it is package-private plumbing (`SingleSourceStream`, `MappedStream`, `LazyStream`, `CloserAttachedStream`, …).

## Close contract

1. Terminal operations (`forEach`, `count`, `toList`/`toSet`/`toCollection`, `reduce`/`fold`/`collect`, `findFirst`/`findLast`, `join`, `takeLast`, …) close the stream in a `finally` block — on both normal and exceptional exit.
2. Intermediate operations delegate `close()` to their source. Closing a mapped stream closes the original.
3. `close()` is idempotent; `closeQuietly()` wraps it in `Try<Void>`.
4. `onClose(Runnable)` appends a task run right after the source's close; multiple registrations run in registration order. The task runs even if the source's close throws (its own exception is then added as suppressed), and `next()` after close throws `IllegalStateException`. Until 2026-11-02 `CloserAttachedStream.close()` ran only the task and never closed the source.
5. `AbstractFStream.close()` is `final` — subclasses implement `closeInGuard()`. `initialize()` (optional) runs once before the first `nextInGuard()`.

## Laziness — the exceptions matter

Most intermediates are lazy, but these are not, and that trips people up:

| Operation | When the source is consumed |
| --- | --- |
| `sort(...)` | **At the call site.** Implemented as `toList()` → sort → `from(list)`, so the source is drained and closed on that line. |
| `takeTopK(...)` | **On the first `next()`** (since 2026-11-02; before, the constructor drained the source at the call site). `TopKPickedFStream` drains the source with `forEach` into a size-k `MinMaxPriorityQueue`; `k == 0` never reads it. Natural/keyer overloads default to `SortOrder.DESCENDING` (largest k, largest first); `takeTopK(k, Comparator)` keeps "first k in comparator order" = `sort(cmp).take(k)` because marmot passes reversed comparators to it (8 call sites). The `boolean reverse` overloads were removed. |
| `sort(...)` | At the call site (`toList()` then `List.sort`). Direction: `sort()` / `sort(keyer)` ascending, `sort(SortOrder)` / `sort(keyer, SortOrder)`; `quasiSort(n[, keyer][, SortOrder])` likewise defaults to ascending. The `boolean reverse` overloads of `sort`/`quasiSort` were replaced by `SortOrder` on 2026-11-02 (no alias; only a test used them). Shared comparators: private static `naturalComparator(order)` / `keyComparator(keyer, order)` in `FStream`. |
| `shuffle()` | On first `next()` — `toList()` then random removal. |
| `dropLast(n)` / `takeLast(n)` | Requires read-ahead buffering of n elements. |
| `quasiSort(len)` | Lazy; only `len` elements are buffered in a `PriorityQueue`, so it works on infinite streams. This is the streaming alternative to `sort()`. |
| `mapAsync` / `flatMapAsync` / `mergeParallel` | Lazy to the consumer, but up to `workerCount` mappings are started ahead of demand. |

`distinct()` is lazy but accumulates every seen element in a `HashSet`; `unique()` only collapses *adjacent* duplicates (Unix `uniq`) and is O(1) in memory.

## Async operators

- **`mapAsync(mapper, options)` / `mapCheckedAsync(...)`** — Returns `FStream<Tuple<T,Try<S>>>`: the input element paired with a `Try`-wrapped result. **Mapper exceptions never terminate the stream** — they surface as `Try.failure`. Dispatches on `options.getKeepOrder()`:
  - `true` → `OrderedMapAsyncStream`: input order preserved by queueing `StartableExecution` handles in order; one slow mapping blocks everything behind it (head-of-line blocking).
  - `false` → `UnorderedMapAsyncStream`: emits in completion order; a worker picks up the next input as soon as it finishes.
- **`flatMapAsync(mapper, options)` / `flatMapCheckedAsync(...)`** — `keepOrder=true` reduces to `mapAsync` + ordered flattening; `keepOrder=false` routes to `mergeParallel`, interleaving elements from concurrently-consumed sub-streams.
- **`FStream.mergeParallel(streamOfStreams, workerCount, executor)`** → `MergeParallelFStream`. Workers consume one inner stream to completion, then pull the next one from the factory (worker reuse). A `null` executor means the common `ForkJoinPool` (via `Executions.toExecution`), same as the map operators — the Javadoc used to claim a new `Thread` per task.
- **`KeyValueFStream.flatMatch(lut, keepUnmatched)`** (not async; noted here with the other join-likes) — one-to-many join. An element whose reference list is empty, `null`, or missing is "unmatched" in all three cases: 0 rows for `keepUnmatched=false` (inner), one `(v, null)` row for `true` (left outer). Implemented directly with `flatMap` (no longer via `match`), iterating the list once per element. Before 2026-11-02 a `null` list matched (a `(v, null)` row even for inner) and an empty list vanished even for outer.
- **`flatMapAsync` vs `flatMapCheckedAsync`** differ only in the mapper type (`Function` vs `CheckedFunction`; checked exceptions reach the consumer sneaky-thrown). `keepOrder=true` parallelizes only the mapper calls and reads inner streams sequentially on the consumer thread; `keepOrder=false` parallelizes reading too (`mergeParallel`). With `keepOrder=true`, closing the result closes the inner stream being read, the ones created but not yet read, and (asynchronously, when the mapper finishes) the ones still being created at close time — via `OrderedMapAsyncStream`'s package-private `onDiscarded` hook, which `flatMapAsync` sets to `FStream::closeQuietly` (review #26, 2026-11-02; before, those were leaked). `mapAsync` passes no hook.
- **Failure propagation (flatMap/merge, since 2026-11-02).** Unlike `mapAsync`, these propagate: a mapper failure, a failure while reading an inner stream, or a failure pulling the next inner stream reaches the consumer as the **unwrapped** original exception (sneaky for checked ones) after the already-buffered elements. `keepOrder=true` does `new OrderedMapAsyncStream<>(..., FStream::closeQuietly).flatMap(t -> t._2().get())` (`Try.get()` sneaky-throws). `MergeParallelFStream` records the first failure (`m_failure`) under its guard via `failInGuard`, ends the output channel with `endOfSupply(e)`, stops starting inner streams, and `nextInGuard` unwraps the channel's `RuntimeException` back to `m_failure`; later failures, and push failures after the consumer closed (`m_closed`), are ignored. A worker interrupt is a failure too. Before, `flatMapTry` dropped failed inputs and a failed worker just moved on (`whenFinished → startNext`), silently losing results.

**Async close semantics differ between the two map impls** — worth knowing before assuming cancel works:

| Impl | `close()` |
| --- | --- |
| `OrderedMapAsyncStream` | Closes channel + source. Does **not** propagate cancel; in-flight mappings run to completion and their results are dropped. It tracks started-but-undelivered executions (`m_undelivered`); with an `onDiscarded` hook it registers `whenFinishedAsync` on each at close so every dropped *successful* result is handed to the hook once (hook failures are logged). After close it starts no new mappings, and a `next()` racing a close returns empty instead of a result the hook will also clean up. |
| `UnorderedMapAsyncStream` | Closes channel + source and propagates `cancel(true)`. But `CompletableFuture.cancel` does not interrupt a running supplier thread, so an uncooperative mapper still runs to completion. |

Either way, results arriving after close are dropped silently. A mapper holding external resources must clean up after itself.

## Push → pull bridges

- **`SuppliableFStream<T>`** — `supply`/`next` block on full/empty; `poll()` is the non-blocking read; `setSupplyListener(Runnable)` for async notification. Two termination paths: producer-side `endOfSupply()` / `endOfSupply(Throwable)` (already-buffered data stays consumable; an error is rethrown wrapped in `RuntimeException` after the buffer drains) and consumer-side `close()` (discards buffered data; later `supply` throws `IllegalStateException`). Producer-side termination calls are **idempotent — first one wins**, so `endOfSupply()` followed by `endOfSupply(error)` loses the error.
- **`Generator<T>` + `FStream.generate(gen, bufLength)` / `asynchronouslyFrom(gen, len, threadName)`** → `GeneratorBasedFStream`. The generator thread starts on the first `next()`. **Single consumer only** — concurrent `next()`/`close()` is unsynchronized and races.
- **`PrependableFStream<T>`** — `toPrependable()`; adds `prepend(v)`, `peekNext()`, `hasNext()`, `forEachWhile(pred, effect)` for lookahead parsing.

## Implementation classes (package-private)

Reached only through `FStream` methods, never instantiated directly: `OrderedMapAsyncStream`, `UnorderedMapAsyncStream`, `FlatMapUnorderedAsyncStream`, `MergeParallelFStream`, `QuasiSortedFStream`, `ShuffledFStream`, `SlicedFStream`, `ZippedFStream`, `ConcatedStream`, `BufferedStream`, `MatchFStream`, `MatchKVFStream`, `GeneratorBasedFStream`, `FStreamIterator`, `InnerJoinedFStream`, `OuterJoinedFStream`. Public but rarely constructed directly: `AdaptiveSamplingStream`, `TopKPickedFStream`.

**`InnerJoinedFStream` / `OuterJoinedFStream` are currently unreachable** — the only entry point, `KeyValueFStream.outerJoin`, is commented out (KeyValueFStream.java:181). Join-like work goes through `match`/`flatMatch`/`lookup` instead. Don't document them as available API.

## Conventions

- Argument validation uses `utils.Preconditions.checkNotNullArgument` / `checkArgument`. Note that `KeyValueFStream` and `AsyncExecutionOptions` still use Guava `Preconditions` in places — new code in this package should use `utils.Preconditions`.
- Return `FOption`, not `null`, for optional single results. `max`/`min` are the exceptions — they return `java.util.Optional`.
- New intermediate operations should be `default` methods on `FStream` that return a `FStreams.SingleSourceStream` subclass (or an anonymous one) so close-delegation comes for free.
- Use PECS variance on functional and stream parameters (`Function<? super T, ? extends FStream<? extends V>>`, `FStream<? extends T>`, `Map<? super K, ? extends R>`, `M extends Map<? super K, ? super V>`). Widened in review #13 (2026-11-02); internally, `FStream<? extends T>` is narrowed to `FStream<T>` by the private `narrow(...)` helpers in `FStream`/`KeyValueFStream` (safe: streams are producer-only, `KeyValue` is immutable). `toArray(Class<S>)` cannot be restricted (Java has no lower-bounded method type parameter) — a wrong component type still compiles and throws `ArrayStoreException`.
- Korean Javadoc dominant; preserve language when modifying.

## Common gotchas

- **`for (T t : stream)` + `break` leaks the stream.** `FStreamIterator` closes the source only when iteration reaches the end (an empty stream included). Early exit (break, exception) must be wrapped in try-with-resources. Elements are read lazily in `hasNext()` and `null` elements are returned as-is; until 2026-11-02 the iterator pre-read in its constructor and in `next()`, treated `null` as end-of-stream (so `[a, null, c]` iterated and `stream()`-ed as `[a]`), and never closed an initially empty stream.
- **Streams are single-use.** A stream consumed by any terminal is closed; the next terminal throws `IllegalStateException`. Materialize with `toList()` to reuse.
- **`mapAsync` swallows failures into `Try`.** Nothing forces the caller to inspect them.
- **`mapOrThrow` / `mapToKeyValueOrThrow` sneaky-throw.** The checked `X` is thrown unwrapped when an element is pulled (`next()` or a terminal op), but terminal ops do not declare `throws X`, so `catch (X e)` compiles only if another call in the same `try` declares `X`. Callers either declare `throws X` on the enclosing method or catch `Exception`/`Throwable` and test with `instanceof` / `Throwables.throwIfInstanceOf` (MDT's `RESTfulRpcTask` does the latter for `InterruptedException`). Unlike `forEachOrThrow`, "OrThrow" here does not mean a declared `throws`. Documented, not renamed (review #6 option b, 2026-11-02); a rename is deferred to the failure-handling naming scheme.
- **Infinite streams** (`repeat`, `generate`, `unfold`) must be bounded with `take(n)` before `sort`/`shuffle`/`takeTopK`/`count`/`toList`.
- `FStream.of(T...)` vs `FStream.of(int[])` — the latter resolves to the `IntFStream` overload, not a one-element stream of `int[]`.
