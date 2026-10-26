# `utils.async` 스레드 기반 Execution 사용자 가이드

별도 스레드에서 작업을 수행하고 그 수명주기를 [`Execution`](../src/main/java/utils/async/Execution.java)으로 노출하는 클래스 계열의 사용법을 설명한다. 한 번 실행되는 작업부터, 반복(loop) 작업, 주기 작업, 목표 상태가 될 때까지 기다리는 polling까지 같은 계층 위에 쌓여 있다.

다루는 클래스는 다음과 같다.

- [`AbstractThreadedExecution`](../src/main/java/utils/async/AbstractThreadedExecution.java)
- [`AbstractLoopExecution`](../src/main/java/utils/async/AbstractLoopExecution.java)
- [`PeriodicLoopExecution`](../src/main/java/utils/async/PeriodicLoopExecution.java)
- [`AbstractPeriodicPoller`](../src/main/java/utils/async/AbstractPeriodicPoller.java)
- [`PeriodicPoller`](../src/main/java/utils/async/PeriodicPoller.java)

`Execution` 자체의 상태·리스너·조합 API는 [`utils.async` 개발 노트](../src/main/java/utils/async/CLAUDE.md)를 참고한다.

---

## 1. 개요

### 1.1 클래스 계층

```
EventDrivenExecution<T>                상태 전이(notifyXxx)와 대기·리스너 제공
 └─ AbstractAsyncExecution<T>          start() + Executor 주입
     └─ AbstractThreadedExecution<T>   ★ 작업 1회를 별도 스레드(또는 호출 스레드)에서 수행
         └─ AbstractLoopExecution<T>   ★ 결과가 나올 때까지 iterate()를 반복
             └─ PeriodicLoopExecution<T>   ★ iteration 사이에 주기적으로 대기 + 제한 시간
                 └─ AbstractPeriodicPoller<R>  ★ "목표 상태가 될 때까지 조회"에 특화된 hook
                     └─ PeriodicPoller<T>      ★ 람다 + 빌더로 바로 쓰는 polling
```

아래로 갈수록 구현할 부분이 줄어든다. 하위 클래스는 상위 클래스의 hook 일부를 `final`로 막고 더 좁은 hook을 제공한다.

### 1.2 무엇을 써야 하나

| 하고 싶은 일 | 사용할 클래스 | 구현할 것 |
|---|---|---|
| 오래 걸리는 작업 하나를 비동기로 수행 | `AbstractThreadedExecution` | `executeWork()` |
| 결과가 나올 때까지 단계를 반복 (주기 없음) | `AbstractLoopExecution` | `initializeLoop()` / `iterate(long)` / `finalizeLoop()` |
| 일정 주기로 작업을 반복, 제한 시간 적용 | `PeriodicLoopExecution` | `performPeriodicAction(long)` |
| 목표 상태가 될 때까지 주기적으로 조회 (재사용할 클래스로) | `AbstractPeriodicPoller` | `tryPoll()` (+ 선택 hook) |
| 목표 상태가 될 때까지 주기적으로 조회 (한 번 쓰고 마는 코드) | `PeriodicPoller` | 람다 하나 |

> 서비스처럼 시작/중지를 반복하며 계속 도는 주기 작업은 이 계층이 아니라 Guava `Service` 기반의 [`PeriodicLoopService`](../src/main/java/utils/async/PeriodicLoopService.java)를 쓴다. 이 계층의 실행은 **한 번 시작해서 한 번 끝나는** 일회성 작업이다.

---

## 2. 공통 — 실행하고 결과 받기

이 계층의 모든 클래스는 같은 방식으로 실행하고 결과를 받는다.

### 2.1 상태

```
NOT_STARTED → STARTING → RUNNING → COMPLETED | FAILED | CANCELLED
                            └→ CANCELLING ┘ (cancel(true) 요청 시 거치는 중간 상태)
```

- `STARTING`: 초기화 hook(`initializeThread()` 등)이 수행되는 구간.
- `RUNNING`: 작업 본문(`executeWork()` 등)이 수행되는 구간.
- `CANCELLING`: 취소가 요청되었지만 작업이 아직 끝나지 않은 구간. 이 상태에서도 작업이 결과를 내면 `COMPLETED`로 끝날 수 있다.

### 2.2 `start()`와 `run()`

