# `utils.thread` 사용자 가이드

`java.util.concurrent`와 [`utils.async`](../src/main/java/utils/async/CLAUDE.md) 사이에 위치하는 동시성 유틸리티 모음. lock/condition 래퍼, 주기 실행 스케줄러, 실행기(executor) 래퍼, 쓰레드 간 데이터 전달 헬퍼를 제공한다.

이 가이드는 [`Guard`](../src/main/java/utils/thread/Guard.java)를 중심으로 패키지 사용법을 설명한다.

---

## 1. 개요

패키지의 클래스는 역할별로 네 갈래다.

| 갈래 | 타입 | 요약 |
|---|---|---|
| **동기화** | [`Guard`](../src/main/java/utils/thread/Guard.java), [`ConditionVariable`](../src/main/java/utils/thread/ConditionVariable.java) | lock + condition 패턴의 표준 래퍼 |
| **주기 실행** | [`RecurringSchedule`](../src/main/java/utils/thread/RecurringSchedule.java), [`RecurringScheduleThread`](../src/main/java/utils/thread/RecurringScheduleThread.java), [`RecurringScheduleImpl`](../src/main/java/utils/thread/RecurringScheduleImpl.java), [`RecurringWork`](../src/main/java/utils/thread/RecurringWork.java), [`InterruptableWork`](../src/main/java/utils/thread/InterruptableWork.java) | 고정 주기/고정 지연 반복 작업 |
| **실행기** | [`CamusExecutor`](../src/main/java/utils/thread/CamusExecutor.java), [`CamusExecutorImpl`](../src/main/java/utils/thread/CamusExecutorImpl.java), [`ExecutorAware`](../src/main/java/utils/thread/ExecutorAware.java), [`AsyncConsumer`](../src/main/java/utils/thread/AsyncConsumer.java), [`SingleWaiterExecutor`](../src/main/java/utils/thread/SingleWaiterExecutor.java), [`Timer`](../src/main/java/utils/thread/Timer.java) | 쓰레드 풀 래퍼와 특수 실행 정책 |
| **쓰레드 간 전달** | [`LengthOneOverridingQueue`](../src/main/java/utils/thread/LengthOneOverridingQueue.java), [`CompletionFStream`](../src/main/java/utils/thread/CompletionFStream.java), [`InterThreadShareSupplier`](../src/main/java/utils/thread/InterThreadShareSupplier.java) | 값·결과 전달 채널 |

이 중 **`Guard`가 압도적으로 많이 쓰인다** — 라이브러리 내부 12개 파일, 다운스트림 MDT 프로젝트 30개 파일에서 사용된다. 나머지는 필요할 때 꺼내 쓰는 독립 유틸리티에 가깝다.

---

## 2. `Guard` — lock + condition 표준 래퍼

`ReentrantLock` 하나와 그 위의 `Condition` 하나를 쌍으로 보유한다. 사용 모드는 두 가지다.

```java
private final Guard m_guard = Guard.create();
@GuardedBy("m_guard") private State m_state = State.NOT_READY;
```

### 2.1 작업 실행 모드 — lock 획득 + 자동 signal

`run`/`get`/`accept`는 lock을 자동으로 획득·해제하고, **작업이 끝난 뒤 `finally`에서 `signalAll()`을 자동 호출**한다. 공유 상태를 갱신하고 대기 쓰레드를 깨우는 가장 일반적인 경로다.

```java
m_guard.run(() -> m_state = State.READY);          // 갱신 + 자동 signalAll
State s = m_guard.get(() -> m_state);              // 읽기
m_guard.accept(this::setStateInGuard, State.DONE); // 값 전달

// checked 예외를 던지는 작업
m_guard.runChecked(() -> flushToDisk());           // throws X
int n = m_guard.getChecked(() -> readCount());     // throws X
```

> 읽기 전용 작업에도 `signalAll()`이 호출된다. 비용이 문제되는 hot path라면 `lock()`/`unlock()`을 직접 쓴다.

### 2.2 조건 대기 모드 — "조건 대기 → 작업 → signal"

`awaitCondition(...)`이 반환하는 빌더로 표현한다. **조건이 `true`가 될 때까지 대기**한다.

```java
// 조건 만족까지 대기만
m_guard.awaitCondition(() -> m_state == State.READY).andReturn();

// 조건 만족 후 작업 수행 (작업 후 signalAll 자동 호출)
m_guard.awaitCondition(() -> m_queue.size() > 0)
       .andRun(() -> m_queue.poll());

T value = m_guard.awaitCondition(() -> m_value != null)
                 .andGet(() -> m_value);

// checked 예외를 던지는 작업
m_guard.awaitCondition(() -> isReady()).andRunChecked(() -> commit());
```

