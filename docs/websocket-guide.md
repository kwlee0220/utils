# `utils.websocket` 사용자 가이드

JDK 11+ 의 [`java.net.http.WebSocket`](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/WebSocket.html) 클라이언트를 두 가지 상위 API로 감싼 패키지.

| API | 기반 | 적합한 상황 |
|---|---|---|
| [`WebSocketObservable`](../src/main/java/utils/websocket/WebSocketObservable.java) | RxJava3 `Observable` | 단순 메시지 수신 파이프라인. 메시지를 reactive 스트림으로 처리하면 충분한 경우. |
| [`WebSocketStateChart`](../src/main/java/utils/websocket/WebSocketStateChart.java) | [`utils.statechart`](statechart-guide.md) | 연결 → 인증 → 메시지 송수신 → 종료처럼 단계별 상태 전이가 필요한 워크플로우. 송신 API와 keep-alive(ping/pong)가 필요한 경우. |

이 가이드는 두 API를 중심으로 패키지 구성, 사용법, 흔한 함정을 정리한다.

---

## 1. 패키지 구성

| 파일 | 역할 |
|---|---|
| [`WebSocketObservable`](../src/main/java/utils/websocket/WebSocketObservable.java) | RxJava3 `Observable<Message>` 어댑터. Builder + sealed `Message` 계층 포함. |
| [`WebSocketStateChart`](../src/main/java/utils/websocket/WebSocketStateChart.java) | `StateChart<C>` 확장. 송신 API + keep-alive 설정 + ErrorMessage/ConnectionClosed fall-back. |
| [`WebSocketContext`](../src/main/java/utils/websocket/WebSocketContext.java) | `WebSocketStateChart`의 도메인 컨텍스트. 서버 URL 보유. |
| [`WebSocketListener`](../src/main/java/utils/websocket/WebSocketListener.java) | (package-private) `WebSocket.Listener` 구현. 콜백 → 도메인 신호 변환 + ping 타이머. |
| [`Signals`](../src/main/java/utils/websocket/Signals.java) | `Connected`, `ConnectionFailed`, `TextMessage`, `BinaryMessage`, `ErrorMessage`, `ConnectionClosed`. |
| [`States`](../src/main/java/utils/websocket/States.java) | 재사용 가능한 상태 (`OpenWebSocket`, `CompletedState`, `CancelledState`, `ErrorState`). |
| [`WebSocketSignal`](../src/main/java/utils/websocket/WebSocketSignal.java) | `WebSocket` 참조를 보유한 신호 베이스. |

---

## 2. `WebSocketObservable` — RxJava3 어댑터

### 2.1 빠른 시작

```java
WebSocketObservable.builder()
        .uri("ws://example.com/ws")
        .build()
        .subscribe(msg -> {
            switch ( msg ) {
                case TextMessage tm   -> System.out.println("text: " + tm.getText());
                case BinaryMessage bm -> System.out.println("binary: " + bm.getBytes().length + " bytes");
            }
        });
```

