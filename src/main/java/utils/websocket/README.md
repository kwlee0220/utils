## utils.websocket — WebSocket 상태머신

JDK `java.net.http.WebSocket` 클라이언트를 [`utils.statechart`](../statechart/) 의 `StateChart` 위에 얹어,
연결 / 메시지 수신 / 오류 / 종료를 신호 기반으로 처리하는 라이브러리.

### 기본 사용

```java
// 상태 차트 수행 과정에서 사용할 상태 정보를 정의
class MyCtx extends WebSocketContext<MyCtx> {
    MyCtx(String url) { super(url); }
}

MyCtx ctx = new MyCtx("ws://example.com/socket");
WebSocketStateChart<MyCtx> chart = new WebSocketStateChart<>(ctx);

// (선택) keep-alive
// 매 30초마다 ping 메시지를 보내고, ping 메시지 전송 후
// 5초 이내에 pong 메시지가 도착하지 않으면 alive하지 않다고 판단함
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

두 값이 모두 설정되어야 keep-alive 가 활성화되며, **연결이 열리기 전**에 설정해야 한다
(타이머는 `onOpen` 에서 한 번만 시작된다). ping 발송 후 pong 이 timeout 안에 도착하지 않으면
`ErrorMessage` (`RuntimeTimeoutException`) 가 차트에 전달되고 fall-back 으로 `fail` 처리된다.

감시는 **ping 단위**로 이루어진다. ping 마다 자신의 pong 을 감시하는 태스크가 예약되고 해당 pong 이
도착하면 취소되므로, 두 값의 대소 관계와 무관하게 동작한다. 대신 연결 두절 감지까지 최대
`pingInterval + pongTimeout` 이 걸린다 — 위 예에서는 최대 35초다.

타이머는 차트 종료 시, 그리고 **pong timeout 이 처음 발동했을 때** 정리되며 한 번 정리되면
재개되지 않는다. `ErrorMessage` 를 받아 차트를 계속 실행시키는 transition 을 정의한 경우 그 이후로는
keep-alive 감시가 없다는 뜻이다.

pong 왕복 시간은 `utils.websocket` 로거를 DEBUG 로 내리면 `received PONG: elapsed=23ms` 형태로 확인할 수 있다.

### 종료 코드

```java
WebSocket ws = chart.getWebSocket();   // 차트 종료 후에도 마지막 WebSocket 참조 유지
```

연결 종료는 `chart.close()` 한 곳으로 모여 있다.

```java
chart.close();                                        // NORMAL_CLOSURE, "normal closure"
chart.close(WebSocket.NORMAL_CLOSURE, "bye");         // 사유 지정
```

**best-effort 연산이다.** 연결 전이거나, 이미 닫혔거나, close 전송이 실패해도 조용히 넘어가므로
반복 호출해도 안전하다. close 프레임이 전송될 때까지 블록하지만 상대의 응답은 기다리지 않는다.

**차트가 종료되면 자동으로 호출된다.** 생성자에서 등록한 `whenFinished` 훅이 담당하기 때문에,
`States.CompletedState` / `CancelledState` / `ErrorState` 로 전이하는 경우는 물론
`complete()` / `fail()` / `cancel(true)` 를 직접 부르거나 사용자 정의 final state 를 쓰는 경우에도
연결이 남지 않는다. 따라서 위 메소드를 직접 부를 일은 **차트를 끝내지 않고 연결만 끊고 싶을 때**뿐이다.
close 사유는 실패 종료면 `"closure due to a error"`, 그 외에는 `"normal closure"` 가 된다.

### 한계

- **재연결 미지원** — 한 `WebSocketStateChart` 인스턴스는 1회 연결 only. 재연결이 필요하면 새 인스턴스 생성.
- **멀티 connection 미지원** — 단일 WebSocket 만 관리. 풀이 필요하면 호출 측에서 직접 관리.

자세한 contract 는 [`WebSocketStateChart`](WebSocketStateChart.java) / [`WebSocketContext`](WebSocketContext.java)
의 Javadoc 과 [`utils.statechart`](../statechart/README.md) 의 가이드 참고.