시간 제한을 두려면 `Date due` / `Duration` / `(long timeout, TimeUnit)` 오버로드를 쓴다. 제한을 넘기면 `TimeoutException`이 발생한다.

```java
m_guard.awaitCondition(() -> m_done, Duration.ofSeconds(5))
       .andReturn();                                 // throws TimeoutException
```

`timeout`이 0이면 조건이 만족되지 않은 즉시 `TimeoutException`이 발생한다.

### 2.3 `preAction` — lock 획득 직후 1회 실행

대기 전에 "요청 보내기" 같은 사전 작업이 필요할 때 사용한다. lock을 잡은 상태에서 조건 검사 **직전에 한 번만** 실행된다.

```java
m_guard.preAction(() -> sendRequest())
       .awaitCondition(() -> m_response != null, Duration.ofSeconds(10))
       .andGet(() -> m_response);
```

`preAction`이 예외를 던지면 대기 쓰레드들에게 `signalAll()`을 보낸 뒤 예외가 그대로 전파된다.

### 2.4 저수준 API

두 모드로 표현하기 어려운 흐름은 직접 구성한다. lock/unlock 짝과 signal 시점은 호출자 책임이다.

```java
m_guard.lock();
try {
    while ( !ready() ) {
        m_guard.awaitSignal();                 // 또는 awaitSignal(due) / awaitSignal(duration)
    }
    doWork();
    m_guard.signalAll();
}
finally {
    m_guard.unlock();
}
```

| 메소드 | 설명 |
|---|---|
| `lock()` / `unlock()` | lock 획득·해제 (재진입 가능) |
| `signalAll()` | 대기 쓰레드 전체 깨우기. **lock을 잡은 상태에서 호출해야 한다** |
| `awaitSignal()` | signal 또는 interrupt까지 대기 |
| `awaitSignal(Date due)` / `awaitSignal(Duration)` | 시간 제한 대기. 시간 초과로 깬 경우 `false` |
| `awaitSignalNanos(long)` | 남은 나노초 반환 |

---

## 3. `ConditionVariable` — 조건 극성에 주의

`Condition`과 술어(predicate)를 묶은 얇은 래퍼다. **`Guard.awaitCondition`과 술어의 의미가 반대**다.

| 타입 | 술어의 의미 | 루프 |
|---|---|---|
| `Guard.awaitCondition(pred)` | **만족 조건** — `pred`가 `true`가 될 때까지 대기 | `while (!pred) await()` |
| `ConditionVariable(cond, waitCond)` | **대기 조건** — `waitCond`가 `true`인 동안 대기 | `while (waitCond) await()` |

```java
Lock lock = new ReentrantLock();
Condition cond = lock.newCondition();
ConditionVariable cv = new ConditionVariable(cond, () -> m_queue.isEmpty());  // 비어 있는 동안 대기

lock.lock();
try {
    cv.await();                                    // 또는 await(5, SECONDS) / awaitUntil(deadline)
    process(m_queue.poll());
}
finally {
    lock.unlock();
}
```

새 코드에서는 `Guard`를 우선 검토한다.

---

## 4. 주기 실행

### 4.1 작업 정의 — `RecurringWork`

```java
public class HealthCheck implements RecurringWork {
    @Override public void onStarted(RecurringSchedule schedule) throws Throwable { /* 1회 초기화 */ }
    @Override public void perform() throws Exception { ping(); }        // 주기마다 호출
    @Override public void onStopped() { /* 1회 정리 */ }
}
```

작업이 자체 취소 경로를 제공하려면 [`InterruptableWork`](../src/main/java/utils/thread/InterruptableWork.java)(`interrupt()`)를 함께 구현한다.

### 4.2 전용 쓰레드 방식 — `RecurringScheduleThread`

```java
RecurringScheduleThread sched =
        RecurringScheduleThread.newFixedRateSchedule("health", new HealthCheck(), 5_000);
        // 또는 newFixedDelaySchedule(...), initialDelay를 받는 4-인자 오버로드

sched.start();
...
sched.stop(true);              // true면 수행 중인 perform() 쓰레드를 interrupt
sched.waitForStopped();
```

| 팩토리 | 주기 계산 |
|---|---|
| `newFixedRateSchedule` | **고정 주기** — `perform()` 수행 시간을 interval에서 뺀 만큼만 대기한다. 수행이 interval을 넘기면 `late schedule` 로그를 남기고 즉시 다음 iteration으로 간다. 내부적으로 `LongVariableSmoother`로 대기 오버헤드를 보정한다. |
| `newFixedDelaySchedule` | **고정 지연** — `perform()`이 끝난 시점부터 interval만큼 대기한다. |