`Message`는 [sealed interface](../src/main/java/utils/websocket/WebSocketObservable.java#L176)이므로 `switch` 패턴매칭에서 컴파일러가 분기 누락을 검증한다.

### 2.2 Builder 옵션

```java
WebSocketObservable obs = WebSocketObservable.builder()
        .uri("ws://example.com/ws")          // 필수
        .httpClient(myHttpClient)            // 선택. 미지정 시 HttpClient.newHttpClient()
        .collectTextFragments(true)          // 기본 true
        .collectBinaryFragments(true)        // 기본 true
        .build();
```

| 메서드 | 의미 |
|---|---|
| [`uri(String)`](../src/main/java/utils/websocket/WebSocketObservable.java#L116) | 연결 URL. 호출하지 않으면 `build()`에서 `IllegalArgumentException`. |
| [`httpClient(HttpClient)`](../src/main/java/utils/websocket/WebSocketObservable.java#L128) | 사용할 HTTP 클라이언트. 자체 풀/타임아웃을 쓰고 싶을 때만 지정. |
| [`collectTextFragments(boolean)`](../src/main/java/utils/websocket/WebSocketObservable.java#L140) | `true`(기본): 모든 fragment를 누적해 `last=true`에서 합쳐진 단일 `TextMessage` emit. `false`: fragment 단위로 즉시 emit (`isLast()`는 원본 플래그). |
| [`collectBinaryFragments(boolean)`](../src/main/java/utils/websocket/WebSocketObservable.java#L152) | `BinaryMessage`에 동일한 fragment 누적/패스스루 선택. |

대용량 binary를 스트리밍 처리(progress 표시 등)하려면 `collectBinaryFragments(false)`로 두고 직접 누적해야 메모리 폭주 없이 처리할 수 있다.

### 2.3 메시지 타입

```java
sealed interface Message permits TextMessage, BinaryMessage {}

final class TextMessage   implements Message { String getText();  boolean isLast(); }
final class BinaryMessage implements Message { byte[] getBytes(); boolean isLast(); }
```

> ⚠️ `BinaryMessage.getBytes()`는 **내부 배열을 직접 반환**한다 ([Javadoc](../src/main/java/utils/websocket/WebSocketObservable.java#L226)). 보관/수정이 필요하면 호출자가 `clone()` 또는 `Arrays.copyOf(...)`로 복사해야 한다.

### 2.4 구독 라이프사이클

`subscribe`마다 **새로운 WebSocket 연결**이 생성된다. 한 인스턴스를 여러 번 구독해도 연결을 공유하지 않는다 — 멀티캐스트가 필요하면 `.publish().refCount()` 등 RxJava 연산자로 감싼다.

| 이벤트 | 동작 |
|---|---|
| 서버 → 텍스트 | `onNext(TextMessage)` |
| 서버 → 바이너리 | `onNext(BinaryMessage)` |
| 서버가 close | `onComplete` + `WebSocket.sendClose(statusCode, reason)` |
| 통신 오류 | `onError(Throwable)` + `sendClose(NORMAL_CLOSURE, "error")` |
| `Disposable.dispose()` 호출 | `sendClose(NORMAL_CLOSURE, "disposed")` |

`buildAsync` 도중 `dispose()`가 먼저 호출되더라도 [늦게 도착한 WebSocket을 즉시 close](../src/main/java/utils/websocket/WebSocketObservable.java#L264)하므로 leak이 발생하지 않는다.

### 2.5 Backpressure

매 `onText`/`onBinary` 처리 후 [`webSocket.request(1)`](../src/main/java/utils/websocket/WebSocketObservable.java#L366)을 호출해 한 번에 1개 메시지만 받는다. RxJava `Observable`은 backpressure 지원이 없지만 WebSocket 측 흐름 제어가 한 단계 앞에서 동작하므로 downstream이 느릴 경우 OS TCP 버퍼에서 막힌다.

---

## 3. `WebSocketStateChart` — 상태 차트 기반 WebSocket

`utils.statechart`를 모르면 [statechart 가이드](statechart-guide.md)를 먼저 보는 것을 권장.

### 3.1 4가지 구성 요소

```
                ┌────────────────────────────────┐
                │     WebSocketStateChart<C>     │
                │   ┌─────────────────────────┐  │
                │   │    WebSocketListener    │──┼─→ ping/pong, fragment 누적
                │   └─────────────────────────┘  │       │ onText/onBinary/onError/onClose
                │              ▲                 │       ▼
                │              │ (콜백)           │   handleSignal(Signals.*)
                │   ┌─────────────────────────┐  │       │
                │   │  java.net.http.WebSocket│  │       ▼
                │   └─────────────────────────┘  │   사용자 정의 transition 또는
                │                                │   fall-back (fail / cancel)
                └────────────────────────────────┘
                                 ▲
                                 │ getStateChart()
                ┌────────────────┴────────────────┐
                │       WebSocketContext<C>       │  serverUrl, etc.
                └─────────────────────────────────┘
```

- **컨텍스트** [`WebSocketContext<C>`](../src/main/java/utils/websocket/WebSocketContext.java) — 서버 URL과 차트 역참조. `<C extends WebSocketContext<C>>` CRTP self-bound로 도메인별 데이터를 추가한 서브클래스를 정의한다.
- **차트** [`WebSocketStateChart<C>`](../src/main/java/utils/websocket/WebSocketStateChart.java) — `StateChart<C>` 확장. 송신 API(`sendText`/`sendBinary`), keep-alive 설정, fall-back 신호 처리.
- **리스너** [`WebSocketListener<C>`](../src/main/java/utils/websocket/WebSocketListener.java) — 차트가 보유. `WebSocket.Listener` 콜백을 [`Signals`](../src/main/java/utils/websocket/Signals.java)로 변환해 차트에 발송.
- **신호** [`Signals`](../src/main/java/utils/websocket/Signals.java) — 아래 6종.

### 3.2 신호 카탈로그

| 신호 | 발생 시점 | 동봉 데이터 |
|---|---|---|
| [`Connected`](../src/main/java/utils/websocket/Signals.java#L16) | 연결 성공 (`OpenWebSocket.enter` 의 `whenComplete`) | `WebSocket` |
| [`ConnectionFailed`](../src/main/java/utils/websocket/Signals.java#L26) | 연결 실패 | `Throwable` |
| [`TextMessage`](../src/main/java/utils/websocket/Signals.java#L43) | 텍스트 메시지 수신 (모든 fragment 누적 후 발송) | `String` |
| [`BinaryMessage`](../src/main/java/utils/websocket/Signals.java#L62) | 바이너리 fragment 수신 | `byte[]`, `last` |
| [`ErrorMessage`](../src/main/java/utils/websocket/Signals.java#L87) | `onError` 또는 pong timeout | `Throwable` |
| [`ConnectionClosed`](../src/main/java/utils/websocket/Signals.java#L106) | 서버가 close | `statusCode`, `reason` |

> 텍스트 신호는 listener 내부에서 [fragment를 모두 누적](../src/main/java/utils/websocket/WebSocketListener.java#L171)한 뒤 마지막 fragment에서 한 번 발송된다 (`WebSocketObservable`의 `collectTextFragments=true` 동작과 동일).
> 바이너리 신호는 fragment마다 발송하므로 사용자가 직접 합쳐야 한다.

### 3.3 라이프사이클

```
new WebSocketStateChart<>(context)               ← listener 도 함께 생성
  ↓
(선택) chart.setPingInterval(d) / setPongTimeout(d)   ← 연결 시작 전에만 효과
  ↓
chart.addState(...)                              ← OpenWebSocket 등 사용자 상태 등록
chart.setInitialState(...) / addFinalState(...)
  ↓
chart.start()                                    ← 초기 상태 enter → 연결 시도
  ↓
chart.sendText(...) / sendBinary(...)            ← 차트 RUNNING 동안 송신
  ↓
종료: 사용자 transition 또는 fall-back으로
      COMPLETED / CANCELLED / FAILED
```

### 3.4 제공되는 상태

[`States`](../src/main/java/utils/websocket/States.java)에는 자주 쓰는 상태가 미리 정의돼 있다.

| 상태 | 베이스 | `enter()` 동작 |
|---|---|---|
| [`OpenWebSocket`](../src/main/java/utils/websocket/States.java#L26) | `AbstractState` | 비동기로 `HttpClient.newWebSocketBuilder().buildAsync(uri, listener)` 호출. 성공 시 `setWebSocket(ws)` + `Connected` 신호 발송 → `targetStatePath`로 전이. 실패 시 `ConnectionFailed` 신호 → `failStatePath`로 전이. |
| [`CompletedState`](../src/main/java/utils/websocket/States.java#L86) | `SinkState` | `WebSocket.sendClose(NORMAL_CLOSURE, ...)` 후 join. |
| [`CancelledState`](../src/main/java/utils/websocket/States.java#L98) | `SinkState` | 위와 동일 (취소 경로용 명명). |
| [`ErrorState`](../src/main/java/utils/websocket/States.java#L110) | `ExceptionState` | 비정상 종료 경로용 close. |

### 3.5 차트 구성 예제 — 에코 클라이언트

연결 → 인사 송신 → 응답 1개 수신 → 정상 종료 차트:

```java
public class EchoContext extends WebSocketContext<EchoContext> {
    public EchoContext(String url) { super(url); }
}

EchoContext ctx = new EchoContext("ws://localhost:8080/echo");
WebSocketStateChart<EchoContext> chart = new WebSocketStateChart<>(ctx);

chart.setPingInterval(Duration.ofSeconds(20));
chart.setPongTimeout(Duration.ofSeconds(5));

// 1) 연결 상태
chart.addState(new States.OpenWebSocket<>(
        "open", ctx, chart, "running", "error"));

// 2) 메시지 송수신 상태 (사용자 정의)
chart.addState(new AbstractState<EchoContext>("running", ctx) {
    @Override public void enter() {
        chart.sendText("hello", true);  // 비동기 송신
    }
    @Override public Optional<Transition<EchoContext>> selectTransition(Signal sig) {
        if ( sig instanceof Signals.TextMessage tm ) {
            System.out.println("server replied: " + tm.getMessage());
            return Optional.of(Transitions.noop("done"));
        }
        return Optional.empty();
    }
});

// 3) 종료 상태
chart.addState(new States.CompletedState<>("done", ctx));
chart.addState(new States.ErrorState<>("error", ctx));

chart.setInitialState("open");
chart.addFinalState("done");

chart.start();
chart.waitForFinished();
```

`OpenWebSocket` 생성자의 `targetStatePath`("running") / `failStatePath`("error") 인자가 연결 결과별 분기 경로다.

### 3.6 송신 API

| 메서드 | 동작 |
|---|---|
| [`sendText(String, boolean)`](../src/main/java/utils/websocket/WebSocketStateChart.java#L162) | 비동기 텍스트 송신. `CompletableFuture<WebSocket>` 반환. |
| [`sendTextSync(String, boolean)`](../src/main/java/utils/websocket/WebSocketStateChart.java#L183) | 송신 후 블록. 예외는 `RuntimeExecutionException`으로 unwrap. |
| [`sendBinary(byte[], boolean)`](../src/main/java/utils/websocket/WebSocketStateChart.java#L203) | 비동기 바이너리 송신. |
| [`sendBinarySync(byte[], boolean)`](../src/main/java/utils/websocket/WebSocketStateChart.java#L224) | 위와 동일. |

송신은 차트가 `isRunning()`이고 `setWebSocket(...)`이 호출된 뒤에만 가능하다. 둘 중 하나라도 만족하지 않으면 `IllegalStateException`. 두번째 인자 `last`는 메시지 fragment 시퀀스의 마지막인지 여부다 (단일 메시지면 `true`).

### 3.7 Keep-alive (ping/pong)

`setPingInterval`과 `setPongTimeout`이 **둘 다** 설정된 경우에만 활성화된다. listener의 `onOpen` 시점에 ping 타이머가 시작된다.

```
[ping interval 마다]
   sendPing(payload)
   pong timeout 태스크 schedule
       ↓ 도중에 onPong 도착 → 태스크 cancel
       ↓ 도중에 도착 안함 → ErrorMessage(RuntimeTimeoutException) 신호 + 타이머 cancel
```

차트가 종료되면 `whenFinished` 콜백에서 [ping 타이머가 자동 정리](../src/main/java/utils/websocket/WebSocketListener.java#L71)된다 — 콜백은 생성자에서 한 번만 등록되어 재연결로 인한 누적이 없다.

`setPingInterval`/`setPongTimeout`은 listener가 사용되기 **전** (= 연결 시작 전) 에 호출되어야 한다. 연결 후 호출하면 다음 재연결 전까지 효과가 없다.

### 3.8 ErrorMessage / ConnectionClosed Fall-back

[`handleSignal`](../src/main/java/utils/websocket/WebSocketStateChart.java#L249)이 `super.handleSignal(signal)`을 먼저 호출한 뒤, **선택된 transition이 없고** 차트가 여전히 RUNNING이면 다음 fall-back을 적용한다:

| 신호 | Fall-back |
|---|---|
| `ErrorMessage` | `chart.fail(error)` → FAILED |
| `ConnectionClosed` | `chart.cancel(true)` → CANCELLED |

이 덕분에 사용자가 모든 상태에서 ErrorMessage/ConnectionClosed 처리 transition을 일일이 정의하지 않아도 차트가 dangling 상태에 머무르지 않는다. 단, 명시적으로 처리하고 싶다면 사용자 transition이 우선이다.

---

## 4. 두 API 비교

| 항목 | `WebSocketObservable` | `WebSocketStateChart` |
|---|---|---|
| 모델 | RxJava `Observable` | 상태 차트 |
| 송신 | 직접 지원하지 않음 (HttpClient WebSocket 별도 사용) | `sendText`/`sendBinary` 빌트인 |
| keep-alive | 자동 pong 응답만 (ping 발송 없음) | ping/pong 타이머 + timeout 감지 |
| 메시지 분기 | downstream에서 `switch` | 상태별 `selectTransition` |
| 다중 단계 워크플로우 | 부적합 | 적합 (인증 → 구독 → 송수신 등) |
| 종료 처리 | `onComplete` / `onError` / `dispose` | `whenFinished` + COMPLETED/CANCELLED/FAILED |
| Fragment | 누적/패스스루 선택 | 텍스트는 자동 누적, 바이너리는 fragment 단위 |

---

## 5. 자주 발견되는 함정

1. **`setPingInterval`/`setPongTimeout`은 연결 전에 호출**. 연결 후 호출하면 무시된다.
2. **`BinaryMessage.getBytes()`는 내부 배열 직접 반환** — 보관할 거면 복사하라.
3. **`WebSocketObservable`은 subscribe마다 새 연결** — 멀티캐스트하려면 `publish().refCount()`로 감싸라.
4. **`WebSocketStateChart`는 재연결 불가** — `setWebSocket`은 한 번만 가능. 재연결이 필요하면 새 차트 인스턴스를 생성하라.
5. **바이너리 메시지는 fragment 단위로 도착** (`WebSocketStateChart`) — 사용자 상태가 직접 누적해야 한다.
6. **차트 lock 안에서 블로킹 송신 금지** — `sendTextSync`/`sendBinarySync`를 transition 액션이나 `enter()`에서 호출하면 데드락 가능. 비동기 `sendText`/`sendBinary`를 우선 검토하라.
7. **사용자 ErrorMessage/ConnectionClosed transition이 있다면 fall-back은 동작하지 않음** — 명시 처리 시 종료 의무는 사용자에게 있다.

---

## 6. API 참조 요약

| 클래스/인터페이스 | 책임 |
|---|---|
| [`WebSocketObservable`](../src/main/java/utils/websocket/WebSocketObservable.java) | RxJava3 `Observable<Message>` 어댑터 |
| [`WebSocketObservable.Builder`](../src/main/java/utils/websocket/WebSocketObservable.java#L103) | URI / HttpClient / fragment 수집 모드 설정 |
| [`WebSocketObservable.Message`](../src/main/java/utils/websocket/WebSocketObservable.java#L176) | sealed 마커 (`TextMessage` `BinaryMessage`) |
| [`WebSocketStateChart<C>`](../src/main/java/utils/websocket/WebSocketStateChart.java) | 상태 차트 + 송신/keep-alive API |
| [`WebSocketContext<C>`](../src/main/java/utils/websocket/WebSocketContext.java) | 서버 URL 보유 도메인 컨텍스트 (CRTP) |
| [`WebSocketListener<C>`](../src/main/java/utils/websocket/WebSocketListener.java) | (package-private) 콜백 → 신호 변환 + ping 타이머 |
| [`Signals`](../src/main/java/utils/websocket/Signals.java) | 6종 도메인 신호 |
| [`States.OpenWebSocket`](../src/main/java/utils/websocket/States.java#L26) | 비동기 연결 상태 |
| [`States.CompletedState`](../src/main/java/utils/websocket/States.java#L86) | 정상 종료 SinkState |
| [`States.CancelledState`](../src/main/java/utils/websocket/States.java#L98) | 취소 경로 SinkState |
| [`States.ErrorState`](../src/main/java/utils/websocket/States.java#L110) | 실패 경로 ExceptionState |
