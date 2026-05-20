# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Java 17 utility library published as `etri:utils` (current version `25.10.27`) via Gradle (`java-library` plugin). There is no main method — the output is a JAR consumed by other projects in `$HOME/development/common/…`. Source and Javadoc comments are primarily in Korean; match that language when modifying or adding doc comments near existing Korean docs.

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

Tests use JUnit 4 (`junit:junit:4.13.2`) with Mockito. The `test` task is configured with `useJUnit()` and passes `--add-opens=java.base/java.lang=ALL-UNNAMED` for reflection-heavy tests (Mockito, cglib). Do not switch the runner to `useJUnitPlatform()` — there is no vintage-engine dependency.

Lombok is wired as both `compileOnly` and `annotationProcessor`. IDE setup requires the Lombok plugin, otherwise `@Getter`/`@Setter`/etc. generated members will appear missing.

## Architecture

The library is a collection of loosely-coupled packages under `utils.*`. There is no framework entry point — callers pick pieces. The most load-bearing packages, and how they relate:

- **`utils.func`** — Functional building blocks. `FOption<T>`, `Result<T>`, `Either<L,R>`, `Try`, `Lazy`/`ReloadableLazy`/`UnsafeLazy`/`TimedLazy`, `FuncList`, `Slice`, `TailCall`, `Optionals`, `Funcs`, `MultipleCases`, `FailureHandler(s)`. Also the `Checked*` / `Unchecked*` function families: `CheckedFunction`, `CheckedSupplier`, … and their `CheckedFunctionX<T,R,X extends Throwable>` parameterized-exception variants. `Unchecked.*` bridges checked lambdas into standard `java.util.function` interfaces. Almost every other package depends on this one.

- **`utils.stream`** — Custom pull-based stream API (`FStream<T>` implements `Iterable<T>` + `AutoCloseable`) that parallels `java.util.stream` but is lazier, resource-aware, and integrates with `FOption`, `KeyValue`, `Tuple`, and the async package. Primitive variants (`IntFStream`, `LongFStream`, `DoubleFStream`, `FloatFStream`, `BooleanFStream`), key-value streams (`KeyValueFStream`, `KeyedGroups`), async map / flat-map (`OrderedMapAsyncStream`, `UnorderedMapAsyncStream`, `FlatMapUnorderedAsyncStream`, `MergeParallelFStream`), join (`InnerJoinedFStream`, `OuterJoinedFStream`), and windowing / sampling (`QuasiSortedFStream`, `AdaptiveSamplingStream`, `TopKPickedFStream`, `BufferedStream`, `TimedFStream`, `ShuffledFStream`) all live here. `FStreams` is the package-private implementation bucket imported heavily by `FStream`; `AsyncExecutionOptions` configures the async stream operators.

- **`utils.async`** — `Execution<T> extends Future<T>` with explicit `AsyncState`, `whenStarted`/`whenFinished` listeners, `cancel(boolean)` semantics, and `map`/`flatMap` chaining. `AbstractAsyncExecution`, `AbstractThreadedExecution`, `EventDrivenExecution`, `CompletableFutureAsyncExecution`, `AbstractLoopExecution`, `PeriodicLoopExecution`, `AbstractLoopThreadService` are the main base classes; `AbstractPeriodicPoller` / `PeriodicPoller` implement "poll until target state observed". `AsyncResult`, `AsyncState`, `Startable`/`StartableExecution`, `Cancellable`/`CancellableWork` are the supporting types. `AsyncUtils` and `Executions` are the static-factory façades; `FStreamExecutor` bridges `utils.stream.FStream` into async execution. Subpackages: `utils.async.op` (combinators — `SequentialAsyncExecution`, `ConcurrentAsyncExecution`, `FlatMapAsyncExecution`, `FoldedAsyncExecution`, `TimedAsyncExecution`, `DelayedAsyncExecution`, `BackgroundedAsyncExecution`, plus the `AsyncExecutions` façade) and `utils.async.command` (external-process orchestration: `CommandExecution`, `CommandVariable`, `ProgramService`/`ProgramServiceConfig`, `ServiceShutdownHook`). See [src/main/java/utils/async/CLAUDE.md](src/main/java/utils/async/CLAUDE.md) for deeper detail on lifecycle and cancellation.

- **`utils.statechart`** — Hierarchical state machine (`StateChart`, `CompositeState`/`DefaultCompositeState`, `State`/`AbstractState`/`SingleOutState`/`SinkState`/`ExceptionState`, `Signal`, `Transition`/`TransitionAction`/`Transitions`, `StateContext`). Used by `utils.websocket` (`WebSocketStateChart`, `WebSocketContext`) and by async work in downstream projects. See [src/main/java/utils/statechart/CLAUDE.md](src/main/java/utils/statechart/CLAUDE.md) and [docs/statechart-guide.md](docs/statechart-guide.md).

- **`utils.thread`** — `CamusExecutor` (thread-pool wrapper), `RecurringSchedule`/`RecurringScheduleThread`, `ConditionVariable`, `InterThreadShareSupplier`, `CompletionFStream`, `AsyncConsumer`, and the concurrency primitives shared across the repo: `Guard` (canonical `ReentrantLock` + `Condition` wrapper — `awaitCondition(predicate).andReturn()` / `.andRunChecked(task)` is the standard wait pattern), `Timer` (used by `Execution.setTimeout`), `SingleWaiterExecutor` (latest-wins single-slot executor), `LengthOneOverridingQueue` (single-slot blocking queue where new offers overwrite the pending element). Sits between `java.util.concurrent` and the `utils.async` API.