### 4.3 공유 쓰레드 풀 방식 — `RecurringScheduleImpl`

전용 쓰레드 대신 `CamusExecutor`의 풀을 사용한다. 직접 생성하기보다 `CamusExecutor`의 팩토리로 만든다.

```java
CamusExecutor executor = new CamusExecutorImpl();
RecurringSchedule sched = executor.createScheduleWithFixedRate(work, 0, 5_000);
sched.start();
```

### 4.4 공통 제어

| 메소드 | 설명 |
|---|---|
| `getState()` | `STARTING` / `WORKING` / `IDLE` / `STOPPING` / `STOPPED` |
| `getFailureCause()` | 실패 종료 시 원인 |
| `getInterval()` / `setInterval(millis)` | 주기 조회·변경 (실행 중 변경 가능) |
| `performNow()` | 다음 주기를 기다리지 않고 즉시 1회 수행하도록 깨운다 (IDLE 상태일 때만 유효) |
| `start()` | `onStarted()` 호출 후 반복 시작 |
| `stop(mayInterruptIfRunning)` | 종료 요청. **`onStopped()` 호출 전에 반환될 수 있다** |
| `waitForStopped()` / `waitForStopped(timeout)` | 완전 종료 대기 |

---

## 5. 실행기

### 5.1 `CamusExecutor`

`Executor`를 확장하여 지연 실행·`Future` 제출·반복 스케줄 생성을 한 곳에 모은 인터페이스다.

```java
CamusExecutor executor = new CamusExecutorImpl();          // 또는 기존 ExecutorService 주입

executor.execute(task);
Future<String> f = executor.submit(() -> compute());
Future<?> later = executor.schedule(task, 3, TimeUnit.SECONDS);
RecurringSchedule sched = executor.createScheduleWithFixedDelay(task, 0, 1_000);

executor.stop(true);                                        // true면 기반 executor도 shutdown
```

컴포넌트가 관리자로부터 쓰레드 풀을 주입받아야 하면 [`ExecutorAware`](../src/main/java/utils/thread/ExecutorAware.java)(`getExecutor`/`setExecutor`)를 구현한다. 주입 시점은 속성 설정 완료 후 `Initializable.initialize()` 호출 직전이다.

### 5.2 `AsyncConsumer` — 소비를 별도 쓰레드로

`Consumer<T>`를 구현하므로 기존 소비자 자리를 그대로 대체할 수 있다.

```java
AsyncConsumer<Event> consumer = AsyncConsumer.singleWorker(this::handle);
// 상태 없는 소비자라면 여러 워커로:
// AsyncConsumer.multipleWorkers(this::handle, 4);

consumer.accept(event);                                     // fire-and-forget
CompletableFuture<Void> done = consumer.schedule(event);    // 완료 추적이 필요할 때
consumer.shutdown();                                        // 쓰레드 종료 전에 반환될 수 있다
```

`multipleWorkers`/`from`에 넘기는 소비자는 **상태가 없어야 한다** (여러 쓰레드가 동시 호출).

### 5.3 `SingleWaiterExecutor` — latest-wins 단일 슬롯

동시에 하나의 `StartableExecution`만 실행하고, 대기 슬롯은 길이 1이다. 실행 중에 새 작업이 들어오면 **기존 대기 작업은 `cancel(true)`되어 폐기**되고 새 작업이 그 자리를 차지한다.

```java
SingleWaiterExecutor exec = new SingleWaiterExecutor();
exec.submit(renderTask);      // 화면 갱신처럼 "최신 요청만 처리하면 되는" 작업에 적합
exec.submit(renderTask2);     // 앞선 대기 작업은 취소됨
exec.stop();
```

`Executor`를 구현하지 않으며 fire-and-forget 형태다. 실제 실행 여부를 알아야 하면 `StartableExecution`에 finish 콜백을 직접 등록한다.

### 5.4 `Timer` — `Execution`에 마감 시각 걸기

`utils.async`의 `Execution.setTimeout` 구현에 쓰이는 데몬 쓰레드 기반 타이머다. 보통 직접 쓰지 않고 `Executions.getTimer()`로 공용 인스턴스를 얻는다.

```java
Executions.getTimer().setTimer(execution, Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
```

> ⚠️ 내부 큐가 `MinMaxPriorityQueue.maximumSize(64)`로 **64개 상한**이다. 동시에 65개 이상의 타이머를 등록하면 마감이 가장 늦은 항목이 조용히 밀려난다.