| 진입점 | 수행 스레드 | 반환 |
|---|---|---|
| `start()` | 새 스레드(기본은 daemon) 또는 `setExecutor(...)`로 주입한 `Executor` | 즉시 반환. 결과는 대기 API로 받는다. |
| `run()` | 호출 스레드 | 작업이 끝날 때까지 블록하고 결과를 반환하거나 예외를 던진다. |

둘 중 하나만, 한 번만 호출할 수 있다. 두 번째 호출은 `IllegalStateException`이다.

```java
// 비동기 실행
MyExecution exec = new MyExecution(...);
exec.start();
AsyncResult<Result> r = exec.waitForFinished(30, TimeUnit.SECONDS);   // 또는 exec.get()

// 동기 실행 — 호출 스레드에서 바로 수행
Result value = new MyExecution(...).run();
```

### 2.3 결과 받기

```java
exec.get();                         // 완료 값 반환. 실패 → ExecutionException, 취소 → CancellationException
exec.get(10, TimeUnit.SECONDS);     // 제한 시간 내 대기
AsyncResult<T> r = exec.waitForFinished();   // 예외를 던지지 않고 결과 객체로 받기
if ( r.isCompleted() ) { use(r.getUnchecked()); }
else if ( r.isFailed() ) { log(r.getFailureCause()); }

exec.whenFinished(result -> result.ifSuccessful(this::use)    // 리스너 방식
                                  .ifFailed(this::report));
```

> `waitForFinished()`는 실패해도 예외를 던지지 않는다. 결과를 확인하지 않고 다음 코드로 넘어가면 실패가 엉뚱한 곳(예: 생성되지 않은 출력 파일 읽기)에서 드러난다. 반드시 `isCompleted()`를 확인하거나 `get()`을 쓴다.

### 2.4 예외가 상태로 바뀌는 규칙

hook(작업 본문, 초기화 등)이 던진 예외는 다음과 같이 해석된다.

| 던진 예외 | 최종 상태 | `run()`이 던지는 예외 |
|---|---|---|
| `InterruptedException`, `CancellationException` | `CANCELLED` | 그 예외 그대로 |
| 그 밖의 `Exception` | `FAILED` (원인은 감싼 예외를 푼 원래 예외) | `ExecutionException` (cause = 원인) |
| `Error` | 상태 전이 없음 | 그대로 전파 |

`ExecutionException` 등으로 감싸서 던져도 실패 원인을 기록할 때 풀리므로, `getFailureCause()`에는 원래 예외가 남는다.

### 2.5 취소

```java
exec.cancel(true);              // 취소 요청. 상태를 CANCELLING으로 바꾸고 바로 반환한다.
exec.waitForFinished();         // 실제 종료(CANCELLED 또는 COMPLETED)는 따로 기다려야 한다.
```

취소는 **협력적**이다. `cancel(true)`는 작업 스레드를 인터럽트하지 않는다. 작업이 취소 요청을 알아채고 스스로 끝나야 한다.

- 작업 본문에서 주기적으로 `isCancelRequested()`를 검사하거나 `checkCancelled()`를 호출한다.
- 블로킹 호출(소켓 읽기, 하위 작업 대기 등)을 깨워야 하면 클래스가 [`CancellableWork`](../src/main/java/utils/async/CancellableWork.java)를 구현한다. `cancel()`이 `CANCELLING` 전이 직후 `cancelWork()`를 호출하므로, 거기서 소켓을 닫거나 하위 작업을 취소한다. `cancelWork()`는 `cancel()`을 호출한 스레드에서 실행되므로 중단 신호만 보내고 바로 반환해야 한다.
- `cancel(false)`는 아직 시작하지 않았거나 `CancellableWork`인 경우에만 효과가 있다.
- `cancelWork()`가 예외를 던지면 실행은 그 예외를 원인으로 `FAILED`가 된다.

### 2.6 외부에서 종료가 확정된 경우

`notifyFailed(...)` 등은 `public`이라 작업 밖에서도 실행을 종료시킬 수 있다(`cancelWork()` 실패도 같은 경로다). 이 경우 다음이 보장된다.

- 먼저 확정된 상태가 최종 상태로 유지된다. 작업이 나중에 결과를 내거나 다른 예외로 끝나도 바뀌지 않는다.
- `run()`은 작업 자신의 예외가 아니라 **확정된 결과**를 던진다(외부 `FAILED`면 그 원인을 담은 `ExecutionException`).
- loop 계열은 다음 iteration 경계에서 멈춘다(4장).

---

## 3. `AbstractThreadedExecution` — 작업 하나를 비동기로

### 3.1 hook

