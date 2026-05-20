## utils.websocket — WebSocket 상태머신

JDK `java.net.http.WebSocket` 클라이언트를 [`utils.statechart`](../statechart/) 의 `StateChart` 위에 얹어,
연결 / 메시지 수신 / 오류 / 종료를 신호 기반으로 처리하는 라이브러리.

### 기본 사용

```java
class MyCtx extends WebSocketContext<MyCtx> {
    MyCtx(String url) { super(url); }
}

MyCtx ctx = new MyCtx("ws://example.com/socket");
WebSocketStateChart<MyCtx> chart = new WebSocketStateChart<>(ctx);

// (선택) keep-alive
chart.setPingInterval(Duration.ofSeconds(30));
chart.setPongTimeout(Duration.ofSeconds(5));

// 상태 정의
chart.addState(new States.OpenWebSocket<>("open", ctx, chart, "ready", "fail"));
chart.addState(/* "ready" — 메시지를 수신하는 사용자 정의 상태 */);
chart.addState(new States.CompletedState<>("done", ctx));
chart.addState(new States.ErrorState<>("fail", ctx));

chart.setInitialState("open");
chart.addFinalState("done");
chart.addFinalState("fail");

chart.start();
chart.waitForFinished();
```

### 신호 (Signals)

| 신호 | 발생 시점 | 페이로드 |
|------|----------|----------|
| `Connected` | WebSocket 연결 성공 | `WebSocket` |
| `ConnectionFailed` | 연결 실패 | 원인 `Throwable` |
| `TextMessage` | 텍스트 메시지 수신 (fragment 합쳐진 완전 메시지) | `String` |
| `BinaryMessage` | 바이너리 메시지 수신 (fragment 단위) | `byte[]`, `boolean last` |
| `ErrorMessage` | 연결 오류 발생 | `Throwable` |
| `ConnectionClosed` | 연결 종료 | `int statusCode`, `String reason` |

### 자동 종료 처리 (fall-back)

사용자 transition 이 `ErrorMessage` / `ConnectionClosed` 를 명시적으로 처리하지 않으면 차트는
자동으로:
- `ErrorMessage` → `fail(cause)`
- `ConnectionClosed` → `cancel(true)`

이 fall-back 덕분에 WebSocket 이 비정상 종료되어도 차트가 dangling 상태에 머무르지 않는다.

### 송신

```java
chart.sendText("hello", true);             // 비동기, CompletableFuture<WebSocket> 반환
chart.sendTextSync("hello", true);         // 동기 — InterruptedException 가능
chart.sendBinary(payload, true);
chart.sendBinarySync(payload, true);
```

차트가 `RUNNING` 이고 WebSocket 이 연결된 상태에서만 호출 가능 — 그렇지 않으면
`IllegalStateException`. 비동기 변형이 반환하는 future 는 송신이 완료되면 `WebSocket` 자체를 결과로 가진다.

### Keep-alive

```java
chart.setPingInterval(Duration.ofSeconds(30));
chart.setPongTimeout(Duration.ofSeconds(5));
```

두 값이 모두 설정되어야 keep-alive 가 활성화된다. 차트 시작 후 ping 타이머가 시작되며, ping 발송
후 pong 이 timeout 안에 도착하지 않으면 `ErrorMessage` (`RuntimeTimeoutException`) 이 차트에
전달되고 fall-back 으로 `fail` 처리된다. 차트 종료 시 타이머는 자동 정리.

### 종료 코드

```java
WebSocket ws = chart.getWebSocket();   // 차트 종료 후에도 마지막 WebSocket 참조 유지
```

`States.CompletedState` / `CancelledState` / `ErrorState` 는 진입 시 `NORMAL_CLOSURE` 로
WebSocket 을 닫는다.

### 한계

- **재연결 미지원** — 한 `WebSocketStateChart` 인스턴스는 1회 연결 only. 재연결이 필요하면 새 인스턴스 생성.
- **멀티 connection 미지원** — 단일 WebSocket 만 관리. 풀이 필요하면 호출 측에서 직접 관리.

자세한 contract 는 [`WebSocketStateChart`](WebSocketStateChart.java) / [`WebSocketContext`](WebSocketContext.java)
의 Javadoc 과 [`utils.statechart`](../statechart/README.md) 의 가이드 참고.