---

## 6. 쓰레드 간 데이터 전달

### 6.1 `LengthOneOverridingQueue` — 최신 값만 보관

길이 1의 큐. 새 항목이 들어오면 소비되지 않은 이전 항목은 **통지 없이 폐기**된다(latest-wins).

```java
LengthOneOverridingQueue<Sample> q = new LengthOneOverridingQueue<>();

// producer
q.add(sample);                       // null 금지 (IllegalArgumentException)

// consumer
Sample s = q.poll();                 // 비어 있으면 대기
Sample s2 = q.poll(Duration.ofSeconds(1));   // 시간 초과 시 TimeoutException
Sample peeked = q.peek();            // 소비하지 않고 확인. 비어 있으면 null
```

센서 샘플, 진행률 알림처럼 coalescing되어도 무방한 값에 쓴다. **손실 없는 전달이 필요하면 `BlockingQueue`를 쓴다.**

### 6.2 `CompletionFStream` — 완료 순서대로 결과 수집

여러 `Execution`을 제출해 두고 **완료되는 순서대로** 결과를 `FStream`으로 소비한다. 제출 순서와 결과 순서는 다를 수 있다.

`CompletionFStream<T> extends SuppliableFStream<Result<T>>`이므로 **스트림 원소는 `T`가 아니라 `utils.func.Result<T>`**다 — 성공·실패가 모두 원소로 흘러나온다.

```java
try ( CompletionFStream<Report> completions = new CompletionFStream<>(16) ) {   // 버퍼 길이
    for ( Job job : jobs ) {
        completions.submit(job.toExecution());       // 시작되지 않았으면 자동 start
    }
    completions.endOfSupply();

    completions.forEach(result -> result.ifSuccessful(this::handle)   // 원소는 Result<Report>
                                        .ifFailed(e -> log.warn("job failed", e))
                                        .ifNone(() -> log.info("job cancelled")));
}
```

`SuppliableFStream`을 상속하므로 `poll()`, `next(timeout, unit)`, `setSupplyListener(...)` 등 버퍼 API를 그대로 쓸 수 있다.

- 완료 콜백은 **작업을 수행한 워커 쓰레드에서 동기적으로** 버퍼에 적재한다. 버퍼가 가득 차면 워커가 블록되므로 워커 풀 크기와 버퍼 용량을 함께 고려한다.
- close 또는 `endOfSupply` 이후의 `submit`은 `IllegalStateException`이다.
- 그 이후에 도착한 결과는 폐기된다.

### 6.3 `InterThreadShareSupplier` — 중복 생성 방지 (call coalescing)

같은 값 생성이 여러 쓰레드에서 중복 수행되지 않도록 조율한다. 한 쓰레드가 생성 중이면 다른 쓰레드는 새로 시작하지 않고 대기했다가 그 결과(또는 예외)를 공유받는다.

```java
InterThreadShareSupplier<Token> supplier =
        new InterThreadShareSupplier<>(() -> authServer.issueToken());
supplier.setWaitTimeout(Duration.ofSeconds(5));    // 미지정 시 무제한 대기

Token t = supplier.get();   // throws InterruptedException, TimeoutException, ExecutionException
```

> **memoize가 아니다.** 동시에 진행 중인 호출끼리만 결과를 공유하며, 호출이 겹치지 않으면 매번 새로 생성한다. 영구 캐시가 필요하면 [`utils.func.Lazy`](../src/main/java/utils/func/Lazy.java) 계열을 쓴다.

---

## 7. 함정 (gotchas)

### 7.1 `signalAll()`은 lock을 잡은 상태에서만

저수준 API로 직접 호출할 때 lock 없이 호출하면 `IllegalMonitorStateException`이 발생한다. 작업 실행 모드(`run`/`get`/`accept`)와 `andRun`/`andGet`은 자동 처리되므로 신경 쓸 필요가 없다.

### 7.2 `andReturn()`은 signal을 보내지 않는다

`andRun`/`andGet`은 작업 수행 후 `signalAll()`을 호출하지만, `andReturn()`은 대기만 하고 signal하지 않는다. 대기 후 상태를 바꾼다면 `andRun`을 써야 다른 대기 쓰레드가 깨어난다.

```java
// 잘못된 예: 상태를 바꿨는데 아무도 깨어나지 않는다
m_guard.awaitCondition(() -> m_ready).andReturn();
m_state = CONSUMED;                                 // ← lock 밖이고 signal도 없다

// 올바른 예
m_guard.awaitCondition(() -> m_ready).andRun(() -> m_state = CONSUMED);
```