| hook | 필수 | 호출 시점 | 비고 |
|---|---|---|---|
| `executeWork()` | ✔ | `RUNNING`에서 1회 | 반환값이 결과가 된다. |
| `initializeThread()` |  | `STARTING`에서 1회, 작업 스레드에서 | 예외를 던지면 `executeWork()`는 호출되지 않는다. |
| `abortAfterInitialize()` |  | 초기화는 성공했지만 그 사이 외부에서 종료되어 `RUNNING`이 되지 못한 경우 | 초기화에서 확보한 자원을 정리한다. |
| `finalizeThread()` |  | `executeWork()`가 호출된 경우에만, 종료 상태로 전이한 **뒤** 1회 | 예외는 로그만 남는다. |
| `onResultDiscarded(T)` |  | `executeWork()`가 결과를 반환했지만 외부 종료 때문에 `COMPLETED`가 되지 못한 경우 1회 | 결과가 소유한 자원을 정리한다. 예외는 로그만 남는다. |

> `finalizeThread()`는 종료 상태로 전이한 뒤에 호출되므로, `waitForFinished()`로 기다리던 쪽이 `finalizeThread()`보다 먼저 깨어날 수 있다. 정리 완료를 기다려야 하는 자원은 다른 동기화 수단을 쓴다.

### 3.2 예제 — 다운로드

```java
public class DownloadExecution extends AbstractThreadedExecution<Path> implements CancellableWork {
	private final URI m_uri;
	private final Path m_target;
	private volatile InputStream m_input;

	public DownloadExecution(URI uri, Path target) {
		m_uri = uri;
		m_target = target;
		setThreadNamePrefix("download");
	}

	@Override
	protected void initializeThread() throws Exception {
		m_input = m_uri.toURL().openStream();       // 실패하면 FAILED, executeWork()는 호출되지 않음
	}

	@Override
	protected void abortAfterInitialize() {
		IOUtils.closeQuietly(m_input);              // 초기화 직후 외부 종료로 시작하지 못한 경우
	}

	@Override
	protected Path executeWork() throws Exception {
		Files.copy(m_input, m_target, StandardCopyOption.REPLACE_EXISTING);
		return m_target;
	}

	@Override
	public boolean cancelWork() {
		IOUtils.closeQuietly(m_input);              // 블로킹 중인 read()를 깨운다
		return true;
	}

	@Override
	protected void finalizeThread() {
		IOUtils.closeQuietly(m_input);
	}
}
```

```java
DownloadExecution dl = new DownloadExecution(uri, path);
dl.setExecutor(ioExecutor);       // 선택: 주입하지 않으면 새 daemon 스레드에서 수행
dl.start();
...
dl.cancel(true);                  // cancelWork()가 스트림을 닫아 read()를 깨운다
```

### 3.3 기타 설정

| 메소드 | 의미 |
|---|---|
| `setExecutor(Executor)` | `start()` 시 새 스레드 대신 이 `Executor`에 제출한다. 거부되면 `FAILED`가 되고 예외가 `start()` 호출자에게 전파된다. |
| `setDaemonThread(boolean)` | 새 스레드를 만들 때 daemon 여부 (기본 `true`). |
| `setThreadNamePrefix(String)` | 스레드 이름과 `toString()`에 쓰인다. |

---

## 4. `AbstractLoopExecution` — 결과가 나올 때까지 반복

`executeWork()`를 "결과가 나올 때까지 `iterate()`를 반복"하는 loop로 구현한 클래스다.

### 4.1 hook

| hook | 필수 | 설명 |
|---|---|---|
| `initializeLoop()` | ✔ | loop 시작 전 1회. 예외를 던지면 `finalizeLoop()`도 호출되지 않으므로 부분 초기화의 정리는 직접 한다. |
| `iterate(long loopIndex)` | ✔ | 한 번의 iteration. `FOption.of(값)`을 반환하면 그 값으로 loop가 끝나고, `FOption.empty()`면 다음 iteration으로 간다. `null`을 반환하면 `IllegalStateException`으로 `FAILED`. |
| `finalizeLoop()` | ✔ | `initializeLoop()`가 성공했다면 종료 사유와 무관하게 1회. 예외는 로그만 남는다. 인터럽트 상태를 지운 채 호출되므로 정리 중 블로킹 호출이 바로 중단되지 않는다. |

