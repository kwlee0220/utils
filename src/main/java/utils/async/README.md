## utils.async — 비동기 실행 라이브러리

`Execution<T>` 인터페이스 (Java `Future<T>` 의 확장) 를 중심으로 비동기 작업의 라이프사이클,
listener, 취소 / 타임아웃 처리를 제공한다.

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
        .interval(Duration.ofSeconds(2))     // 누적(catch-up) 모드
        .timeout(Duration.ofMinutes(1))      // 또는 .due(Instant)
        .build();
poller.start();
Connection conn = poller.waitForFinished().get();

// 2) 조건 polling — true 가 될 때까지 / true 인 동안
PeriodicPoller.pollUntil(this::isReady)
        .delay(Duration.ofMillis(500))       // 비누적 모드 (이전 iteration 종료 시점 기준)
        .timeout(Duration.ofSeconds(30))
        .build()
        .start();
```

`interval(Duration)` 와 `delay(Duration)` 는 상호 배타적이며 마지막 호출이 적용된다.
시간 제한은 `timeout(Duration)` 또는 `due(Instant)` 로 설정 — 둘 다 설정 시 `due` 가 우선.
제한 시간 내에 목표 상태가 관찰되지 않으면 `TimeoutException` 으로 FAILED 종료된다.

직접 상속해 hook 을 주입하려면 `AbstractPeriodicPoller<R>` 를 사용:
`tryPoll()` (필수), `initializePoller()`/`finalizePoller(state)` (선택).
`finalizePoller` 는 정상 종료 시 결과값, 취소/timeout/예외 시 `null` 을 받는다.

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

Execution<C> seq = AsyncExecutions.sequential(execA, execB, execC);
Execution<List<R>> par = AsyncExecutions.concurrent(execA, execB);
Execution<T>  timed = AsyncExecutions.timed(exec, Duration.ofSeconds(5));
```

각 클래스의 contract 와 상태 전이 규칙은 Javadoc 참고.