### 7.3 `Guard`와 `ConditionVariable`의 술어 극성이 반대다

[3절](#3-conditionvariable--조건-극성에-주의) 참조. 둘을 섞어 쓰면 즉시 반환하거나 영원히 대기하는 버그가 난다.

### 7.4 `stop()`은 종료를 기다리지 않는다

`RecurringSchedule.stop()`은 종료 요청만 하고 반환한다. `onStopped()`가 아직 호출되지 않았을 수 있으므로, 정리 완료를 보장하려면 `waitForStopped()`를 이어서 호출한다.

또한 **`perform()` 수행 쓰레드에서 자기 스케줄의 `waitForStopped()`를 호출하면** deadlock 방지를 위해 `RuntimeException`이 발생한다.

### 7.5 `LengthOneOverridingQueue`는 이벤트를 잃는다

설계된 동작이다. 모든 이벤트가 필요하면 `BlockingQueue`나 `utils.stream.SuppliableFStream`을 쓴다.

### 7.6 `Timer`의 64개 상한

[5.4절](#54-timer--execution에-마감-시각-걸기) 참조. 다수의 동시 타임아웃이 필요한 설계라면 별도 `ScheduledExecutorService`를 쓴다.

---

## 8. API 요약

| 타입 | 역할 |
|---|---|
| [`Guard`](../src/main/java/utils/thread/Guard.java) | `ReentrantLock` + `Condition` 표준 래퍼. `PreAction`/`AwaitCondition`/`TimedAwaitCondition` 빌더 포함 |
| [`ConditionVariable`](../src/main/java/utils/thread/ConditionVariable.java) | `Condition` + 대기 술어 |
| [`RecurringSchedule`](../src/main/java/utils/thread/RecurringSchedule.java) | 반복 스케줄 제어 인터페이스 (`State` enum 포함) |
| [`RecurringScheduleThread`](../src/main/java/utils/thread/RecurringScheduleThread.java) | 전용 쓰레드 기반 구현. `newFixedRateSchedule`/`newFixedDelaySchedule` |
| [`RecurringScheduleImpl`](../src/main/java/utils/thread/RecurringScheduleImpl.java) | `CamusExecutor` 풀 기반 구현 |
| [`RecurringWork`](../src/main/java/utils/thread/RecurringWork.java) / [`InterruptableWork`](../src/main/java/utils/thread/InterruptableWork.java) | 반복 작업 / 취소 가능 작업 인터페이스 |
| [`CamusExecutor`](../src/main/java/utils/thread/CamusExecutor.java) / [`CamusExecutorImpl`](../src/main/java/utils/thread/CamusExecutorImpl.java) | 지연 실행·제출·반복 스케줄을 합친 executor |
| [`ExecutorAware`](../src/main/java/utils/thread/ExecutorAware.java) | 쓰레드 풀 주입을 받는 컴포넌트 인터페이스 |
| [`AsyncConsumer`](../src/main/java/utils/thread/AsyncConsumer.java) | 소비를 쓰레드 풀로 넘기는 `Consumer<T>` |
| [`SingleWaiterExecutor`](../src/main/java/utils/thread/SingleWaiterExecutor.java) | latest-wins 단일 슬롯 실행기 |
| [`Timer`](../src/main/java/utils/thread/Timer.java) | `Execution`에 마감 시각을 거는 데몬 타이머 |
| [`LengthOneOverridingQueue`](../src/main/java/utils/thread/LengthOneOverridingQueue.java) | 최신 값 하나만 보관하는 길이 1 큐 |
| [`CompletionFStream`](../src/main/java/utils/thread/CompletionFStream.java) | 완료 순서대로 결과를 내보내는 `FStream` |
| [`InterThreadShareSupplier`](../src/main/java/utils/thread/InterThreadShareSupplier.java) | 동시 호출 간 결과 공유(call coalescing) |

---

## 9. 관련 문서

* 패키지 개발 노트: [src/main/java/utils/thread/CLAUDE.md](../src/main/java/utils/thread/CLAUDE.md)
* 비동기 실행 프레임워크: [src/main/java/utils/async/CLAUDE.md](../src/main/java/utils/async/CLAUDE.md) — `Guard`/`Timer`/`SingleWaiterExecutor`가 여기서 쓰인다
* 스트림: [docs/stream-guide.md](stream-guide.md) — `SuppliableFStream`이 손실 없는 쓰레드 간 채널이다
* 라이브러리 전체 구조: [CLAUDE.md](../CLAUDE.md)