`finalizeLoop()` 안에서는 `getLoopOutcome()`으로 loop가 어떻게 끝났는지(완료 값, 실패 원인, 취소, 또는 외부에서 확정된 결과)를 알 수 있다. 이 시점에는 아직 종료 전이 전이라 `poll()`로는 결과를 얻을 수 없다.

### 4.2 loop가 끝나는 조건

다음 iteration을 시작하기 직전에 검사한다.

1. 취소 요청(`CANCELLING`)이 있거나 스레드가 인터럽트되었으면 → `CANCELLED`.
2. 외부에서 이미 종료가 확정되었으면 → 확정된 상태를 유지한 채 종료(`finalizeLoop()`는 호출된다).

따라서 iteration 하나가 오래 걸리면 그만큼 종료가 늦어진다. 긴 iteration 안에서는 `isCancelRequested()`를 직접 확인하거나, 작업을 잘게 나누거나, `CancellableWork`로 블로킹을 깨운다.

### 4.3 예제 — 페이지 단위 수집

```java
public class PageCollector extends AbstractLoopExecution<List<Item>> {
	private final ApiClient m_client;
	private final List<Item> m_items = new ArrayList<>();
	private Cursor m_cursor;

	@Override
	protected void initializeLoop() throws Exception {
		m_cursor = m_client.openCursor();
	}

	@Override
	protected FOption<List<Item>> iterate(long loopIndex) throws InterruptedException, ExecutionException {
		Page page = m_client.next(m_cursor);         // 한 페이지 = 한 iteration (취소 응답성 확보)
		m_items.addAll(page.items());
		return page.isLast() ? FOption.of(m_items) : FOption.empty();
	}

	@Override
	protected void finalizeLoop() {
		m_cursor.close();
		AsyncResult<List<Item>> outcome = getLoopOutcome();
		if ( outcome.isFailed() ) {
			getLogger().warn("collection failed after {} items", m_items.size(), outcome.getFailureCause());
		}
	}
}
```

### 4.4 정책 — 취소 요청 중에 결과가 나온 경우

취소 요청 시점에 진행 중이던 iteration이 결과를 반환하면 그 결과로 `COMPLETED`가 된다. `cancel(true)`가 `true`를 반환했더라도 최종 상태는 `COMPLETED`일 수 있으므로, 호출자는 최종 상태를 확인해야 한다.

---

## 5. `PeriodicLoopExecution` — 주기적인 반복 + 제한 시간

iteration 사이에 정해진 간격만큼 대기하고, 전체 loop에 제한 시간을 거는 클래스다. 하위 클래스는 `iterate()` 대신 `performPeriodicAction(long)`을 구현한다(`iterate()`는 `final`).

### 5.1 주기 모드 — `IntervalMode`

| 모드 | 다음 iteration 시작 | 한 iteration이 주기보다 오래 걸리면 | 적합한 경우 |
|---|---|---|---|
| `FIXED_DELAY` (단일 인자 생성자의 기본값) | 이번 iteration이 **끝난** 시점 + 주기 | 그래도 주기만큼 기다린다 | 원격 조회, 재연결 등 연달아 수행되면 안 되는 작업 |
| `FIXED_RATE` | loop 시작 시점 + 주기 × n | 밀린 만큼 **대기 없이 연달아** 수행한다(catch-up) | 일정한 박자가 필요한 샘플링·보고 |

```java
protected MySampler() {
	super(Duration.ofMillis(100), IntervalMode.FIXED_RATE);
}
```

> `(Duration, boolean cumulativeInterval)` 생성자는 deprecated다. 호출부에서 의미가 드러나는 `IntervalMode`를 쓴다.

### 5.2 제한 시간 — `setTimeout`과 `setDue`

둘 다 loop의 제한 시간을 설정하며, 넘기면 `TimeoutException`으로 `FAILED`가 된다. **기준이 다르다.**

| 메소드 | 기준 | 시작이 늦어지면 |
|---|---|---|
| `setTimeout(Duration)` | loop가 **실제로 시작된 시점**부터의 기간 | 영향 없음 — 지정한 기간 전체를 쓴다 |
| `setDue(Instant)` | 시스템 시계 기준의 **절대 시각** | 그만큼 쓸 수 있는 시간이 준다. 시작 시점에 이미 지났으면 첫 iteration 전에 실패 |

