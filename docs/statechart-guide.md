# `utils.statechart` 사용자 가이드

계층적 상태 차트(state chart) 프레임워크. UML 상태 차트의 핵심 개념(상태, 신호, 전이, 합성 상태)을 단순한 Java API로 제공한다.

이 가이드는 [`StateChart`](../src/main/java/utils/statechart/StateChart.java)를 중심으로 패키지 사용법을 설명한다.

---

## 1. 개요

상태 차트는 다음 5가지 요소로 구성된다:

| 요소 | 역할 | 주요 타입 |
|---|---|---|
| **컨텍스트** | 도메인 데이터 보유 + 차트 역참조 | [`StateContext<C>`](../src/main/java/utils/statechart/StateContext.java) |
| **차트** | 상태들을 보유하고 신호를 라우팅 | [`StateChart<C>`](../src/main/java/utils/statechart/StateChart.java) |
| **상태** | 차트의 한 노드. 신호에 반응하여 전이 결정 | [`State<C>`](../src/main/java/utils/statechart/State.java) |
| **신호** | 상태에 입력되는 이벤트 | [`Signal`](../src/main/java/utils/statechart/Signal.java) |
| **전이** | 상태 간 이동 + 액션 | [`Transition<C>`](../src/main/java/utils/statechart/Transition.java) |

차트는 비동기 실행 컨텍스트(`AbstractAsyncExecution`)를 상속하여 `whenFinished` 콜백, `cancel`, fail/complete 통지를 제공한다.

---

## 2. 빠른 시작 — 신호등 예제

세 가지 상태 (`Red`, `Green`, `Yellow`) 간 전이하는 단순 차트:

```java
// 1. 컨텍스트 정의 (CRTP self-bound)
public class TrafficLightContext implements StateContext<TrafficLightContext> {
    private StateChart<TrafficLightContext> m_chart;

    @Override public StateChart<TrafficLightContext> getStateChart() { return m_chart; }
    @Override public void setStateChart(StateChart<TrafficLightContext> machine) {
        Utilities.checkState(m_chart == null, "already set");
        m_chart = machine;
    }
}

// 2. 신호 정의
public enum LightSignal implements Signal { TIMER_TICK }

// 3. 차트 구성
TrafficLightContext ctx = new TrafficLightContext();
StateChart<TrafficLightContext> chart = new StateChart<>(ctx);

chart.addState(new SingleOutState<>("red",    ctx, LightSignal.TIMER_TICK, Transitions.noop("green")));
chart.addState(new SingleOutState<>("green",  ctx, LightSignal.TIMER_TICK, Transitions.noop("yellow")));
chart.addState(new SingleOutState<>("yellow", ctx, LightSignal.TIMER_TICK, Transitions.noop("done")));
chart.addState(new SinkState<>("done", ctx));

chart.setInitialState("red");
chart.addFinalState("done");

// 4. 시작 + 신호 발송
chart.start();
chart.handleSignal(LightSignal.TIMER_TICK);  // red → green
chart.handleSignal(LightSignal.TIMER_TICK);  // green → yellow
chart.handleSignal(LightSignal.TIMER_TICK);  // yellow → done (자동 complete 통지)

// 5. 완료 대기
chart.waitForFinished();
```

---

## 3. 핵심 개념 상세

### 3.1 `StateContext<C>` — 도메인 데이터 + 차트 역참조

CRTP self-bound (`<C extends StateContext<C>>`) 인터페이스. 구현체는 자기 타입을 인자로 명시한다:

```java
public class MyContext implements StateContext<MyContext> {
    // 도메인 데이터 보유
    private final String userId;
    private StateChart<MyContext> m_chart;

    public MyContext(String userId) { this.userId = userId; }

    @Override public StateChart<MyContext> getStateChart() { return m_chart; }

    @Override
    public void setStateChart(StateChart<MyContext> machine) {
        Utilities.checkState(m_chart == null, "already registered");
        m_chart = machine;
    }
}
```

