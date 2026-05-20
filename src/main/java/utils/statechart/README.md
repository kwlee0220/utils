## utils.statechart — 계층적 상태머신

각 상태가 다음 상태로의 전이를 결정하는 신호 기반 상태머신. `StateChart` 자체가
`AbstractAsyncExecution<C>` 의 비동기 실행 객체이므로 시작 / 취소 / 완료 / 실패의 라이프사이클을
그대로 활용한다.

### 기본 흐름

```java
class MyContext implements StateContext<MyContext> {
    private StateChart<MyContext> m_chart;
    @Override public StateChart<MyContext> getStateChart() { return m_chart; }
    @Override public void setStateChart(StateChart<MyContext> m) {
        if (m_chart != null) throw new IllegalStateException("이미 등록됨");
        m_chart = m;
    }
}

MyContext ctx = new MyContext();
StateChart<MyContext> chart = new StateChart<>(ctx);   // 자동 attach

Signal go = new Signal() {};

// "idle" 에서 trigger 신호를 받으면 "running" 으로 이동
chart.addState(new SingleOutState<>("idle", ctx, go, Transitions.noop("running")));
chart.addState(new SinkState<>("running", ctx));

chart.setInitialState("idle");
chart.addFinalState("running");
chart.start();

chart.handleSignal(go);          // idle → running, 자동 complete
chart.waitForFinished();          // COMPLETED 상태 도달 대기
```

### 상태 (State)

| 클래스 | 용도 |
|--------|------|
| `AbstractState` | 일반 상태의 베이스 — `enter` / `exit` 오버라이드, `selectTransition` 직접 구현 |
| `SingleOutState` | 단일 trigger 신호에만 반응 — 가장 단순한 형태 |
| `DefaultCompositeState` | 초기 sub-state 를 두는 계층 상태 |
| `SinkState` | 종료 상태. `exit` / `selectTransition` 호출 시 UOE |
| `ExceptionState` | 실패 종료 상태 — `failureCause` 보유 |

### 전이 (Transition)

`Transitions` 정적 팩토리 사용 권장:

```java
// 단순 이동
Transitions.noop("nextState");

// 액션 + 이동
Transitions.create("nextState", (ctx, signal) -> {
    System.out.println("transitioning with " + signal);
});

// self-transition (현재 상태 유지, 액션 / exit / enter 모두 호출 안 됨)
Transitions.stay();
```

### 종료 처리

```java
chart.complete();          // 정상 종료
chart.fail(new RuntimeException("error"));   // 실패 종료
chart.cancel(true);        // 취소
```

또는 final state 에 진입 시 자동 종료:
- 일반 final state → `complete()` 자동 호출
- `ExceptionState` final state → `fail(cause)` 자동 호출 (cause 는 진입 transition 의 action 에서 setFailureCause 로 미리 설정)

### 보장과 한계

- 모든 transition / listener 호출은 내부 lock 으로 직렬화된다 (thread-safe).
- `handleSignal` 이 비-RUNNING 상태에서 호출되면 조용히 무시되고 `Optional.empty()` 를 반환한다.
- transition action 안의 긴 작업은 lock 을 점유하므로 다른 신호 처리가 차단된다 — 무거운 작업은
  별도 비동기 처리로 분리.

자세한 contract 는 [`StateChart`](StateChart.java) / [`State`](State.java) / [`Transition`](Transition.java) 의 Javadoc 참고.