- 둘 다 설정하면 `setDue`가 우선한다. `TimeoutException` 메시지에는 실제로 적용된 쪽(`due=` 또는 `timeout=`)이 표시된다.
- 둘 다 시작 전에만 설정할 수 있다.
- 제한 시간은 진행 중인 iteration을 **중단시키지 않는다.** iteration 시작 직전과 끝난 직후에 검사한다. 원격 호출처럼 오래 걸릴 수 있는 작업에는 `getRemainingTime()`으로 남은 시간을 얻어 개별 요청의 timeout으로 넘긴다.

```java
@Override
protected FOption<Status> performPeriodicAction(long loopIndex)
	throws InterruptedException, TimeoutException, ExecutionException {
	Duration remains = getRemainingTime().getOrElse(Duration.ofSeconds(30));
	Status status = m_client.fetchStatus(remains);       // 요청 timeout = 남은 시간
	return status.isReady() ? FOption.of(status) : FOption.empty();
}
```

`performPeriodicAction()`은 `InterruptedException`/`CancellationException`/`TimeoutException`/`ExecutionException`만 던질 수 있다. 그 밖의 checked 예외는 `ExecutionException`으로 감싼다(실패 원인에는 원래 예외가 남는다). `AbstractPeriodicPoller`의 hook은 이 제약이 없다(6.1).

> 주기와 마감 판정은 시스템 시계가 아니라 단조 시계(`System.nanoTime()`)로 측정한다. NTP 동기화 등으로 시스템 시계가 점프해도 iteration이 몰리거나, 대기가 길어지거나, 제한 시간이 조기에 만료되지 않는다. `getDue()`가 돌려주는 시각은 참고용이다.

### 5.3 hook override 규칙

`initializeLoop()`/`finalizeLoop()`을 override하면 **반드시 `super`를 호출**한다. 특히 `super.initializeLoop()`는 시작 시점과 마감을 계산하므로 빠뜨리면 주기·제한 시간이 동작하지 않는다.

### 5.4 예제 — 일정 시간 동안 샘플 수집

```java
public class SampleCollector extends PeriodicLoopExecution<List<Double>> {
	private final Sensor m_sensor;
	private final int m_count;
	private final List<Double> m_samples = new ArrayList<>();

	public SampleCollector(Sensor sensor, int count) {
		super(Duration.ofMillis(100), IntervalMode.FIXED_RATE);   // 100ms 박자 유지
		m_sensor = sensor;
		m_count = count;
		setTimeout(Duration.ofSeconds(5));
	}

	@Override
	protected FOption<List<Double>> performPeriodicAction(long loopIndex) {
		m_samples.add(m_sensor.read());
		return m_samples.size() >= m_count ? FOption.of(m_samples) : FOption.empty();
	}
}
```

---

## 6. `AbstractPeriodicPoller` — 목표 상태가 될 때까지 조회

"상태를 주기적으로 확인하다가 목표 상태가 되면 끝낸다"는 패턴에 맞춘 hook을 제공한다. `PeriodicLoopExecution`의 hook(`initializeLoop`, `finalizeLoop`, `performPeriodicAction`)은 `final`이므로 아래 hook을 구현한다.

### 6.1 hook과 호출 순서

| 순서 | hook | 필수 | 설명 | 예외가 나면 |
|---|---|---|---|---|
| 1 | `initializePoller()` |  | 시작 시 1회 | 실패/취소 (정리 hook 호출 안 됨) |
| 2 | `tryPoll()` | ✔ | 주기마다 반복. `FOption.of(값)` = 목표 도달, `empty()` = 계속 | 실패/취소 |
| 3 | `onTargetReached(R)` |  | 목표 도달 시 1회. 반환값이 최종 결과. 결과의 가공·반영을 여기서 한다 | **실패/취소로 반영** |
| 4 | `onTargetDiscarded(R)` |  | 얻은 값이 최종 결과로 전달되지 못한 경우 1회. 그 값이 가진 자원을 닫는다 | 로그만 |
| 5 | `cleanupPoller(AsyncResult<R>)` |  | 종료 사유와 무관하게 1회. 완료·실패·취소와 원인을 받는다 | 로그만 |

- `tryPoll()`과 `onTargetReached()`는 `IOException` 같은 checked 예외를 감싸지 않고 바로 던져도 된다. 그 예외가 그대로 실패 원인이 된다.
- 결과 반영이 실패하면 작업도 실패해야 하는 처리(예: 연산 결과를 내부 상태에 반영)는 `cleanupPoller()`가 아니라 **`onTargetReached()`**에서 한다. `cleanupPoller()`의 예외는 결과에 영향을 주지 않는다.
- 목표 상태 값이 `null`이어도 된다(`FOption.of(null)`). `cleanupPoller()`에는 완료로 전달되어 비정상 종료와 구분된다.
- 현재 시도 번호는 `getPollAttempt()`로 얻는다(0부터. 첫 조회 전에는 `-1`).

