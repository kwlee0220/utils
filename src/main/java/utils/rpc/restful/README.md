# `utils.rpc.restful` 사용자 가이드

원격 *연산(operation)*을 HTTP로 호출하고, 실행 과정을 [`utils.async`](../../async/README.md)의 실행 객체로 관리하는 라이브러리다. 짧게 끝나는 연산은 동기 방식으로, 오래 걸리는 연산은 세션을 발급받아 상태를 폴링하는 비동기 방식으로 호출한다.

`utils.http`의 `HttpRESTfulClient`를 기반으로 구현되며, HTTP 호출 오류는 `RESTfulException` 계열 예외 (`RESTfulIOException`, `RESTfulRemoteException`)로 전달된다.

| 목적 | 읽을 장 |
|---|---|
| RPC 연산을 호출한다 | [1~3장](#1-호출-방식-선택), 메시지 형식은 [4장](#4-메시지-형식) |
| 새 RPC 서버를 구현한다 | [4장](#4-메시지-형식), [5장](#5-서버-구현) |
| 외부 명령을 RPC 연산으로 제공한다 | [6장](#6-외부-명령을-연산으로-제공하기) |

## 목차

- [1. 호출 방식 선택](#1-호출-방식-선택)
- [2. 동기 호출](#2-동기-호출)
- [3. 비동기 호출](#3-비동기-호출)
- [4. 메시지 형식](#4-메시지-형식)
- [5. 서버 구현](#5-서버-구현)
- [6. 외부 명령을 연산으로 제공하기](#6-외부-명령을-연산으로-제공하기)
- [7. 제약 사항](#7-제약-사항)
- [8. 관련 문서](#8-관련-문서)

---

## 1. 호출 방식 선택

| | `RESTfulRpcClient` | `RESTfulAsyncRpcClient` |
|---|---|---|
| 호출 | `call()`이 응답을 받을 때까지 호출 스레드에서 대기 | `start()` 후 폴링, `Execution`으로 관찰 |
| 서버 응답 | 최종 상태(`COMPLETED`/`FAILED`/`CANCELLED`) | 먼저 `RUNNING` 상태와 세션 엔드포인트를 반환 |
| 취소 | 불가 | `cancel(true)` → 세션에 DELETE 전송 |
| 진행 상황 | 알 수 없음 | 폴링 주기마다 상태 확인 |
| 적합한 연산 | 수 초 이내에 끝나는 연산 | 수 분 이상 걸리거나 중간 취소가 필요한 연산 |

두 클라이언트는 **서버가 어느 방식으로 응답하도록 구현되어 있는지**에 맞춰 선택한다.

- 동기 클라이언트가 `RUNNING` 응답을 받으면 `UnexpectedRpcStateException`(`IllegalStateException`의 하위 타입)이 발생한다. 이때 원격 연산은 이미 시작되었으므로, 예외의 `getResponse().getSessionEndpoint()`로 얻은 세션에 비동기 클라이언트를 연결하여([3.3절](#33-기존-세션에-연결)) 결과를 기다리거나 취소할 수 있다.
- 반대로 비동기 클라이언트가 시작 응답으로 곧바로 `COMPLETED`/`CANCELLED`를 받으면 그 응답을 최종 결과로 처리해 폴링 없이 종료한다.

---

## 2. 동기 호출

### 2.1 호출 예제

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

`setOperationUrl`과 `setInputs`만 필수다. `setHttpClient`를 생략하면 기본 OkHttp 클라이언트를, `setJsonMapper`를 생략하면 기본 매퍼를 사용한다.

### 2.2 응답 상태별 결과

`call()`은 서버 응답 상태에 따라 다음과 같이 동작한다.

| 서버 응답 상태 | 결과 |
|---|---|
| `COMPLETED` | 연산 출력(`Map<String,JsonNode>`)을 반환한다. |
| `FAILED` | `ExecutionException`을 던진다. 원인은 서버의 오류 코드와 메시지를 담은 `RESTfulRemoteException`이다. |
| `CANCELLED` | `CancellationException`(비검사 예외)을 던진다. |
| `RUNNING` | `UnexpectedRpcStateException`을 던진다. 동기 엔드포인트가 아니라는 뜻이며, `getResponse()`에 세션 엔드포인트가 있다. |

### 2.3 실패 원인 다루기

```java
try {
    Map<String,JsonNode> outputs = client.call();
}
catch ( IOException e ) {
    // 요청 메시지를 JSON으로 직렬화하지 못한 경우
}
catch ( ExecutionException e ) {
    RESTfulRemoteException cause = (RESTfulRemoteException)e.getCause();   // 서버가 보낸 code/message
}
catch ( RESTfulException e ) {
    // HTTP 호출 자체의 실패: 통신 오류, 또는 서버가 HTTP 오류 응답을 보낸 경우
}
```

`FAILED` 응답은 서버의 오류 코드와 메시지를 담은 `RESTfulRemoteException`으로 변환되며, `call()`은 이를 원인으로 갖는 `ExecutionException`을 던진다. HTTP 호출 자체의 실패는 이와 별도로 `RESTfulException` 계열 예외로 전달된다.

`call()`은 요청 메시지를 JSON으로 직렬화하지 못할 때 `IOException`을 던질 수 있다. 이는 검사 예외이므로 호출자는 이를 처리하거나 호출 메서드의 `throws` 절에 선언해야 한다.

오류를 예외로 변환할 때 서버가 지정한 예외 클래스를 로딩하지 않는다. `RESTfulErrorEntity.toRemoteException()`이 `code`/`message`를 데이터로 보존한 `RESTfulRemoteException`을 만든다. 자세한 내용은 [`utils.http` 가이드](../../http/README.md)를 참고한다.

---

## 3. 비동기 호출

### 3.1 호출 예제

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

`RESTfulAsyncRpcClient`는 `AbstractPeriodicPoller`를 상속하므로 `utils.async`의 `Execution` API를 그대로 사용할 수 있다.

```java
client.whenFinished(result -> { /* 성공·실패·취소 모두 여기서 처리 */ });
client.waitForFinished(5, TimeUnit.MINUTES);
client.cancel(true);                      // 세션에 DELETE 전송
```

### 3.2 실행 상태 조회

```
start()
  └ POST {baseUrl}/{operationEndpoint}   ← RpcRequestMessage
      응답 RUNNING  → session_endpoint를 기억
      응답 FAILED   → ExecutionException
      응답 COMPLETED/CANCELLED → 폴링 없이 그 응답으로 종료 (동기 연산을 비동기로 호출한 경우. 이 경우 cancelWork()는 false를 반환)

[pollInterval마다]
  └ GET {baseUrl}{session_endpoint}
      RUNNING    → 계속 폴링
      COMPLETED  → outputs를 결과로 종료
      FAILED     → ExecutionException
      CANCELLED  → CancellationException
```

폴링 대상은 **세션 엔드포인트 자체**다. `/state` 같은 하위 경로를 붙이지 않는다.

### 3.3 기존 세션에 연결

이미 시작된 세션에 연결하려면 `operationEndpoint`·`requestMessage` 대신 `setSessionEndpoint(endpoint)`를 지정한다. 그러면 POST 없이 그 세션을 폴링하고, `cancel`도 그 세션에 DELETE를 보낸다.

실행 중인 클라이언트의 세션 엔드포인트는 `getSessionEndpoint()`로, 기본 URL을 붙인 전체 URL은 `getSessionUrl()`로 얻는다. 둘 다 세션을 받기 전이거나 시작이 실패했거나 시작 응답이 곧 종료 상태였으면 `null`이다.

### 3.4 취소

`cancel(true)`는 로컬 실행을 `CANCELLING`으로 전이시킨 뒤, 세션 URL로 DELETE를 보내 원격 연산에 취소를 요청한다.

**`cancel(true)`의 결과.** 다음 표는 로컬 실행이 `RUNNING` 상태이고 세션 URL이 확보되어, `cancel(true)`가 DELETE 요청을 보낸 경우를 설명한다.

| DELETE 응답 | `cancel(true)` | 로컬 실행 |
|---|---|---|
| `200` + `CANCELLED` / `202` + `RUNNING` | `true` | 다음 폴링 시점에 `CANCELLED`로 종료 |
| `409 Conflict` | `true` | 다음 폴링 시점에 `CANCELLED`로 종료 |
| `404` 등 HTTP 오류 | `false` | `FAILED`로 전이 |

취소 요청 시점에 진행 중이던 폴링이 `COMPLETED` 응답을 받으면 로컬 실행은 `COMPLETED`로 끝날 수도 있다.

`409`는 원격 서버가 취소를 받아들이지 않았다는 뜻인데도 `cancel(true)`는 `true`를 반환한다. 따라서 `cancel(true)`의 반환값만으로 원격 서버의 취소 접수 여부를 판단해서는 안 된다. 서버 규약 위반으로 발생한 `AssertionError`(아래 참고)는 `Error`이므로 `cancel(true)`를 호출한 쪽으로 그대로 전달된다.

`waitForFinished()`는 클라이언트 실행 객체의 종료를 확인하는 용도다. 로컬 실행이 `CANCELLED`로 종료되어도 원격 연산의 취소 완료를 보장하지 않는다. 원격 연산의 최종 상태가 필요하면 세션 엔드포인트를 별도로 조회해야 한다. 예를 들어 `getSessionEndpoint()`로 얻은 세션 엔드포인트를 새 클라이언트의 `setSessionEndpoint()`에 전달해 같은 세션에 다시 연결한 뒤([3.3절](#33-기존-세션에-연결)) 폴링할 수 있다. 종료된 세션은 서버의 보존 기간(기본 30초, [5.4절](#54-세션-수명)) 동안만 조회할 수 있다.

**원격 취소 요청의 처리.** DELETE 요청과 응답 해석은 `cancelWork()`가 담당한다. `cancel(true)`는 `cancelWork()`의 반환값을 사용하지 않으며, `cancelWork()`가 예외를 던지면 이를 로그에 기록하고 실행을 `FAILED`로 전이시킨 뒤 `false`를 반환한다. `cancelWork()`는 HTTP 상태 코드와 본문의 `status`를 함께 확인한다. HTTP `409` 응답이면 `false`를 반환하고, 그 밖의 HTTP 오류는 `RESTfulException`으로 전달한다. 2xx 성공 응답에서는 본문의 `status`가 `CANCELLED` 또는 `RUNNING`이면 `true`를 반환한다.

| DELETE 응답 | `cancelWork()` |
|---|---|
| `200` + `CANCELLED` | `true` — 취소가 확정되었다. |
| `202` + `RUNNING` | `true` — 취소가 접수되었다. 원격 연산은 아직 정지 중일 수 있다. |
| `409 Conflict` | `false` — 연산이 이미 종료되었거나 취소 요청이 거부되었다. |
| `404 Not Found` | `RESTfulException` 전파 — 세션이 존재하지 않거나 보존 기간이 지나 제거되었다. |

2xx 응답의 `status`가 `COMPLETED`나 `FAILED`이면 서버가 규약을 어긴 것으로 보고 `AssertionError`를 던진다. 취소할 수 없는 경우 서버는 `409`로 응답해야 한다.

`202` + `RUNNING`은 정지에 시간이 걸리는 경우의 응답이다. `RESTfulAsyncRpcServer`는 취소 대기 시간 안에 멈추지 않은 연산에 이 응답을 보내고, 로봇 트윈은 감속 중인 팔에 `phase: CANCELING` 필드를 포함해 보낸다. 서버는 세션을 유지한 채 정지를 진행하고, 최종 `CANCELLED`는 이후의 세션 조회로만 확인된다. 클라이언트의 실행 객체는 다음 폴링 시점에 `CANCELLED`로 종료되지만, **이 시점에 원격 연산이 실제로 완전히 멈췄음은 보장되지 않는다.**

`409`와 `404`는 서로 다른 상황이다. `409`는 연산이 이미 종료되었거나 취소 요청이 거부되었음을 뜻하고, `404`는 세션이 존재하지 않거나 보존 기간이 지나 제거되었음을 뜻한다.

**DELETE를 보내지 않는 경우.** 시작 전이거나 이미 종료된 실행에 `cancel(true)`를 호출하면 DELETE 요청 없이 반환한다. 시작 응답이 곧 종료 상태였던 실행은 취소할 세션이 없으므로 `cancelWork()`가 `false`를 반환한다.

**세션 생성 전의 취소.** 시작 POST의 응답을 받기 전에는 취소를 보낼 세션 URL이 없다. 이때 `cancel`을 호출하면 즉시 DELETE를 보내지 않고 취소 요청을 기록한 뒤 `true`를 반환한다. 세션을 발급받으면 곧바로 DELETE를 전송하고, 이후 폴링을 이어 가며 원격 연산의 최종 상태를 확인한다. 시작이 거부되면 기록된 취소는 버려진다.

### 3.5 URL 구성

세션 URL은 기본 URL(`setBaseUrl`)과 세션 경로를 결합하여 구성한다. 기본 URL 끝과 세션 경로 앞의 `/`는 결합 과정에서 조정되므로, 세션 경로 앞의 `/`는 생략할 수 있다. 세션 엔드포인트에는 전체 URL이 아닌 경로를 지정한다. 연산 엔드포인트(`setOperationEndpoint`)도 같은 방식으로 결합한다.

---

## 4. 메시지 형식

### 4.1 요청 — `RpcRequestMessage`

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

`inputs`와 `outputs` 이외의 최상위 필드는 추가 필드(`getExtraFields()`)로 보존되며, 다시 직렬화할 때 최상위에 그대로 기록된다. 서버 고유 필드를 함께 보낼 때 사용한다. 다만 필수 필드인 `inputs`를 누락하거나 이름을 잘못 작성하면 요청 메시지 생성에 실패한다. 입력 인자가 없는 연산도 `"inputs": {}`를 지정해야 한다.

### 4.2 응답 — `RpcResponseMessage`

완료 응답:

```jsonc
{
  "session_endpoint": "/api/v1/sessions/3",   // 비동기 연산에만
  "status": "COMPLETED",                       // RUNNING | COMPLETED | FAILED | CANCELLED
  "outputs": { "result": 42 }                  // COMPLETED이면 필수
}
```

실패 응답:

```jsonc
{
  "session_endpoint": "/api/v1/sessions/3",
  "status": "FAILED",
  "error": { "code": "...", "message": "..." } // FAILED이면 필수
}
```

`COMPLETED` 응답에는 `outputs`가 반드시 있어야 한다. 출력값이 없으면 빈 객체 `{}`를 지정한다. 다른 상태에서 생략된 `outputs`는 빈 맵으로 처리한다. `FAILED` 응답에는 실패 정보를 담은 `error`가 필수다.

| 메서드 | 설명 |
|---|---|
| `getState()` | `RpcState` |
| `getSessionEndpoint()` | 세션 엔드포인트. 동기 응답이면 `null` |
| `getOutputs()` | 연산 출력 `Map`을 반환한다. **`null`을 반환하지 않으며**, 출력이 없으면 빈 `Map`을 반환한다. |
| `getError()` | 실패 정보. 실패가 아니면 `null` |
| `getJavaException()` | 실패 정보로 만든 `RESTfulRemoteException`. **상태가 `FAILED`가 아니면 `IllegalStateException`** |

서버를 직접 구현할 때는 정적 팩토리를 사용한다.

```java
RpcResponseMessage.running(sessionEndpoint);
RpcResponseMessage.completed(sessionEndpoint, outputs);
RpcResponseMessage.failed(sessionEndpoint, cause);
RpcResponseMessage.cancelled(sessionEndpoint);
```

> **필드 이름 주의** — 응답 필드 이름은 `status`와 `session_endpoint`다. `state`나 `sessionEndpoint`를 사용하는 상대와는 **통신할 수 없다.** 알 수 없는 필드는 무시하므로(`ignoreUnknown`) 서버가 응답에 새 필드를 *추가*해도 클라이언트 역직렬화는 깨지지 않는다.

---

## 5. 서버 구현

### 5.1 서버 클래스 작성

`RESTfulAsyncRpcServer`를 상속하고 네 개의 추상 메서드를 구현한다. HTTP 계층(라우팅·직렬화)은 포함되어 있지 않으므로, 웹 프레임워크에서 받은 요청을 `start`/`status`/`cancel`로 넘겨준다.

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

### 5.2 요청과 서버 메서드의 대응

| 요청 | 서버 메서드 | 반환 |
|---|---|---|
| `POST /operation` | `start(reqMsg)` | `RUNNING` 또는 `FAILED` |
| `GET {session}` | `status(sessionEndpoint)` | 현재 상태. **세션이 없으면 `null`이며, 404로 응답한다.** |
| `DELETE {session}` | `cancel(sessionEndpoint)` | `RpcCancelResult`(응답 메시지 + 취소 접수 여부). 세션이 없으면 `null`이며, 404로 응답한다. |

### 5.3 취소 결과와 HTTP 응답 코드

서버 클래스는 HTTP 상태 코드를 정하지 않는다. 웹 프레임워크의 라우팅 코드가 `cancel()`이 반환한 `RpcCancelResult`를 보고 다음과 같이 HTTP 상태 코드를 설정한다.

| `RpcCancelResult` | 의미 | HTTP 응답 |
|---|---|---|
| `null` | 세션이 없거나 보존 기간이 지나 제거됨 | `404 Not Found` |
| `isCancelled()` | 취소가 확정됨 | `200 OK` |
| `isStopping()` | 취소가 접수되었고 연산이 멈추는 중 | `202 Accepted` |
| 그 밖의 경우 | 취소 거부, 또는 이미 완료·실패로 종료됨 | `409 Conflict` |

### 5.4 세션 수명

- 연산이 `RUNNING`에 도달하면 세션이 등록되고, 연산이 종료되면 `releaseSession`이 호출된다.
- **종료된 세션은 `getSessionRetainTimeout()`(기본 30초) 동안 남는다.** 그동안 `status`를 여러 번 호출해도 같은 종료 결과를 반환한다. 보존 기간이 지나면 세션이 제거되며, 이후 `status()`는 `null`을 반환한다. 따라서 응답을 받지 못한 클라이언트가 다시 조회하거나, 연산이 끝난 직후 들어온 취소가 `409`로 거부된 뒤 다시 상태를 조회하더라도 정상적으로 결과를 받을 수 있다.
- 연산이 `getStartTimeout()`(기본 5초) 안에 시작되지 못하면 취소를 요청하고, 세션 엔드포인트가 `null`인 `FAILED` 응답이 반환된다.

### 5.5 취소 처리

`cancel()`은 다음 순서로 처리한다.

1. **취소 요청** — 실행 중인 연산에 `cancel(true)`로 취소를 요청한다. 이미 취소 중인 세션에는 취소를 다시 요청하지 않는다.
2. **접수 여부 판단** — 취소가 받아들여지지 않고 연산이 계속 실행 중이면 취소 거부(`accepted=false`)로 판단한다.
3. **종료 대기** — 취소가 접수되면 `getCancelWait()`(기본 2초)까지 연산 종료를 기다린다.
4. **결과 반환** — 대기 시간 안에 끝나면 종료 상태를, 끝나지 않으면 `RUNNING`(멈추는 중)을 담아 반환한다.

이미 종료된 세션이면 그 종료 상태를 담아 반환하며(`CANCELLED`인 경우만 접수로 본다), 세션은 지우지 않는다.

### 5.6 시간 설정

세 시간 값(`getStartTimeout()`/`getSessionRetainTimeout()`/`getCancelWait()`)은 오버라이드하여 조정한다. `getCancelWait()`는 취소를 요청하는 클라이언트의 응답 대기 시간(mdtpy는 3초)보다 짧아야 한다.

```java
@Override protected Duration getStartTimeout()          { return Duration.ofSeconds(30); }
@Override protected Duration getSessionRetainTimeout()  { return Duration.ofMinutes(5); }
```

내부 대기 동작 등 구현 세부 사항은 [CLAUDE.md](CLAUDE.md)의 "Server side" 절을 참고한다.

---

## 6. 외부 명령을 연산으로 제공하기

`utils.rpc.restful.process`의 `RESTfulCommandExecutionServer`는 `RESTfulAsyncRpcServer`의 구현체로, JSON 실행 명세에 지정한 외부 명령을 원격 연산으로 제공한다.

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

- 연산 입력은 `CommandVariable`로 등록되어 명령줄 변수 치환으로 전달된다. `${name:path}`는 값을 파일로 만들어 그 절대 경로를 전달한다.
- 서버는 `outputVariables`에 지정된 각 변수에 대해, 외부 명령이 해당 출력 파일에 기록한 값을 읽어 연산 출력으로 반환한다.
- 요청마다 세션 식별자 `<UUID>-<seq>`를 발급한다. UUID는 서버 객체마다 새로 만들고, seq는 요청마다 증가한다. 작업 디렉터리 아래에 `session-<UUID>-<seq>` 디렉터리를 생성해 입출력 파일을 격리하며, 세션이 종료될 때 (`releaseSession`) 해당 디렉터리를 삭제한다. 세션 엔드포인트는 `/api/v1/sessions/<UUID>-<seq>` 형태다. 서버를 다시 시작하면 UUID가 달라지므로, 재시작 전에 발급한 엔드포인트와 겹치지 않는다.
- 값과 파일 사이의 변환 규칙을 바꾸려면 `CommandVariableSerDe`를 구현해 전달한다. 입력 변수는 `deserialize`로, 출력 변수는 `deserializeOutput`으로 만든다. `deserializeOutput`의 기본 구현은 `deserialize`와 같으며, 출력 위치를 미리 읽지 않아야 하는 경우(예: 결과를 업로드할 원격 파일)에 재정의한다.

**명령줄에 해석되지 않은 `${X}`가 남아 있으면 연산이 실패한다.** 원본 토큰을 그대로 셸에 전달하지 않고, 해석할 수 없는 변수 참조는 오류로 처리한다.

---

## 7. 제약 사항

- **HTTP 서버 계층은 포함되지 않는다.** 라우팅과 직렬화는 사용하는 웹 프레임워크에서 연결해야 한다.
- **진행률 보고 채널이 없다.** 비동기 연산의 중간 상태는 `RUNNING` 하나뿐이며, 세부 진행 상황을 전달하려면 `RpcResponseMessage`를 확장해야 한다.
- **재시도와 멱등성을 지원하지 않는다.** POST 응답이 유실된 뒤 같은 요청을 재전송하면 연산이 중복 실행될 수 있다.

---

## 8. 관련 문서

- 내부 동작 규칙과 구현상 주의 사항: [CLAUDE.md](CLAUDE.md)
- HTTP 계층의 오류 처리: [`utils.http` 가이드](../../http/README.md)
- 외부 명령 실행과 변수 치환: [`utils.async.command` 가이드](../../async/command/README.md)
