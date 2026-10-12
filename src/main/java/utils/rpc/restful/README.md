## utils.rpc.restful — RESTful RPC

원격 *연산(operation)* 을 HTTP 로 호출하고, 그 수행 과정을 [`utils.async`](../../async/README.md) 의
실행 객체로 다루는 라이브러리. 짧게 끝나는 연산은 동기로, 오래 걸리는 연산은 세션을 발급받아
폴링하는 비동기로 호출한다.

`utils.http` 의 `HttpRESTfulClient` 위에 얹혀 있어, 통신 오류는 `RESTfulIOException` /
`RESTfulRemoteException` 으로 올라온다.

### 어느 쪽을 쓸 것인가

| | `RESTfulRpcClient` | `RESTfulAsyncRpcClient` |
|---|---|---|
| 호출 | `call()` 이 응답까지 블록 | `start()` 후 폴링, `Execution` 으로 관찰 |
| 서버 응답 | 최종 상태 (`COMPLETED`/`FAILED`/`CANCELLED`) | 먼저 `RUNNING` + 세션 endpoint |
| 취소 | 불가 | `cancel(true)` → 세션 DELETE |
| 진행 상황 | 알 수 없음 | 폴링 주기마다 상태 확인 |
| 적합한 연산 | 수 초 이내 | 수 분 이상, 중간 취소가 필요한 경우 |

두 클라이언트는 **서버가 어느 방식으로 응답하도록 구현되어 있는지**에 맞춰 골라야 한다. 동기 클라이언트가
`RUNNING` 응답을 받으면 `IllegalStateException` 이 발생한다.

---

## 1. 동기 호출

```java
Map<String,JsonNode> inputs = Map.of("x", IntNode.valueOf(1));

RESTfulRpcClient client = RESTfulRpcClient.builder()
                                        .setOperationUrl("http://localhost:8080/operation")
                                        .setInputs(inputs)
                                        .setHttpClient(new OkHttpClient())   // 선택
                                        .setJsonMapper(MAPPER)               // 선택
                                        .build();

Map<String,JsonNode> outputs = client.call();
```

`setOperationUrl` 과 `setInputs` 만 필수다. `setHttpClient` 를 생략하면 기본 OkHttp 클라이언트가,
`setJsonMapper` 를 생략하면 기본 매퍼가 쓰인다.

`call()` 은 연산 상태에 따라 다음과 같이 끝난다.

| 서버 응답 상태 | 결과 |
|---|---|
| `COMPLETED` | 연산 출력(`Map<String,JsonNode>`) 반환 |
| `FAILED` | `ExecutionException` — cause 는 서버가 보낸 원인 예외를 복원한 것 |
| `FAILED` (원인이 `RESTfulIOException`) | 그 예외를 그대로 던짐 |
| `CANCELLED` | `CancellationException` (unchecked) |
| `RUNNING` | `IllegalStateException` — 동기 endpoint 가 아님 |

### 실패 원인 다루기

```java
try {
    Map<String,JsonNode> outputs = client.call();
}
catch ( ExecutionException e ) {
    Throwable cause = e.getCause();   // 서버가 보낸 code/message 로 복원된 예외
}
catch ( RESTfulException e ) {
    // 통신 실패, 또는 서버가 RESTful 에러 응답을 보낸 경우
}
```

복원된 예외는 서버의 클래스를 로드하지 않는다 — `RESTfulErrorEntity.toRemoteException()` 이
`code`/`message` 를 데이터로 보존한 `RESTfulRemoteException` 을 만든다. 자세한 내용은
[`utils.http`](../../http/CLAUDE.md) 참고.

---

## 2. 비동기 호출

```java
RESTfulAsyncRpcClient client = RESTfulAsyncRpcClient.builder()
                                        .setBaseUrl("http://localhost:8080")
                                        .setOperationEndpoint("operation")
                                        .setRequestMessage(new RpcRequestMessage(inputs))
                                        .setPollInterval(Duration.ofSeconds(3))   // 기본 3초
                                        .setTimeout(Duration.ofMinutes(10))       // 선택
                                        .build();

client.start();
Map<String,JsonNode> outputs = client.get();   // 종료까지 대기
```

`RESTfulAsyncRpcClient` 는 `AbstractPeriodicPoller` 이므로 `utils.async` 의 `Execution` API 를
그대로 쓸 수 있다.

```java
client.whenFinished(result -> { /* 성공/실패/취소 모두 여기서 */ });
client.waitForFinished(5, TimeUnit.MINUTES);
client.cancel(true);                      // 세션에 DELETE 전송
```