> 예전 `finalizePoller(@Nullable R)`는 deprecated다. 실패와 "결과 값이 `null`인 완료"를 구분할 수 없으므로 `cleanupPoller(AsyncResult<R>)`를 쓴다.

### 6.2 생성자와 주기

```java
protected MyPoller() {
	super(Duration.ofSeconds(1));                              // FIXED_DELAY (기본)
	// super(Duration.ofSeconds(1), IntervalMode.FIXED_RATE);  // 필요할 때만
}
```

조회는 보통 원격 서버나 장치를 대상으로 하므로 기본값인 `FIXED_DELAY`가 맞다. `FIXED_RATE`는 조회가 느려졌을 때 쉬지 않고 연달아 요청하게 된다.

### 6.3 자원을 반환하는 poller — `onTargetDiscarded`

`tryPoll()`이 연결 객체처럼 자원을 소유한 값을 반환하면, 그 값이 최종 결과로 전달되지 못하는 경우 **아무도 닫지 않게 된다.** 다음 경우에 `onTargetDiscarded()`가 호출되므로 거기서 닫는다.

- `tryPoll()`이 값을 반환했지만 그 사이 외부에서 실행이 종료되어 `onTargetReached()`가 호출되지 않은 경우
- `onTargetReached()`가 예외를 던진 경우
- `onTargetReached()` 수행 도중 외부에서 종료되어 결과로 채택되지 않은 경우
- 결과로 채택된 뒤 정리나 완료 전이 전에 외부에서 종료된 경우 (완료 전이 자체의 실패로 감지하므로 빠짐없이 호출된다)

같은 값에 대해 최대 1회 호출된다.

### 6.4 예제 — WebSocket 재연결

```java
public class WebSocketReconnector extends AbstractPeriodicPoller<WebSocketClient> {
	private final Supplier<WebSocketClient> m_clientFactory;

	public WebSocketReconnector(Supplier<WebSocketClient> factory, Duration retryDelay) {
		super(retryDelay);                                   // FIXED_DELAY: 연결 시도가 길어져도 연달아 재시도하지 않음
		m_clientFactory = factory;
	}

	@Override
	protected FOption<WebSocketClient> tryPoll() throws InterruptedException {
		WebSocketClient client = m_clientFactory.get();
		long timeoutMillis = getRemainingTime().map(Duration::toMillis).getOrElse(5_000L);
		if ( client.connectBlocking(Math.min(timeoutMillis, 5_000L), TimeUnit.MILLISECONDS) ) {
			getLogger().info("connected after {} attempt(s)", getPollAttempt() + 1);
			return FOption.of(client);
		}
		client.close();                                      // 실패한 시도의 클라이언트는 여기서 정리
		return FOption.empty();
	}

	@Override
	protected void onTargetDiscarded(WebSocketClient client) {
		client.close();                                      // 연결됐지만 결과로 전달되지 못한 클라이언트
	}

	@Override
	protected void cleanupPoller(AsyncResult<WebSocketClient> outcome) {
		if ( !outcome.isCompleted() ) {
			getLogger().warn("reconnection gave up: {}", outcome);
		}
	}
}
```

```java
WebSocketReconnector reconnector = new WebSocketReconnector(factory, Duration.ofSeconds(2));
reconnector.setTimeout(Duration.ofMinutes(1));
WebSocketClient client = reconnector.run();
```

### 6.5 정책 — 결과 반영과 외부 종료가 경합할 때

- 외부에서 이미 종료가 확정되었으면 `onTargetReached()`를 호출하지 않는다.
- `onTargetReached()` 도중 종료가 확정되면 그 반환값을 결과로 채택하지 않는다.
- 단, `onTargetReached()`가 이미 수행한 부수 효과(내부 상태 반영 등)는 되돌리지 않는다. 그러므로 `onTargetReached()`가 반영한 상태는 **실행이 `COMPLETED`로 끝난 것을 확인한 뒤**에 사용한다.
- `CANCELLING`(취소 요청 중)은 종료가 확정된 것이 아니므로, 그 사이 목표에 도달하면 `onTargetReached()`가 호출되고 `COMPLETED`로 끝날 수 있다.
- `cleanupPoller()`는 종료 전이보다 먼저 호출되므로, 드물게 그 뒤에 외부 종료가 확정되면 받은 결과와 실제 최종 상태가 다를 수 있다.