- **`utils.io`** — `FilePath` / `LfsPath`, `FileUtils`, `IOUtils`, `JarBuilder`, `ZipFile`, `LogTailer`/`LogTailerListener`, `SuppliableInputStream`, `LimitedInputStream`, `SequenceInputStream`, `InputStreamFromOutputStream`, `Serializables`, `TempFile`, `ResourceUtils`, `EnvironmentFileLoader`. Jackson-integrated helpers live in `utils.json` (`JacksonUtils`, `JacksonSerializable(s)`, `JacksonDeserializer`).

- **`utils.jdbc`** — `JdbcProcessor`, `JdbcConfiguration`, `ResultSetFStream` (SQL results as `FStream`), `ResultSetSpliterator`, `SQLDataType(s)`, `JdbcRowSource`, `JdbcParameters`, `JdbcObjectIterator`, `JdbcUtils`, `FullJdbcUrlParser`, `GeometryFormat`, `DriverDelegate`, plus a `crud` subpackage (`JdbcCRUDOperation`, `JdbcSQLCRUDOperation`, `JdbcDaoListCRUDOperation`, `TableBinding(s)`, `TableBindingSQLCRUDOperation`, `TableBindingCRUDOperation`, `DaoList`). `utils.jpa` is a thinner session/module wrapper (`JpaSession`/`JpaSessionFactory`, `JpaProcessor`, `JpaContext`, `JpaModule`/`JpaModuleFactory`).

- **`utils.http`** — OkHttp-based REST client (`HttpRESTfulClient`, `HttpClientProxy`, `OkHttpClientUtils`) with Jackson error-deserialization (`JacksonErrorEntityDeserializer`, `JacksonResponseBodyDeserializer`, `RESTfulErrorEntity`, `RESTfulRemoteException`, `RESTfulIOException`, `RESTfulServerErrorMessage`, `SpringExceptionEntity`).

- **Smaller packages** — `utils.rx` (RxJava3 `Observables`/`Flowables` helpers plus `ExecutionProgress`/`ExecutionProgressReport`), `utils.xml` (fluent DOM wrappers — `FluentDocument`, `FluentElement`, `FluentNode`, `XmlUtils`, `XmlSerializable`/`LoadableXmlSerializable`, `XmlWrite`), `utils.swing` (canvas/image helpers — `Convas`, `BufferedImageConvas`, `JFrameConvas`, `ImageView`, `ImageUtils`, `SwingUtils`), `utils.jni` (`AbstractJniObjectProxy`, `JniObjectProxy`, `JniUtils`, `JniRuntimeException`), `utils.fostore` (file-backed object store — `FileObjectStore`, `DefaultFileObjectStore`, `CachingFileObjectStore`, `FileObjectHandler`, `FileObjectReader`, `FileObjectVisitor`), `utils.websocket` (state-chart-driven WebSocket client — `WebSocketStateChart`, `WebSocketContext`, `WebSocketObservable`, `WebSocketListener`, `WebSocketSignal`, `States`, `Signals`; see [docs/websocket-guide.md](docs/websocket-guide.md)).

- **Root `utils.*`** — value types and cross-cutting helpers used everywhere: `Tuple`/`Tuple3`/`Tuple4`/`ITuple`/`ITuple3`, `KeyValue`/`ComparableKeyValue`/`KeyedValueList`/`Keyed`/`Keyeds`, `Point2{i,l,f}` / `Size2{i,l,f,d}`, `CSV`, `CIString` / `CIStringMap`, `ChainedMap`, `Holder`, `Indexed`, `Named`, `Timestamped`, `Permutation`, `Split`/`SplitStream`, `StrSubstitutor`, `Suppliable`, `Instants`/`LocalDates`/`LocalDateTimes`/`LocalTimes`, `UnitUtils`, `NetUtils`, `ReflectionUtils`, `ProxyUtils`, `Throwables`, `Utilities`, `DataUtils`, `Preconditions` (argument-precondition helpers — `checkNotNullArgument`, `checkArgument`, `checkState`, `checkNotNull`; the codebase uses these in place of Guava `Preconditions` by convention), `StopWatch`, `MovingAverage`, `LongVariableSmoother`, `FramePerSecondMeasure`, `ProcessTree`, `CallHandler`, `Initializable`/`NotReadyException`/`UninitializedException`, `LoggerSettable`/`LoggerNameBuilder`/`LogbackConfigLoader`, `InternalException`, `RuntimeExecutionException`/`RuntimeInterruptedException`/`RuntimeTimeoutException`. Picocli integration (`PicocliCommand`, `PicocliSubCommand`, `HomeDirPicocliCommand`, `Picoclies`, `UsageHelp`) is the standard CLI harness for downstream executables.

## Conventions worth preserving

- Argument validation uses `utils.Preconditions.checkNotNullArgument` / `checkArgument` / `checkState` / `checkNotNull` (often `import static`). Prefer these over Guava `Preconditions` in new code inside this library to stay consistent with neighbouring files.
- Exceptions wrap checked causes into the `Runtime*Exception` family in root (`RuntimeExecutionException`, `RuntimeInterruptedException`, `RuntimeTimeoutException`) rather than leaking checked types through public APIs.
- When a new lambda surface needs to throw checked exceptions, extend the `Checked*` / `Checked*X` family in `utils.func` rather than inventing a new functional interface.
- Streams and executions both implement `AutoCloseable` / expose `cancel` — respect the close/cancel contracts when composing them (e.g., `FStream.onClose`, `Execution` listeners).