취소 결과는 **HTTP 상태 코드**로 구분한다. 본문의 상태값이 아니다.

| DELETE 응답 | `cancelWork()` |
|---|---|
| `200` + `CANCELLED` | `true` — 취소 확정 |
| `409 Conflict` | `false` — 이미 종료되었거나 연산이 취소를 거부함 |
| `404 Not Found` | `RESTfulException` 전파 — 세션이 없거나 보존 기간이 지남 |

`409` 와 `404` 는 다른 상황이므로 뭉뚱그리지 않는다. 전자는 "취소할 것이 없다", 후자는 "그런 세션이
없다" 이다.

### 동작 순서

```
start()
  └ POST {baseUrl}/{operationEndpoint}   ← RpcRequestMessage
      응답 RUNNING  → session_endpoint 를 기억
      응답 FAILED   → ExecutionException

[pollInterval 마다]
  └ GET {baseUrl}{session_endpoint}
      RUNNING    → 계속 폴링
      COMPLETED  → outputs 를 결과로 종료
      FAILED     → ExecutionException
      CANCELLED  → CancellationException
```

폴링 대상은 **세션 endpoint 그 자체**다. `/state` 같은 하위 경로를 붙이지 않는다.

`setBaseUrl` 과 세션 endpoint 는 이어 붙여져(`baseUrl + session_endpoint`) 폴링 URL 이 되므로,
서버는 base URL 기준의 **절대 경로**를 돌려주어야 한다 (전체 URL 이 아니라).

---

## 3. 메시지 포맷

### 요청 — `RpcRequestMessage`

```jsonc
{
  "inputs":  { "x": 1, "y": 2 },      // 필수
  "outputs": { "result": null }        // 선택 — 출력 위치 지정이 필요한 연산용
}
```

```java
new RpcRequestMessage(inputs);            // outputs 없이
new RpcRequestMessage(inputs, outputs);
```

`inputs`/`outputs` 이외의 최상위 필드는 `getExtraFields()` 에 보존되어 재직렬화 시 원래 자리로
복원된다. 서버 고유 필드를 실어 보낼 때 유용하지만, **`inputs` 를 오타내면 오류 없이 추가 필드로
받아들여져** 입력 없이 연산이 수행되므로 주의한다.

### 응답 — `RpcResponseMessage`

```jsonc
{
  "session_endpoint": "/api/v1/sessions/3",   // 비동기 연산에만
  "status": "COMPLETED",                       // RUNNING | COMPLETED | FAILED | CANCELLED
  "outputs": { "result": 42 },                 // 항상 존재 (없으면 {})
  "error": { "code": "...", "message": "..." } // FAILED 인 경우에만
}
```

| 메소드 | 설명 |
|---|---|
| `getState()` | `RpcState` |
| `getSessionEndpoint()` | 세션 endpoint. 동기 응답이면 `null` |
| `getOutputs()` | 연산 출력. **`null` 이 아님** — 없으면 빈 `Map` |
| `getError()` | 실패 정보. 실패가 아니면 `null` |
| `getJavaException()` | 실패 정보로부터 복원한 예외. **상태가 `FAILED` 가 아니면 `IllegalStateException`** |

서버를 직접 구현한다면 정적 팩토리를 쓴다.

```java
RpcResponseMessage.running(sessionEndpoint);
RpcResponseMessage.completed(sessionEndpoint, outputs);
RpcResponseMessage.failed(sessionEndpoint, cause);
RpcResponseMessage.cancelled(sessionEndpoint);
```

> **호환성 주의** — `status` / `session_endpoint` 는 이전 버전에서 `state` / `sessionEndpoint` 였다.
> alias 를 두지 않았으므로 **구버전과 신버전은 통신할 수 없다.** 서버와 클라이언트를 함께 배포해야 한다.
> 모르는 필드는 무시하므로(`ignoreUnknown`) 서버가 필드를 *추가* 하는 것은 안전하다.

---

## 4. 서버 구현

`RESTfulAsyncRpcServer` 를 상속하고 네 개의 추상 메소드를 구현한다. HTTP 계층(라우팅·직렬화)은
포함되어 있지 않으므로, 프레임워크에서 받은 요청을 `start`/`status`/`cancel` 로 넘겨주면 된다.