---

## 7. `PeriodicPoller` — 람다와 빌더로 바로 쓰는 polling

클래스를 따로 만들 필요 없이 조회 함수 하나로 poller를 만든다.

### 7.1 팩토리

| 팩토리 | 조회 함수 | 종료 조건 | 결과 |
|---|---|---|---|
| `poll(CheckedSupplier<T>)` | `() -> T` | non-null을 반환하면 종료, `null`이면 계속 | 반환한 값 |
| `poll(CheckedFunction<PollContext, T>)` | `ctx -> T` | 위와 같음 | 반환한 값 |
| `pollUntil(CheckedSupplier<Boolean>)` | `() -> boolean` | `true`가 되면 종료 | 항상 `TRUE` (의미 없음) |
| `pollWhile(CheckedSupplier<Boolean>)` | `() -> boolean` | `false`가 되면 종료 | 항상 `TRUE` (의미 없음) |

`PollContext`는 조회 함수 안에서 `attempt()`(현재 시도 번호, 0부터)와 `remainingTime()`(남은 제한 시간)을 제공한다.

> `pollUntil`/`pollWhile`의 결과 값은 조건 값이 아니다. 특히 `pollWhile`은 조건이 `false`가 되어 끝나도 결과는 `TRUE`다. 조건 충족 여부는 실행이 `COMPLETED`로 끝났는지로 판단한다(제한 시간 초과는 `FAILED`).

### 7.2 빌더 옵션

| 메소드 | 의미 | 기본값 |
|---|---|---|
| `fixedDelay(Duration)` | 조회가 끝난 시점부터 대기 (`FIXED_DELAY`) | **`fixedDelay(1초)`** |
| `fixedRate(Duration)` | 시작 시점 기준 고정 박자 (`FIXED_RATE`, 밀리면 연달아) | |
| `timeout(Duration)` | 실제로 시작된 시점부터의 제한 기간 | 없음 |
| `due(Instant)` | 절대 마감 시각 (`timeout`보다 우선) | 없음 |
| `initializer(CheckedRunnable)` | 시작 시 1회 실행 | 없음 |
| `onDiscarded(CheckedConsumer<T>)` | 결과로 전달되지 못한 값의 정리 (6.3과 같은 조건) | 없음 |
| `cleanup(CheckedConsumer<AsyncResult<T>>)` | 종료 결과를 받는 정리 작업 | 없음 |
| `name(String)` | 스레드 이름 접두사 | `"poller"` |

`fixedRate`와 `fixedDelay`는 나중에 호출한 쪽이 적용된다. 예전 이름 `interval()`/`delay()`는 deprecated 별칭이다.

### 7.3 예제

```java
// 1) 상태 대기 — 목표 상태가 되면 그 상태를 결과로
InstanceStatus status = PeriodicPoller.poll(() -> {
								InstanceStatus s = client.getStatus();
								return s == InstanceStatus.RUNNING ? s : null;
							})
							.fixedDelay(Duration.ofSeconds(1))
							.timeout(Duration.ofSeconds(30))
							.build()
							.run();

// 2) 남은 시간을 요청 timeout으로 — PollContext 사용
PeriodicPoller.poll(ctx -> fetchIfReady(ctx.remainingTime().getOrElse(Duration.ofSeconds(10))))
		.timeout(Duration.ofMinutes(1))
		.build()
		.start();

// 3) 연결 획득 — 자원을 반환하므로 onDiscarded 필수
Connection conn = PeriodicPoller.poll(this::tryConnect)       // 실패 시 null
							.fixedDelay(Duration.ofSeconds(2))
							.timeout(Duration.ofMinutes(1))
							.onDiscarded(Connection::close)
							.build()
							.run();

// 4) 조건 대기 — 결과 값이 아니라 완료 여부로 판단
PeriodicPoller<Boolean> waiter = PeriodicPoller.pollUntil(() -> Files.exists(readyFile))
							.fixedRate(Duration.ofMillis(300))
							.timeout(Duration.ofSeconds(3))
							.build();
waiter.start();
boolean ready = waiter.waitForFinished().isCompleted();
```

> 조회 함수가 던진 예외는 감싸지지 않고 그대로 실패 원인이 된다. 조회가 일시적으로 실패해도 계속 재시도하려면 조회 함수 안에서 예외를 잡고 `null`을 반환한다.

---

