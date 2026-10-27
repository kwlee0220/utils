## utils.async — 비동기 실행 라이브러리

`Execution<T>` 인터페이스 (Java `Future<T>` 의 확장) 를 중심으로 비동기 작업의 라이프사이클,
listener, 취소 / 타임아웃 처리를 제공한다.

스레드 기반 실행 클래스, 즉 Execution 계열(`AbstractThreadedExecution` → `AbstractLoopExecution` →
`PeriodicLoopExecution` → `AbstractPeriodicPoller` → `PeriodicPoller`)과 Service 계열(`AbstractLoopService` →
`PeriodicLoopService`)의 자세한 사용법은 [스레드 기반 실행 사용자 가이드](../../../../../docs/async-threaded-guide.md)를 참고한다.

### 라이프사이클

```
NOT_STARTED → STARTING → RUNNING → COMPLETED
                                 ↘ FAILED
                                 ↘ CANCELLING → CANCELLED
```

상태 전이는 모두 내부 lock (`m_aopGuard`) 으로 직렬화되며, 모든 `notify*` 메소드 호출이 끝난 후에는
신호 (signalAll) 가 자동으로 전파되어 `waitForStarted` / `waitForFinished` 가 깨어난다.

### 기본 사용 — 별도 스레드에서 실행

```java
class MyTask extends AbstractThreadedExecution<Integer> {
    @Override
    protected Integer executeWork() throws Exception {
        // 별도 워커 스레드에서 실행됨
        return 42;
    }
}

MyTask task = new MyTask();
task.whenStarted(() -> System.out.println("started"));
task.whenFinished(result -> {
    result.ifSuccessful(v -> System.out.println("done: " + v))
          .ifFailed(e -> e.printStackTrace())
          .ifNone(() -> System.out.println("cancelled"));
});

task.start();              // 비동기 시작 (start 후 즉시 반환)
task.waitForStarted();     // RUNNING 상태 도달 대기
Integer v = task.get();    // 또는 waitForFinished() 후 result.get()
```

호출 스레드에서 동기 실행이 필요하면 `task.run()` 사용.

### 반복 작업 — `AbstractLoopExecution`

```java
class CountDown extends AbstractLoopExecution<Integer> {
    private int m_count;
    @Override protected void initializeLoop() { m_count = 10; }
    @Override protected void finalizeLoop() {}

    @Override protected FOption<Integer> iterate(long idx) throws Exception {
        if (isCancelRequested()) {
            throw new CancellationException();   // 협력적 취소
        }
        Thread.sleep(100);
        return (--m_count <= 0) ? FOption.of(idx) : FOption.empty();
    }
}
```

`isCancelRequested()` 를 iteration 사이마다 검사하면 `cancel(true)` 후 다음 iteration 진입 시
즉시 종료된다.

### 주기적 polling — `PeriodicPoller`

값 source 를 주기적으로 호출해 목표 상태가 관찰될 때까지 반복하는 빌더 기반 helper.

```java
import utils.async.PeriodicPoller;

// 1) 임의 결과 polling — supplier 가 non-null 을 반환하면 그 값을 결과로 종료
PeriodicPoller<Connection> poller = PeriodicPoller.poll(this::tryConnect)
        .fixedRate(Duration.ofSeconds(2))    // FIXED_RATE: 밀리면 연달아 실행(catch-up)
        .timeout(Duration.ofMinutes(1))      // 또는 .due(Instant). timeout 은 실제 시작 시점부터 측정
        .onDiscarded(Connection::close)      // 연결은 됐지만 결과로 채택되지 못한 경우 닫는다
        .build();
poller.start();
Connection conn = poller.waitForFinished().get();

// 2) 시도 번호·남은 제한 시간이 필요한 polling — PollContext 를 받는 함수
PeriodicPoller.poll(ctx -> fetchState(ctx.remainingTime().getOrElse(DEFAULT_TIMEOUT)))  // 남은 시간을 요청 timeout 으로
        .timeout(Duration.ofMinutes(1))
        .build()
        .start();

// 3) 조건 polling — true 가 될 때까지 / true 인 동안 (결과 값은 항상 TRUE 로 의미 없음 — COMPLETED 여부로 판단)
PeriodicPoller.pollUntil(this::isReady)
        .fixedDelay(Duration.ofMillis(500))  // FIXED_DELAY: 이전 polling 종료 시점부터 대기
        .timeout(Duration.ofSeconds(30))
        .build()
        .start();
```

`fixedRate(Duration)` 와 `fixedDelay(Duration)` 는 상호 배타적이며 마지막 호출이 적용된다.
둘 다 지정하지 않으면 `fixedDelay(1초)` 가 기본값이다. 원격 서버 조회나 재연결처럼 한 번의 polling 이
오래 걸릴 수 있는 경우에는 비누적 모드를 쓰는 것이 좋다 — 누적 모드는 밀린 만큼 대기 없이 연달아 polling 한다.
시간 제한은 `timeout(Duration)` 또는 `due(Instant)` 로 설정 — 둘 다 설정 시 `due` 가 우선.
제한 시간 내에 목표 상태가 관찰되지 않으면 `TimeoutException` 으로 FAILED 종료된다.
supplier 가 연결처럼 자원을 가진 값을 반환하면 `onDiscarded(...)` 를 꼭 지정한다 — 외부 종료 등으로 그 값이 결과로
채택되지 못하면 아무도 닫지 않기 때문이다. 종료 결과(완료/실패/취소와 원인)가 필요한 정리는 `cleanup(...)` 으로 지정한다.