```java
class MyServer extends RESTfulAsyncRpcServer {
    private final AtomicInteger m_idGen = new AtomicInteger();

    @Override protected StartableExecution<?> createExecution(RpcRequestMessage req) { ... }
    @Override protected Map<String,JsonNode> getExecutionOutput(StartableExecution<?> exec) { ... }

    @Override protected AsyncRpcSession allocateSession(StartableExecution<?> exec) {
        return new AsyncRpcSession("/sessions/" + m_idGen.incrementAndGet(), exec);
    }
    @Override protected void releaseSession(AsyncRpcSession session) { /* 자원 정리 */ }
}
```

| 요청 | 서버 메소드 | 반환 |
|---|---|---|
| `POST /operation` | `start(reqMsg)` | `RUNNING` 또는 `FAILED` |
| `GET {session}` | `status(sessionEndpoint)` | 현재 상태. **세션이 없으면 `null` → 404 로 응답** |
| `DELETE {session}` | `cancel(sessionEndpoint)` | 취소 결과. 세션이 없으면 `null` → 404. **`CANCELLED` 가 아니면 409 로 응답** |

### 세션 수명

- 연산이 `RUNNING` 에 도달하면 세션이 등록되고, 종료되면 `releaseSession` 이 호출된다.
- **종료 결과는 한 번만 회수할 수 있다.** `status`/`cancel` 이 종료 상태를 반환하는 순간 세션은
  내부 맵에서 제거되므로, 같은 endpoint 를 다시 조회하면 `null` 이 나온다.
- 클라이언트가 결과를 회수하러 오지 않는 경우에 대비해 `getSessionRetainTimeout()`(기본 30초) 후
  자동 제거된다.
- 연산이 `getStartTimeout()`(기본 5초) 안에 시작되지 못하면 취소되고, 세션 endpoint 가 `null` 인
  `FAILED` 응답이 반환된다.

두 시한은 오버라이드하여 조정한다.

```java
@Override protected Duration getStartTimeout()          { return Duration.ofSeconds(30); }
@Override protected Duration getSessionRetainTimeout()  { return Duration.ofMinutes(5); }
```

---

## 5. 외부 명령을 연산으로 노출하기

`utils.rpc.restful.process` 의 `RESTfulCommandExecutionServer` 는 위 서버의 구현체로, JSON 서술자
하나로 외부 명령을 연산으로 만든다.

```jsonc
{
  "commandLine": ["/usr/bin/python3", "run.py", "${input:path}", "${result:path}"],
  "workingDirectory": "/var/lib/myop",
  "outputVariables": ["result"],
  "timeout": "10m",
  "startTimeout": "5s",
  "sessionRetainTimeout": "30s"
}
```

```java
var server = new RESTfulCommandExecutionServer(descriptorFile, new DefaultCommandVariableSerDe());
```

- 연산 입력은 `CommandVariable` 로 등록되어 명령줄 변수 치환으로 전달된다. `${name:path}` 는 값을
  파일로 만들어 그 절대 경로를 넘긴다.
- `outputVariables` 에 나열된 변수는 명령이 기록한 파일을 읽어 연산 출력이 된다.
- 요청마다 작업 디렉토리 아래 `session-<UUID>-<seq>` 디렉토리를 만들어 입출력 파일을 격리하고,
  세션 종료 시 삭제한다. 세션 endpoint 는 `/api/v1/sessions/<seq>` 형태다.
- 값과 파일 사이의 변환 규칙을 바꾸려면 `CommandVariableSerDe` 를 구현해 넘긴다.

**명령줄에 해석되지 않는 `${X}` 가 남아 있으면 연산이 실패한다.** 원본 토큰이 그대로 셸에 전달되지
않으므로, 변수 이름 오타는 조용히 넘어가지 않고 바로 드러난다.

---

## 한계

- **HTTP 서버 계층은 포함되지 않는다.** 라우팅과 직렬화는 사용하는 웹 프레임워크에서 연결해야 한다.
- **진행률 보고 채널이 없다.** 비동기 연산의 중간 상태는 `RUNNING` 하나뿐이며, 세부 진행 상황을
  실어 보내려면 `RpcResponseMessage` 를 확장해야 한다.
- **재시도·멱등성 지원이 없다.** 응답이 유실된 POST 를 재시도하면 연산이 중복 수행된다.

자세한 내부 계약과 함정은 [CLAUDE.md](CLAUDE.md) 를, HTTP 계층의 에러 처리는
[`utils.http`](../../http/CLAUDE.md) 및 [docs/http-guide.md](../../../../../../docs/http-guide.md) 를 참고한다.