## 8. 함정 (gotchas)

### 8.1 이름이 같은 `setTimeout`이 두 개다

| 메소드 | 정의된 곳 | 만료되면 |
|---|---|---|
| `setTimeout(long, TimeUnit)` | 모든 `Execution` | `Timer`가 `cancel(true)` 호출 → **`CANCELLED`** |
| `setTimeout(Duration)` | `PeriodicLoopExecution` 이하 | **`TimeoutException`으로 `FAILED`** |

loop 계열에서 "시간 안에 목표에 도달하지 못함"을 실패로 다루려면 `setTimeout(Duration)`을 쓴다. 또 `Timer`는 동시에 64개까지만 보관하므로 대량의 실행에 `setTimeout(long, TimeUnit)`을 걸지 않는다.

### 8.2 `cancel(true)`는 즉시 끝내지 않는다

`CANCELLING`으로 바꾸고 반환할 뿐이다. 최종 종료는 `waitForFinished()`로 기다린다. iteration이나 `executeWork()`가 취소를 확인하지 않으면 영원히 끝나지 않을 수도 있다.

### 8.3 `waitForFinished()`는 실패를 던지지 않는다

반환된 `AsyncResult`를 확인한다(2.3).

### 8.4 정리 hook은 종료 전이 전후로 나뉜다

- `finalizeLoop()`, `cleanupPoller()`: 종료 전이 **전**. 이 안에서 `poll()`은 아직 `RUNNING`이다. 대신 `getLoopOutcome()`/인자로 받은 결과를 쓴다.
- `finalizeThread()`: 종료 전이 **후**. 대기자가 먼저 깨어날 수 있다.

### 8.5 `PeriodicLoopExecution` hook override 시 `super` 호출

`initializeLoop()`/`finalizeLoop()`를 override할 때 `super`를 빼먹으면 주기·제한 시간이 동작하지 않는다(5.3).

### 8.6 `FIXED_RATE`로 원격을 조회하지 않는다

조회가 주기보다 오래 걸리면 쉬지 않고 연달아 요청한다. 원격 조회와 재연결에는 `FIXED_DELAY`를 쓴다.

### 8.7 자원을 반환하는 조회에는 `onTargetDiscarded`/`onDiscarded`

지정하지 않으면 외부 종료 시 연결이 새는 경로가 생긴다(6.3).

---

## 9. API 요약

| 클래스 | 구현할 hook | 주요 설정/조회 |
|---|---|---|
| `AbstractThreadedExecution<T>` | `executeWork()`, (`initializeThread`, `abortAfterInitialize`, `finalizeThread`, `onResultDiscarded`) | `start()`, `run()`, `setExecutor`, `setDaemonThread`, `setThreadNamePrefix` |
| `AbstractLoopExecution<T>` | `initializeLoop()`, `iterate(long)`, `finalizeLoop()` | `getLoopOutcome()` (finalizeLoop 안에서) |
| `PeriodicLoopExecution<T>` | `performPeriodicAction(long)`, (`initializeLoop`/`finalizeLoop` + `super`) | 생성자 `(Duration[, IntervalMode])`, `setTimeout(Duration)`, `setDue(Instant)`, `getRemainingTime()`, `getIntervalMode()`, `getLoopInterval()` |
| `AbstractPeriodicPoller<R>` | `tryPoll()`, (`initializePoller`, `onTargetReached`, `onTargetDiscarded`, `cleanupPoller`) | 생성자 `(Duration[, IntervalMode])`, `getPollAttempt()` |
| `PeriodicPoller<T>` | 없음 (람다) | `poll`, `pollUntil`, `pollWhile`, 빌더(7.2) |
| 공통 (`Execution`) | — | `get`, `waitForFinished`, `poll`, `whenFinished`, `cancel`, `isCancelRequested`, `checkCancelled` |

---

## 10. 관련 문서

- [`utils.async` 개발 노트](../src/main/java/utils/async/CLAUDE.md) — 상태 전이, 취소 흐름, 조합 연산자 등 내부 동작
- [`utils.async` README](../src/main/java/utils/async/README.md) — 짧은 사용 예
- [`utils.thread` 가이드](thread-guide.md) — `Guard`, `Timer` 등 이 계층이 기반으로 쓰는 동시성 도구
- [RESTful RPC 개발 노트](../src/main/java/utils/rpc/restful/CLAUDE.md) — `AbstractPeriodicPoller`의 실제 구현 예인 `RESTfulAsyncRpcClient`