**구현 계약**: `setStateChart`가 두 번 호출되면 반드시 `IllegalStateException`을 던져 거부해야 한다 (양방향 참조 일관성 보존).

`StateChart` 생성자는 자동으로 `m_context.setStateChart(this)`를 호출하므로 사용자는 직접 호출할 필요 없다.

### 3.2 `StateChart<C>` — 차트 본체

`AbstractAsyncExecution<C>`를 상속하여 비동기 실행 모델을 따른다. 구성/실행 흐름:

```
new StateChart(context)
  ↓
addState(...)        ← 차트가 시작되기 전에만 가능
addState(...)
setInitialState(path)
addFinalState(path)
  ↓
start()              ← 초기 상태 enter, 차트 RUNNING
  ↓
handleSignal(signal) ← 현 상태가 transition 선택, 차트가 traverse
handleSignal(signal)
...
  ↓
최종 상태 도달 또는 fail()/cancel(true) 호출
  ↓
COMPLETED / FAILED / CANCELLED
```

주요 메서드:

| 메서드 | 호출 시점 | 효과 |
|---|---|---|
| [`addState(State<C>)`](../src/main/java/utils/statechart/StateChart.java#L63) | start 전 | 상태를 차트에 등록 (path가 키) |
| [`setInitialState(String)`](../src/main/java/utils/statechart/StateChart.java#L74) | start 전 | 초기 상태의 path 지정 |
| [`addFinalState(String)`](../src/main/java/utils/statechart/StateChart.java#L86) | start 전 | 최종 상태로 등록 (도달 시 자동 complete) |
| [`start()`](../src/main/java/utils/statechart/StateChart.java#L103) | 한 번 | initial state.enter() → RUNNING |
| [`handleSignal(Signal)`](../src/main/java/utils/statechart/StateChart.java#L151) | RUNNING 동안 | 현 상태가 transition 선택, traverse |
| [`getCurrentState()`](../src/main/java/utils/statechart/StateChart.java#L98) | 언제든 | 현재 상태 (lock 보유 후 안전 접근) |
| [`complete()`](../src/main/java/utils/statechart/StateChart.java#L180) | 사용자 호출 또는 자동 | 정상 완료 통지 |
| [`fail(Throwable)`](../src/main/java/utils/statechart/StateChart.java#L192) | 사용자 호출 또는 자동 | 실패 통지 |
| [`cancelWork()`](../src/main/java/utils/statechart/StateChart.java#L219) | `cancel(true)` 경유 | 현 상태 exit + 종료 |

### 3.3 `State<C>` — 상태

```java
public interface State<C extends StateContext<C>> {
    @NotNull String getPath();                                // 고유 식별 경로
    @NotNull C getContext();                                  // 컨텍스트 접근
    default void enter() { }                                  // 상태 진입 hook
    default void exit() { }                                   // 상태 진출 hook
    Optional<Transition<C>> selectTransition(Signal signal);  // 신호 처리
}
```

`enter`/`exit`은 default no-op. 부수 작업이 필요한 상태만 override한다.

`selectTransition`은 **부수 효과 없이** 전이 후보를 선택만 한다 (실제 실행은 `Transition.execute`가 담당, 차트가 호출).

### 3.4 사전 제공 상태 타입

#### [`AbstractState<C>`](../src/main/java/utils/statechart/AbstractState.java) — 공통 베이스

`path`/`context`/`pathSegments`를 보유. 사용자 정의 상태는 보통 이 클래스를 상속한다.

#### [`SingleOutState<C>`](../src/main/java/utils/statechart/SingleOutState.java) — 단일 trigger 신호

특정 신호가 도착하면 등록된 단일 전이 수행. 일치하지 않으면 `Optional.empty()` 반환.

```java
new SingleOutState<>(path, context, triggerSignal, transition);
new SingleOutState<>(path, context, triggerSignal, () -> dynamicallyComputedTransition);  // Supplier 변형
```

신호 비교는 `equals()`로 수행되므로 trigger 신호는 enum 또는 싱글턴 패턴 권장.

#### [`SinkState<C>`](../src/main/java/utils/statechart/SinkState.java) — 흡수 상태

`exit`이 호출되면 `UnsupportedOperationException`을 던지고 모든 신호를 거부. **최종 상태**로 등록할 때 적합.

#### [`ExceptionState<C>`](../src/main/java/utils/statechart/ExceptionState.java) — 실패 흡수

`SinkState`를 상속하며 `m_failureCause`를 추가로 보유. 차트가 이 상태에 도달하면 `traverse` 로직이 자동으로 `fail(cause)`를 호출하여 차트를 `FAILED`로 종료시킨다.

```java
ExceptionState<MyCtx> errState = new ExceptionState<>("error", ctx);
errState.setFailureCause(new RuntimeException("..."));
chart.addState(errState);
chart.addFinalState("error");
```

#### [`CompositeState<C>`](../src/main/java/utils/statechart/CompositeState.java) / [`DefaultCompositeState<C>`](../src/main/java/utils/statechart/DefaultCompositeState.java) — 합성 상태

내부에 자체 초기/현재 서브 상태를 보유하는 컨테이너 상태. 자세한 사용은 §6에서 다룬다.

#### 사용자 정의 상태

```java
public class MyState extends AbstractState<MyCtx> {
    public MyState(String path, MyCtx ctx) { super(path, ctx); }

    @Override public void enter() {
        // 진입 작업 (DB 연결, 타이머 시작 등)
    }

    @Override public void exit() {
        // 정리 작업
    }

    @Override
    public Optional<Transition<MyCtx>> selectTransition(Signal signal) {
        if (signal instanceof MySpecificSignal s) {
            return Optional.of(Transitions.create(s.targetPath(), (ctx, sig) -> {
                // 액션
            }));
        }
        return Optional.empty();
    }
}
```

### 3.5 `Signal` — 마커 인터페이스

```java
public interface Signal { }
```

비어 있는 marker. 사용자가 도메인 신호를 enum 또는 record로 정의:

```java
public enum CommandSignal implements Signal { START, STOP, RESET }

public record DataReceived(byte[] payload) implements Signal { }
```

### 3.6 `Transition<C>` — 전이

```java
public interface Transition<C extends StateContext<C>> {
    Optional<String> getTargetStatePath();  // 목표 상태 path
    default boolean isSelfTransition() { return getTargetStatePath().isEmpty(); }
    void execute(C context, Signal signal);  // 전이 액션
}
```

**self-transition 컨벤션**: `getTargetStatePath()`가 `Optional.empty()`이면 self-transition으로 취급되어 상태 변경이 일어나지 않으며 `execute`도 호출되지 않는다.

### 3.7 [`Transitions`](../src/main/java/utils/statechart/Transitions.java) — 전이 정적 팩토리

세 가지 표준 변형:

```java
// 목표 + 액션
Transition<MyCtx> t1 = Transitions.create("target", (ctx, signal) -> {
    // 전이 시 수행할 작업
});

// 목표만 (액션 없음)
Transition<MyCtx> t2 = Transitions.noop("target");

// self-transition (상태 유지, 액션 없음)
Transition<MyCtx> t3 = Transitions.stay();
```

사용자가 `Transition` 인터페이스를 직접 구현할 필요는 거의 없다.

---

## 4. 차트 라이프사이클

### 4.1 구성 단계

```java
// 1. 컨텍스트와 차트 생성
MyContext ctx = new MyContext(...);
StateChart<MyContext> chart = new StateChart<>(ctx);

// 2. 상태 등록 (start 전에만 가능)
chart.addState(new SingleOutState<>("a", ctx, ..., ...));
chart.addState(new SingleOutState<>("b", ctx, ..., ...));
chart.addState(new SinkState<>("done", ctx));

// 3. 초기/최종 상태 지정
chart.setInitialState("a");
chart.addFinalState("done");
```

검증 포인트:
- `addState` 이후 `setInitialState`/`addFinalState` 호출 — path가 등록된 상태인지 검증
- `start()` 시점에 initial이 final에 포함되지 않았는지, final이 비어있지 않은지 검증

### 4.2 시작과 신호 처리

```java
chart.start();  // initial state.enter() 호출, 차트 RUNNING

// 신호 처리는 차트 내부 lock(Guard) 으로 직렬화됨
chart.handleSignal(mySignal);
```

`handleSignal` 흐름:

```
lock 획득
  ├─ 차트가 RUNNING이 아니면 → 무시 + Optional.empty() 반환
  ├─ 현 상태의 selectTransition(signal) 호출
  ├─ Optional<Transition> 결과:
  │   ├─ empty → 신호 무시 (전이 없음)
  │   └─ present → traverse(transition, signal)
  │       ├─ self-transition이면 → 즉시 반환
  │       ├─ 현 상태 exit
  │       ├─ transition.execute(ctx, signal)
  │       ├─ 목표 상태 enter
  │       ├─ 목표가 final 이면 자동 complete() (또는 ExceptionState면 fail())
  │       └─ 예외 발생 시 notifyFailed
  └─ Optional<Transition> 반환
unlock
```

### 4.3 종료

세 가지 종료 경로:

1. **자동 complete** — 현 상태가 `addFinalState`에 등록된 상태로 도달 + `ExceptionState`가 아닌 경우
2. **자동 fail** — 현 상태가 `ExceptionState` (final 등록됨) 인 경우 또는 traverse 도중 예외 발생
3. **명시적 호출** — `chart.complete()`, `chart.fail(cause)`, `chart.cancel(true)`

종료 후 상태:
- `chart.isCompleted()` / `isFailed()` / `isCancelled()` — `AbstractAsyncExecution` 의 상태 조회
- `chart.waitForFinished()` — 종료까지 블록 대기
- `chart.whenFinished(callback)` — 종료 시 콜백 (비동기)
- `chart.getResult()` — completed 시 컨텍스트 반환, failed 시 예외 throw

---

## 5. 동시성 모델

### 5.1 단일 lock으로 직렬화

`StateChart`는 [`Guard`](../src/main/java/utils/async/Guard.java) 한 개를 보유하며, 다음 작업이 모두 같은 lock 안에서 실행된다:

- `start()`
- `handleSignal()`
- `complete()` / `fail()` / `cancelWork()`
- 상태의 `enter()` / `exit()` / `selectTransition()`
- 전이의 `execute()`

따라서 사용자가 작성한 enter/exit/transition 액션 코드는 **단일 스레드 직렬 실행**이 보장된다.

### 5.2 외부 신호 발송 스레드

여러 외부 스레드가 `handleSignal()`을 동시 호출해도 차트 lock으로 직렬화 처리된다. lock 경쟁이 발생하면 신호 처리가 직렬화되므로 처리 throughput에 영향이 있을 수 있다.

### 5.3 비동기 콜백

상태의 `enter()` 안에서 비동기 작업을 시작하고, 그 결과 콜백이 다시 `chart.handleSignal()`을 호출하는 패턴은 안전하다 (lock이 reentrant이지만, 일반적으로 콜백은 다른 스레드에서 발화되므로 별도 진입). 예: `OpenWebSocket.enter()`가 `HttpClient.buildAsync(...)`를 호출하고 결과 콜백에서 `Connected` 신호 발송.

### 5.4 `cancel(true)`

`AbstractAsyncExecution.cancel(true)`는 내부에서 `cancelWork()`를 호출. 차트는 현 상태의 `exit`을 시도하고 `CANCELLED`로 종료한다.

---

## 6. 합성 상태 (Composite State)

`CompositeState<C>`는 내부에 다른 상태들을 호스팅한다. 외부에서 보면 단일 상태이지만 내부적으로는 자체 초기/현재 서브 상태를 갖는다.

`DefaultCompositeState`는 가장 단순한 구현으로:
- `enter()` → 내부 currentState = initialState, 그 상태의 enter 호출
- `exit()` → 내부 currentState.exit, currentState = null
- `selectTransition(signal)` → 내부 currentState에 위임

```java
DefaultCompositeState<MyCtx> outer = new DefaultCompositeState<>("outer", ctx);
// 외부 차트에 등록
chart.addState(outer);

// 합성 상태 내부의 서브 상태 구성은 DefaultCompositeState.setInitialState() 등으로 별도 처리
```

> **Note**: 합성 상태의 서브 상태 등록 메서드는 패키지-프라이빗(`setInitialState`)이므로 같은 패키지 내에서 구성하거나 서브클래스로 확장해 사용하는 것이 일반적이다.

---

## 7. 에러 처리

### 7.1 사용자 액션 예외

`enter()`/`exit()`/`Transition.execute()` 도중 예외 발생 시:
- `start()` 단계: 차트가 시작 실패하고 `notifyFailed(cause)` 호출, `IllegalStateException` 호출자에게 전파
- `handleSignal()` → `traverse()` 단계: 예외를 catch하여 `notifyFailed(cause)` 호출, **호출자에게는 전파되지 않음** (차트가 FAILED 상태로 진입)

따라서 `handleSignal` 의 정상/실패 여부는 반환값이 아니라 차트의 종료 상태로 관찰해야 한다 (`whenFinished`, `getResult()`).

### 7.2 `ExceptionState` 패턴

명시적 에러 흡수 상태:

```java
ExceptionState<MyCtx> errState = new ExceptionState<>("error", ctx);
chart.addState(errState);
chart.addFinalState("error");

// 일반 상태에서 에러 신호 시 errState로 전이하면서 cause 주입
state.selectTransition(errorSignal) ==
    Optional.of(Transitions.create("error", (ctx, sig) -> {
        ((ExceptionState<MyCtx>) ctx.getStateChart().getState("error"))
                .setFailureCause(((MyErrorSignal)sig).cause());
    }));
```

`error` 상태로 진입하면 `traverse`가 자동으로 `fail(cause)` 호출.

### 7.3 신호 처리 자체의 예외

`handleSignal` 안에서 잡히지 않은 RuntimeException은:
- catch 절이 `notifyFailed(cause)` 호출
- 예외는 호출자에게 rethrow됨 (다른 종료 경로와 다른 점)

---

## 8. 패턴 / 모범 사례

### 8.1 Signal은 enum 또는 record로

`SingleOutState`의 비교가 `equals()` 기반이므로 enum 싱글턴 또는 record (구조 동일성) 권장. 매번 `new MySignal()` 형태는 instance equality 미스로 trigger 매칭 실패.

### 8.2 State path는 dot-notation

`AbstractState` 생성자에서 path를 `.`으로 분리하여 `pathSegments` 리스트 생성. 합성 상태 계층 표현에 자연스럽다 (`"root.child.grandchild"`).

### 8.3 enter는 가벼운 setup, 무거운 작업은 비동기

`enter()`가 차트 lock 안에서 실행되므로 시간이 오래 걸리는 작업은 다른 신호 처리를 막는다. 비동기 작업은:

```java
@Override public void enter() {
    longRunningTaskAsync(getContext()).whenComplete((result, err) -> {
        Signal signal = (err != null) ? new ErrorSignal(err) : new DoneSignal(result);
        getContext().getStateChart().handleSignal(signal);
    });
}
```

### 8.4 ExceptionState로 실패 경로 명시

각 상태가 자신만의 에러 처리를 가지지 말고, 차트 레벨의 단일 에러 상태로 fan-in하는 패턴이 깔끔.

### 8.5 컨텍스트는 immutable에 가깝게

차트 lock 외부에서 컨텍스트가 변경되면 race 가능. setter는 framework 콜백(`setStateChart`) 정도로만 두고, 도메인 데이터는 final 권장.

---

## 9. 실전 예제 — `WebSocketStateChart`

`utils.websocket` 패키지가 본 프레임워크의 실제 사용 예시:

| 클래스 | 역할 |
|---|---|
| [`WebSocketContext`](../src/main/java/utils/websocket/WebSocketContext.java) | `StateContext<WebSocketContext>` — server URL + WebSocket 보유 |
| [`WebSocketStateChart`](../src/main/java/utils/websocket/WebSocketStateChart.java) | `StateChart<WebSocketContext>` 확장 — 송신 API + ErrorMessage/ConnectionClosed fall-back |
| [`States.OpenWebSocket`](../src/main/java/utils/websocket/States.java) | `enter()`에서 비동기 연결 시도 → 결과로 `Connected`/`ConnectionFailed` 신호 발송 |
| [`States.CompletedState`](../src/main/java/utils/websocket/States.java) | `SinkState` — `enter()`에서 정상 close 송신 |
| [`States.ErrorState`](../src/main/java/utils/websocket/States.java) | `ExceptionState` — `enter()`에서 비정상 close 송신 |

비동기 콜백 → 신호 발송 패턴, 합성 없는 평면 구조, ExceptionState 활용 등의 전형적 패턴을 한곳에서 볼 수 있다.

---

## 10. 자주 발견되는 함정

1. **`addState` 후 `setInitialState`** 순서를 지켜야 한다 (path 검증).
2. **상태 시작 후 구성 변경 금지** — `addState`/`setInitialState`/`addFinalState`는 모두 `isNotStarted()` 체크.
3. **신호 객체 동일성** — `SingleOutState`의 trigger 비교가 `equals()` 기반. enum 또는 record 사용.
4. **컨텍스트 한 번 set 보장** — `setStateChart` 두 번 호출되면 IllegalStateException. 재사용은 새 차트 인스턴스로.
5. **`handleSignal` 의 예외 누락** — 액션 실패가 호출자에게 전파되지 않으므로 종료 상태(`whenFinished`)를 반드시 관찰.
6. **lock 안에서 블로킹 작업 금지** — enter/exit/transition 액션이 차트 lock을 보유하므로 무거운 작업은 비동기로.

---

## 11. API 참조 요약

| 클래스/인터페이스 | 책임 |
|---|---|
| [`StateContext<C>`](../src/main/java/utils/statechart/StateContext.java) | 도메인 데이터 + 차트 역참조 (CRTP) |
| [`StateChart<C>`](../src/main/java/utils/statechart/StateChart.java) | 차트 본체. 상태 등록/시작/신호 처리/종료 |
| [`Signal`](../src/main/java/utils/statechart/Signal.java) | 신호 marker |
| [`State<C>`](../src/main/java/utils/statechart/State.java) | 상태 인터페이스 (enter/exit/selectTransition) |
| [`AbstractState<C>`](../src/main/java/utils/statechart/AbstractState.java) | 공통 베이스 (path/context/pathSegments) |
| [`SingleOutState<C>`](../src/main/java/utils/statechart/SingleOutState.java) | 단일 trigger → 단일 전이 |
| [`SinkState<C>`](../src/main/java/utils/statechart/SinkState.java) | 흡수 상태 |
| [`ExceptionState<C>`](../src/main/java/utils/statechart/ExceptionState.java) | 실패 흡수 상태 |
| [`CompositeState<C>`](../src/main/java/utils/statechart/CompositeState.java) | 합성 상태 인터페이스 |
| [`DefaultCompositeState<C>`](../src/main/java/utils/statechart/DefaultCompositeState.java) | 합성 상태 기본 구현 |
| [`Transition<C>`](../src/main/java/utils/statechart/Transition.java) | 전이 인터페이스 |
| [`TransitionAction<C>`](../src/main/java/utils/statechart/TransitionAction.java) | 전이 액션 (`BiConsumer<C, Signal>`) |
| [`Transitions`](../src/main/java/utils/statechart/Transitions.java) | 전이 정적 팩토리 |
