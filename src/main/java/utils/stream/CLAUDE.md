# utils.stream

Pull-based, resource-aware functional stream API. `FStream<T> extends Iterable<T>, AutoCloseable`. Parallels `java.util.stream` but is lazier, closes deterministically, integrates with `FOption`/`Tuple`/`KeyValue`, and adds async parallel mapping. User-facing guide: [docs/stream-guide.md](../../../../../docs/stream-guide.md).

## Core types

- **`FStream<T>`** — The interface. Nearly the entire API is `default` methods on it; only `next()` and `close()` are abstract. `next()` returns `FOption<T>`; empty means end-of-stream and is idempotent. Calling `next()` after `close()` throws `IllegalStateException` (end-of-stream ≠ closed).
- **`KeyValueFStream<K,V> extends FStream<KeyValue<K,V>>`** — Key/value specialization: `keys`/`values`, `filterKey`/`filterValue`, `mapKey`/`mapValue`/`mapKeyValue`, `BiFunction` overloads of `map`/`flatMap`/`forEach`, `groupByKey`, `sortByKey`, `match`/`flatMatch`, `toMap`/`toListMap`. `DefaultKeyValueFStream` is the wrapper impl; `KeyValueFStreams` holds helpers.
- **`KeyedGroups<K,V>`** — `Map<K,List<V>>` wrapper produced by `groupByKey()`. `fstream()` → `KeyValueFStream<K,List<V>>`, `ungroup()` → `KeyValueFStream<K,V>`.
- **`IntFStream` / `LongFStream` / `FloatFStream` / `DoubleFStream` / `BooleanFStream`** — Primitive variants. Each adds `mapToObj`, `toArray`, and type-appropriate aggregates (`sum`/`average`, `maxValue`/`minValue`, `andAll`/`orAll`). Reached via `mapToInt`/`mapToLong`/… or `FStream.of(int[])`, `FStream.range`.
- **`SuppliableFStream<T>`** — Thread-safe bounded buffer bridging push producers to pull consumers. Implements `TimedFStream` (`next(timeout, unit)`) and `utils.Suppliable`.
- **`AsyncExecutionOptions`** — Immutable options for the async operators (`setXXX` returns a new instance). Defaults: `keepOrder=false`, `workerCount=max(1, cpus-2)`, `executor=null`, `timeoutMillis=-1`.
- **`FStreams`** — Public class, but the only public member is **`AbstractFStream<T>`**, the base class for custom streams. Everything else in it is package-private plumbing (`SingleSourceStream`, `MappedStream`, `LazyStream`, `CloserAttachedStream`, …).

## Close contract

1. Terminal operations (`forEach`, `count`, `toList`/`toSet`/`toCollection`, `reduce`/`fold`/`collect`, `findFirst`/`findLast`, `join`, `takeLast`, …) close the stream in a `finally` block — on both normal and exceptional exit.
2. Intermediate operations delegate `close()` to their source. Closing a mapped stream closes the original.
3. `close()` is idempotent; `closeQuietly()` wraps it in `Try<Void>`.
4. `onClose(Runnable)` appends a task run right after the source's close; multiple registrations run in registration order.
5. `AbstractFStream.close()` is `final` — subclasses implement `closeInGuard()`. `initialize()` (optional) runs once before the first `nextInGuard()`.

## Laziness — the exceptions matter

Most intermediates are lazy, but these are not, and that trips people up:

| Operation | When the source is consumed |
| --- | --- |
| `sort(...)` | **At the call site.** Implemented as `toList()` → sort → `from(list)`, so the source is drained and closed on that line. |
| `takeTopK(...)` | **At the call site.** `TopKPickedFStream`'s constructor runs `src.forEach(...)` into a `MinMaxPriorityQueue`. |
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
- **`FStream.mergeParallel(streamOfStreams, workerCount, executor)`** → `MergeParallelFStream`. Workers consume one inner stream to completion, then pull the next one from the factory (worker reuse).

**Async close semantics differ between the two map impls** — worth knowing before assuming cancel works:

| Impl | `close()` |
| --- | --- |
| `OrderedMapAsyncStream` | Closes channel + source. Does **not** propagate cancel; in-flight mappings run to completion and their results are silently dropped. |
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
- Korean Javadoc dominant; preserve language when modifying.

## Common gotchas

- **`for (T t : stream)` + `break` leaks the stream.** `FStreamIterator` closes the source only when iteration runs to exhaustion. Early exit must be wrapped in try-with-resources. `iterator()` also pulls the first element eagerly in its constructor.
- **Streams are single-use.** A stream consumed by any terminal is closed; the next terminal throws `IllegalStateException`. Materialize with `toList()` to reuse.
- **`mapAsync` swallows failures into `Try`.** Nothing forces the caller to inspect them.
- **Infinite streams** (`repeat`, `generate`, `unfold`) must be bounded with `take(n)` before `sort`/`shuffle`/`takeTopK`/`count`/`toList`.
- `FStream.of(T...)` vs `FStream.of(int[])` — the latter resolves to the `IntFStream` overload, not a one-element stream of `int[]`.
- The Javadoc on `mapAsync(Function, AsyncExecutionOptions)` claims the result elements are bare `Try` values; the actual return type is `FStream<Tuple<T,Try<S>>>`, same as the other `mapAsync` overloads.
