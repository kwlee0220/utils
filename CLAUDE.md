# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Java 17 utility library published as `etri:utils` via Gradle (`java-library` plugin). There is no main method — the output is a JAR consumed by other projects in `$HOME/development/common/…`. Source and Javadoc comments are primarily in Korean; match that language when modifying or adding doc comments near existing Korean docs.

## Common commands

```bash
gradle assemble                                    # compile + jar, no tests (matches README)
gradle build                                       # full build including tests
gradle test                                        # run all tests
gradle test --tests utils.stream.MapTest           # run one test class
gradle test --tests 'utils.stream.*'               # run a package of tests
gradle eclipse                                     # regenerate .project / .classpath for Eclipse import
gradle clean
```

Tests use JUnit 4 (`junit:junit:4.13.2`) with Mockito. The `test` task is configured with `useJUnitPlatform()`, so running JUnit 4 tests via Gradle relies on the vintage engine being resolvable — if a newly-added test does not execute, that's the likely cause (not a bug in the test).

Lombok is wired as both `compileOnly` and `annotationProcessor`. IDE setup requires the Lombok plugin, otherwise `@Getter`/`@Setter`/etc. generated members will appear missing.

## Architecture

The library is a collection of loosely-coupled packages under `utils.*`. There is no framework entry point — callers pick pieces. The most load-bearing packages, and how they relate:

- **`utils.func`** — Functional building blocks. `FOption<T>` (legacy Optional variant — see Conventions below; new code should use `java.util.Optional`), `Result<T>`, `Either<L,R>`, `Try`, `Lazy`/`ReloadableLazy`/`UnsafeLazy`, `FuncList`, `TailCall`. Also the `Checked*` / `Unchecked*` function families: `CheckedFunction`, `CheckedSupplier`, … and their `CheckedFunctionX<T,R,X extends Throwable>` parameterized-exception variants. `Unchecked.*` bridges checked lambdas into standard `java.util.function` interfaces. Almost every other package depends on this one.

- **`utils.stream`** — Custom pull-based stream API (`FStream<T>` implements `Iterable<T>` + `AutoCloseable`) that parallels `java.util.stream` but is lazier, resource-aware, and integrates with `FOption`, `KeyValue`, `Tuple`, and the async package. Primitive variants (`IntFStream`, `LongFStream`, `DoubleFStream`, `FloatFStream`, `BooleanFStream`), key-value streams (`KeyValueFStream`, `KeyedGroups`), async flat-map / map (`MapOrderedAsyncStream`, `FlatMapUnorderedAsyncStream`, `MergeParallelFStream`) and windowing/sampling (`QuasiSortedFStream`, `AdaptiveSamplingStream`, `TopKPickedFStream`) all live here. `FStreams` is the package-private implementation bucket imported heavily by `FStream`.

- **`utils.async`** — `Execution<T> extends Future<T>` with explicit `AsyncState`, `FinishListener`, `cancel(boolean)` semantics, and `map`/`flatMap` chaining. `AbstractAsyncExecution`, `AbstractThreadedExecution`, `EventDrivenExecution`, `CompletableFutureAsyncExecution`, `AbstractLoopExecution`, `PeriodicLoopExecution` are the main base classes. `Guard` is the canonical lock/condition wrapper; `Timer`, `StateChangePoller`, `SingleWaiterExecutor`, `LengthOneOverridingQueue` are the concurrency primitives used across the rest of the repo. Subpackages: `utils.async.op` (combinators — `SequentialAsyncExecution`, `ConcurrentAsyncExecution`, `FoldedAsyncExecution`, `TimedAsyncExecution`, `BackgroundedAsyncExecution`, plus the `AsyncExecutions` façade) and `utils.async.command` (external-process orchestration: `CommandExecution`, `ProgramService`, `ServiceShutdownHook`).

- **`utils.statechart`** — Hierarchical state machine (`StateChart`, `CompositeState`, `State`, `Signal`, `Transition`/`TransitionAction`). Used by `utils.websocket` (`WebSocketStateChart`, `WebSocketContext`) and by async work in downstream projects.

- **`utils.thread`** — `CamusExecutor` (thread-pool wrapper), `RecurringSchedule`/`RecurringScheduleThread`, `ConditionVariable`, `InterThreadShareSupplier`, `CompletionFStream`. Sits between `java.util.concurrent` and the `utils.async` API.

- **`utils.io`** — `FilePath` / `LfsPath`, `FileUtils`, `IOUtils`, `JarBuilder`, `ZipFile`, `LogTailer`, `SuppliableInputStream`, `Serializables`. Jackson-integrated helpers live in `utils.json` (`JacksonUtils`, `JacksonSerializable(s)`, `JacksonDeserializer`).

- **`utils.jdbc`** — `JdbcProcessor`, `JdbcConfiguration`, `ResultSetFStream` (SQL results as `FStream`), `SQLDataType(s)`, `JdbcRowSource`, plus a `crud` subpackage. `utils.jpa` is a thinner session/module wrapper (`JpaSession`, `JpaProcessor`, `JpaModule(Factory)`).

- **`utils.http`** — OkHttp-based REST client (`HttpRESTfulClient`, `OkHttpClientUtils`) with Jackson error-deserialization (`JacksonErrorEntityDeserializer`, `RESTfulErrorEntity`, `RESTfulRemoteException`).

- **Smaller packages** — `utils.rx` (RxJava3 `Observables`/`Flowables` helpers), `utils.xml` (fluent DOM wrappers), `utils.swing` (canvas/image helpers), `utils.jni` (JNI proxy base), `utils.fostore` (file-backed object store), `utils.websocket` (state-chart-driven WebSocket client).

- **Root `utils.*`** — value types and cross-cutting helpers used everywhere: `Tuple`/`Tuple3`/`Tuple4`, `KeyValue`, `Point2{i,l,f,d}` / `Size2{i,l,f,d}`, `CSV`, `CIString` (case-insensitive string key), `Instants`/`LocalDates`/`LocalDateTimes`/`LocalTimes`, `UnitUtils`, `NetUtils`, `ReflectionUtils`, `ProxyUtils`, `Throwables`, `Utilities` (argument-precondition helpers — `checkNotNullArgument`, `checkArgument`; the codebase uses these in place of Guava `Preconditions` by convention), `StopWatch`, `MovingAverage`, `LongVariableSmoother`, `FramePerSecondMeasure`. Picocli integration (`PicocliCommand`, `PicocliSubCommand`, `HomeDirPicocliCommand`, `Picoclies`, `UsageHelp`) is the standard CLI harness for downstream executables.

## Conventions worth preserving

- Argument validation uses `utils.Utilities.checkNotNullArgument` / `checkArgument` (often `import static`). Prefer these over Guava `Preconditions` in new code inside this library to stay consistent with neighbouring files.
- Exceptions wrap checked causes into the `Runtime*Exception` family in root (`RuntimeExecutionException`, `RuntimeInterruptedException`, `RuntimeTimeoutException`) rather than leaking checked types through public APIs.
- When a new lambda surface needs to throw checked exceptions, extend the `Checked*` / `Checked*X` family in `utils.func` rather than inventing a new functional interface.
- Streams and executions both implement `AutoCloseable` / expose `cancel` — respect the close/cancel contracts when composing them (e.g., `FStream.onClose`, `Execution` listeners).
- Prefer `java.util.Optional` over `utils.func.FOption` in new code. `FOption` is scheduled for deprecation; existing usages will be migrated incrementally. Do not introduce new public APIs that return or accept `FOption`.