직접 상속해 hook 을 주입하려면 `AbstractPeriodicPoller<R>` 를 사용한다. hook 은 다음 순서로 호출된다:
`initializePoller()` (선택, 시작 시 1회) → `tryPoll()` (필수, 목표 도달 시까지 반복) →
`onTargetReached(R)` (선택, 목표 도달 시 1회 — 결과를 가공/반영하며 예외는 polling 결과에 반영된다) →
`onTargetDiscarded(R)` (선택, 얻은 값이 최종 결과로 채택되지 못한 경우 1회 — 그 값이 가진 연결 등 자원 정리) →
`cleanupPoller(AsyncResult<R>)` (선택, 종료 사유와 무관하게 1회 — 완료·실패·취소와 실패 원인을 받는 정리 작업이며
예외는 로그만 남는다).
`tryPoll()`/`onTargetReached()` 는 `IOException` 등 임의의 예외를 그대로 던질 수 있고, 그 예외가 실패 원인이 된다.
원격 호출처럼 오래 걸릴 수 있는 작업에는 `getRemainingTime()` 으로 얻은 남은 시간을 개별 요청의 timeout 으로 넘긴다
— 전체 제한 시간은 진행 중인 호출을 중단시키지 않는다.
`AbstractPeriodicPoller(Duration)` 생성자는 `IntervalMode.FIXED_DELAY` 를 사용하며, 고정 주기(catch-up)가
필요하면 `AbstractPeriodicPoller(Duration, IntervalMode.FIXED_RATE)` 를 사용한다.
hook 안에서 `getPollAttempt()` 로 현재 polling 시도 번호(0부터)를 얻을 수 있다 — 재연결 시도 횟수 로깅 등에 사용.
`cleanupPoller` 는 종료 결과(`AsyncResult`)를 받는다 — 완료(결과값, `null` 일 수 있음), 실패(원인), 취소 중 하나.

### 명시적 취소 — `CancellableWork`

기본 `cancel(false)` 는 `NOT_STARTED` 만 취소 가능. `RUNNING` 상태의 작업도 취소시키려면
`CancellableWork` 를 구현하고 `cancel(true)` 를 사용한다:

```java
class InterruptibleTask extends AbstractThreadedExecution<Void> implements CancellableWork {
    private volatile Thread m_worker;
    @Override protected Void executeWork() throws Exception {
        m_worker = Thread.currentThread();
        Thread.sleep(60_000);   // InterruptedException 으로 종료됨
        return null;
    }
    @Override public boolean cancelWork() {
        Thread t = m_worker;
        if (t != null) t.interrupt();
        return true;
    }
}
```

`cancel(true)` 는 즉시 반환하며, 종료 상태까지 대기하려면 `waitForFinished()` 호출.

### Sub-process 실행

```java
import utils.async.command.CommandExecution;

try (CommandExecution cmd = CommandExecution.builder()
        .addCommand("git", "log", "--oneline", "-5")
        .workingDirectory(new File("/repo"))
        .redirectStdoutToFile(new File("out.txt"))
        .timeout(Duration.ofSeconds(30))
        .build()) {
    cmd.start();
    cmd.waitForFinished();
}
```

`try-with-resources` 로 사용하면 sub-process 와 모든 descendants 가 SIGTERM (1초 grace) 후 SIGKILL
로 정리된다.

### Combinator

```java
import utils.async.op.AsyncExecutions;

// 순차 실행: 결과 타입은 Object이며 값은 마지막으로 성공한 element의 결과.
// 중간 element가 실패/취소되면 즉시 FAILED/CANCELLED로 전이하고 뒤는 시작되지 않는다.
SequentialAsyncExecution seq = AsyncExecutions.sequential(execA, execB, execC);

// 동시 실행: 결과 값은 항상 null. 개별 결과가 필요하면 element를 직접 참조한다.
// 기본은 전원 성공이 완료 조건이며, setElementCompletionCountToComplete(n)으로 부분 완료 지정.
ConcurrentAsyncExecution par = AsyncExecutions.concurrent(execA, execB);

TimedAsyncExecution<T> timed = AsyncExecutions.timed(exec, Duration.ofSeconds(5));
```

`sequential`/`concurrent`는 element의 개별 결과를 노출하지 않는다. 각 클래스의 contract 와
상태 전이 규칙은 Javadoc 참고.

### 원격 호출로 노출 — `utils.rpc.restful`

`Execution`을 HTTP 원격 연산으로 노출하려면 `RESTfulAsyncRpcServer`를, 원격 연산을 호출하려면
`RESTfulRpcClient`(동기) 또는 `RESTfulAsyncRpcClient`(비동기 폴링)를 사용한다.
외부 명령을 그대로 원격 연산으로 제공하는 구현체는 `utils.rpc.restful.process.RESTfulCommandExecutionServer`.
