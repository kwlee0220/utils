# `utils.http` 사용자 가이드

OkHttp 기반의 동기 RESTful 클라이언트. 요청 전송, 응답 본문 역직렬화, 서버 에러 응답의 예외 변환을 한 곳에 묶어 제공한다.

이 가이드는 [`HttpRESTfulClient`](../src/main/java/utils/http/HttpRESTfulClient.java)를 중심으로 패키지 사용법을 설명한다. 이 패키지 위에 [`utils.rpc.restful`](../src/main/java/utils/rpc/restful/)의 RPC 계층이 올라가 있고, 다운스트림 MDT 프로젝트 44개 파일이 이 패키지를 사용한다.

---

## 1. 개요

| 갈래 | 타입 | 요약 |
|---|---|---|
| **클라이언트** | [`HttpRESTfulClient`](../src/main/java/utils/http/HttpRESTfulClient.java), [`HttpClientProxy`](../src/main/java/utils/http/HttpClientProxy.java), [`OkHttpClientUtils`](../src/main/java/utils/http/OkHttpClientUtils.java) | 요청 전송과 OkHttp 생성 |
| **역직렬화** | `ResponseBodyDeserializer<T>`, `ErrorEntityDeserializer` (중첩 인터페이스), [`JacksonResponseBodyDeserializer`](../src/main/java/utils/http/JacksonResponseBodyDeserializer.java), [`JacksonErrorEntityDeserializer`](../src/main/java/utils/http/JacksonErrorEntityDeserializer.java) | 본문/에러 파싱 전략 주입 |
| **에러 포맷 DTO** | [`RESTfulErrorEntity`](../src/main/java/utils/http/RESTfulErrorEntity.java), [`SpringBootErrorResponse`](../src/main/java/utils/http/SpringBootErrorResponse.java), [`TypedServerErrorMessage`](../src/main/java/utils/http/TypedServerErrorMessage.java) | 세 가지 서버 에러 응답 포맷 |
| **예외** | [`RESTfulException`](../src/main/java/utils/http/RESTfulException.java) ← [`RESTfulRemoteException`](../src/main/java/utils/http/RESTfulRemoteException.java) / [`RESTfulIOException`](../src/main/java/utils/http/RESTfulIOException.java) | 모두 unchecked |

모든 호출은 **동기**이며, 실패는 checked 예외 대신 unchecked 예외로 전파된다.

---

## 2. 빠른 시작

```java
import utils.http.HttpRESTfulClient;

HttpRESTfulClient client = HttpRESTfulClient.builder()
                                            .header("Authorization", "Bearer " + token)
                                            .build();

// 문자열 그대로 받기
String body = client.get("https://api.example.com/v1/items", HttpRESTfulClient.STRING_DESER);

// 객체로 역직렬화
JsonMapper mapper = JacksonUtils.MAPPER;        // 또는 JacksonUtils.newJsonMapper(false)
Item item = client.get(url, new JacksonResponseBodyDeserializer<>(mapper, Item.class));

// 본문이 필요 없는 호출
client.delete(url);
```

`HttpRESTfulClient.newDefaultClient()`는 `builder().build()`와 같다.

---

## 3. 클라이언트 구성

### 3.1 빌더

```java
HttpRESTfulClient client = HttpRESTfulClient.builder()
        .httpClient(myOkHttpClient)              // 미지정 시 OkHttpClientUtils.newClient()
        .header("Authorization", "Bearer ...")   // 모든 요청에 자동 추가. 같은 이름은 덮어씀
        .header("X-Tenant", "acme")
        .jsonMapper(myMapper)                    // 미지정 시 new JsonMapper()
        .errorEntityDeserializer(myDeser)        // 미지정 시 JacksonErrorEntityDeserializer
        .build();
```

같은 빌더로 `build()`를 여러 번 호출해도 안전하며, 호출할 때마다 default 컴포넌트는 새로 생성된다.

### 3.2 OkHttp 클라이언트 생성

```java
OkHttpClient plain = OkHttpClientUtils.newClient();

// TLS 검증을 끈 클라이언트
OkHttpClient insecure = OkHttpClientUtils.newTrustAllOkHttpClient();
OkHttpClient.Builder b = OkHttpClientUtils.newTrustAllOkHttpClientBuilder();   // 추가 설정용
```

> ⚠️ `newTrustAllOkHttpClient*`은 인증서 검증과 호스트명 검증을 모두 비활성화하므로 **중간자 공격(MITM)에 취약**하다. 테스트나 신뢰된 내부망에서만 사용한다.

타임아웃·커넥션 풀·인터셉터 등은 `OkHttpClient.Builder`로 직접 구성해 `httpClient(...)`로 주입한다.

### 3.3 `HttpClientProxy`

OkHttp 클라이언트와 기본 엔드포인트를 한 쌍으로 노출하는 인터페이스(`getHttpClient()`, `getEndpoint()`). 여러 컴포넌트가 같은 연결 설정과 엔드포인트를 공유해야 할 때 구현한다.

---

## 4. 요청 메소드

| 메소드 | 반환 | 비고 |
|---|---|---|
| `get(url, deser)` / `get(HttpUrl, deser)` | `T` | |
| `get(url)` | `String` | 본문을 그대로 반환 |
| `post(url, RequestBody, deser)` / `post(url, RequestBody)` | `T` / `String` | |
| `put(url, RequestBody, deser)` / `put(url, RequestBody)` | `T` / `String` | |
| `patch(url, RequestBody, deser)` / `patch(url, RequestBody)` | `T` / `String` | |
| `delete(url, deser)` / `delete(url)` | `T` / `String` | |
| `head(url)` | `Headers` | |
| `callAndGetHeaders(Request, deser)` | `Tuple<Headers,T>` | 직접 만든 `Request` 실행 |

**본문을 가질 수 없는 응답** — HEAD 요청의 응답과 204(No Content)/205(Reset Content)/304(Not Modified) — 은 본문을 읽지 않고 `null`로 처리한다. `deser`도 호출되지 않는다. 204/205는 성공이므로 `null`이 반환되고, 304는 성공 응답이 아니므로 에러 처리 경로를 탄다.

본문 미디어 타입 상수: `HttpRESTfulClient.MEDIA_TYPE_JSON` (`application/json; charset=utf-8`), `MEDIA_TYPE_TEXT` (`text/plain; charset=utf-8`). 문자열로부터 `RequestBody`를 만드는 정적 헬퍼도 있다 — `newJsonRequestBody(String)`, `newTextRequestBody(String)`.

```java
// JSON 본문을 명시적으로 지정
RequestBody body = RequestBody.create(json, HttpRESTfulClient.MEDIA_TYPE_JSON);
Item created = client.post(url, body, itemDeser);

// 응답 헤더까지 필요한 경우
Request req = new Request.Builder().url(url).get().build();
Tuple<Headers,Item> result = client.callAndGetHeaders(req, itemDeser);
String etag = result._1.get("ETag");
Item value = result._2;
```

---

## 5. 역직렬화 전략

### 5.1 응답 본문 — `ResponseBodyDeserializer<T>`

```java
@FunctionalInterface
public interface ResponseBodyDeserializer<T> {
    T deserialize(Headers headers, String respBody) throws IOException;
}
```

제공되는 구현:

| 구현 | 용도 |
|---|---|
| `HttpRESTfulClient.STRING_DESER` | 본문을 문자열 그대로 반환 |
| `HttpRESTfulClient.JSON_DESER` | 본문을 `JsonNode`로 파싱. **전용 매퍼 사용** (아래 주의) |
| `client.jsonDeserializer()` | 본문을 `JsonNode`로 파싱. 이 클라이언트에 설정된 매퍼 사용 |
| `JacksonResponseBodyDeserializer<>(mapper, Item.class)` | Jackson으로 지정 타입 역직렬화 |
| 람다 | 헤더까지 참조해 커스텀 파싱 |

> `JSON_DESER`는 static 상수라 자체 `JsonMapper` 인스턴스를 갖는다. 따라서 `builder().jsonMapper(...)`로 지정한 매퍼는 **반영되지 않는다.** 모듈 등록이나 파싱 기능 설정을 응답 파싱에도 적용하려면 인스턴스 메소드 `client.jsonDeserializer()`를 쓴다.

```java
// Content-Type에 따라 분기하는 커스텀 deserializer
ResponseBodyDeserializer<Payload> deser = (headers, body) -> {
    String ct = headers.get("Content-Type");
    return ct != null && ct.startsWith("application/json")
                ? mapper.readValue(body, Payload.class)
                : Payload.ofText(body);
};
```

`deser`는 `null`일 수 없다 (`IllegalArgumentException`). 본문이 없는 응답(204/205, HEAD)에서는 `deser`를 아예 호출하지 않고 `null`을 반환한다.

`deser`가 던지는 예외는 checked(`IOException`)든 unchecked든 모두 `RESTfulIOException`으로 감싸여 전파된다. 단, 이미 `RESTfulException` 하위 타입이면 그대로 통과시켜 이중으로 감싸지 않는다.

### 5.2 에러 본문 — `ErrorEntityDeserializer`

```java
@FunctionalInterface
public interface ErrorEntityDeserializer {
    RESTfulErrorEntity deserialize(String respBody) throws IOException;
}
```

기본값은 `JacksonErrorEntityDeserializer`이며, `{code, message}` 형태의 본문을 `RESTfulErrorEntity`로 읽는다. 서버가 다른 필드명을 쓰면 이 인터페이스를 구현해 `errorEntityDeserializer(...)`로 주입한다.

---

## 6. 에러 처리

### 6.1 예외 계층

```
RuntimeException
 └─ RESTfulException                (공통 부모 — 구분 없이 처리할 때 catch)
     ├─ RESTfulRemoteException      (서버가 구조화된 에러를 반환)
     └─ RESTfulIOException          (네트워크 실패 / 응답 파싱 실패 / 인식 불가 에러 포맷)
```

```java
try {
    return client.get(url, deser);
}
catch ( RESTfulRemoteException e ) {
    RESTfulErrorEntity entity = e.getRemoteErrorEntity();   // 엔티티 없이 생성된 경우 null
    ...
}
catch ( RESTfulIOException e ) {
    ...
}

// 또는 구분 없이
catch ( RESTfulException e ) { ... }
```

`RESTfulException`은 직접 던져지지 않는다 — catch 지점을 하나로 묶기 위한 부모다.

### HTTP 상태 코드

`RESTfulException.getHttpStatusCode()`는 이 오류를 발생시킨 에러 응답의 HTTP 상태 코드를 돌려준다. 비-2xx 응답을 예외로 바꾸는 시점에 채워지며, HTTP 응답에서 비롯되지 않은 오류(연결 실패, 타임아웃, 본문 파싱 실패)는 `RESTfulException.UNKNOWN_STATUS_CODE`(`-1`)이다.

**상태 코드 자체가 프로토콜상 의미를 갖는 경우 이 값으로 분기한다.** 예외 *타입*은 판별 근거가 되지 못한다 — 아래 에러 포맷 분기표에서 보듯 같은 404라도 본문 모양에 따라 `RESTfulRemoteException`이 되기도 하고 `RESTfulIOException`이 되기도 하기 때문이다.

```java
try {
    return m_restClient.delete(sessionUrl, deser);
}
catch ( RESTfulException e ) {
    if ( e.getHttpStatusCode() == HttpURLConnection.HTTP_CONFLICT ) {
        return false;      // 이미 종료되어 취소할 수 없음
    }
    throw e;               // 404(세션 없음) 등은 그대로 전파
}
```

### 6.2 네트워크 단계의 실패

`OkHttpClient.newCall(...).execute()` 단계에서 발생하는 예외는 모두 `RESTfulIOException`으로 변환된다.

| 원인 | 처리 |
|---|---|
| `ConnectException` | `"Failed to connect to the server: endpoint=..."` |
| `SocketTimeoutException` (connect/read/write 타임아웃) | `"RESTful call timed out: ..."` |
| `InterruptedIOException` (스레드가 실제로 인터럽트된 경우) | `"RESTful call interrupted: ..."` |
| `InterruptedIOException` (그 외 — call timeout) | `"RESTful call timed out: ..."` |
| 그 외 `IOException` | `"Failed to call"` |

`SocketTimeoutException`은 연결 타임아웃뿐 아니라 read/write 타임아웃도 포함하므로 "연결 실패"로 단정하지 않는다.

OkHttp는 `callTimeout` 만료를 `InterruptedIOException("timeout")`으로 보고하기 때문에 예외 타입만으로는 인터럽트와 타임아웃을 구분할 수 없다. 그래서 `Thread.currentThread().isInterrupted()`로 갈라낸다. OkHttp는 blocking socket I/O 중의 `Thread.interrupt()`에 반응하지 않으므로, 진짜 인터럽트된 스레드는 이 시점에도 플래그가 살아 있다. 즉 플래그는 **읽기만 하고 세우지 않는다**.

`catch` 절의 순서에 의미가 있다. `SocketTimeoutException extends InterruptedIOException`이므로 순서를 뒤집으면 모든 소켓 타임아웃이 인터럽트 판정 분기로 흡수된다.

### 6.3 HTTP 에러 응답의 분기

응답이 `!isSuccessful()`이면 본문을 한 번 `JsonNode`로 파싱한 뒤, **존재하는 필드로 포맷을 판별**한다 ([`toRESTfulClientException`](../src/main/java/utils/http/HttpRESTfulClient.java)).

| 판별 조건 | 해석 포맷 | 결과 예외 |
|---|---|---|
| `messageType` 필드 존재 | `TypedServerErrorMessage` (`{messageType, code, text, timestamp}`) | `TypedServerErrorMessage.toException()`의 결과를 unchecked로 감싼 것 — 보통 `RESTfulRemoteException` |
| `status` + `path` 존재 | `SpringBootErrorResponse` (`{timestamp, status, error, message, path}`) | **`RESTfulIOException`** (remote 예외가 아니다) |
| `code` 또는 `message`가 non-null | `RESTfulErrorEntity` (`{code, message}`) | `RESTfulErrorEntity.toRemoteException()`의 결과 (`RESTfulRemoteException`) |
| 본문이 비어 있음 | — | `"Empty RESTful error response"` `RESTfulIOException` |
| JSON이 아님 | — | 본문을 메시지에 담은 `RESTfulIOException` |
| 포맷은 맞지만 필드 값 타입이 안 맞음 | — | `"Failed to parse RESTful error response: ..."` `RESTfulIOException` |
| 위 어디에도 해당 없음 | — | `"Unrecognized RESTful error response: ..."` `RESTfulIOException` |

`toRESTfulClientException`은 **어떤 경우에도 예외를 던지지 않고 반환한다.** `JsonMapper.convertValue()`가 변환 실패 시 unchecked `IllegalArgumentException`을 던지므로(`messageType`의 enum 값 불일치, `SpringBootErrorResponse.status`에 문자열이 온 경우 등) 분기 전체를 감싸 걸러낸다. 서버 응답이 어떻게 망가져 있든 결과는 `RESTfulException`의 하위 타입이다.

판별은 위 순서대로 이루어진다. 즉 `messageType`이 있으면 `code`/`message`가 함께 있어도 첫 번째 분기로 처리된다. 본문이 비었는지는 가장 먼저 검사한다.

#### 중첩 `error` 객체의 벗겨내기

표를 적용하기 **전에**, 본문이 `{"error": {...}}` 형태로 한 겹 감싸져 있으면 그 안쪽을 root로 삼는다.
에러 본문을 감싸 보내는 서버를 지원하기 위한 처리다.

이때 `error`가 **객체인 경우에만** 벗긴다는 조건이 핵심이다. Spring Boot 기본 포맷의 `error`는 객체가
아니라 reason phrase **문자열**(`"error": "Internal Server Error"`)이어서, 조건 없이 벗기면 root가
`TextNode`가 되고 표의 모든 분기가 빗나가 `"Unrecognized ..."`로 떨어진다. 즉 `SpringBootErrorResponse`
분기가 통째로 죽는다.

이 회귀는 **본문을 그대로 echo하는 메시지 때문에 substring 단언으로는 잡히지 않는다.** `"Unrecognized
RESTful error response: response={원문}"` 안에 `"Internal Server Error"`도 `path` 값도 그대로 들어 있기
때문이다. 테스트는 `SpringBootErrorResponse.toString()` 고유 형식(`"Internal Server Error (500): boom,
path=/api/x"`)으로 검증해야 한다 (`HttpRESTfulClientTest.springBootError_with_status_and_path_becomes_io_exception`,
`nestedErrorObject_is_unwrapped`).

`RESTfulIOException`으로 끝나는 분기의 메시지에는 `[<상태코드> <URL>]` 형태의 HTTP 컨텍스트가 앞에 붙는다.

```
[404 http://localhost:8080/svc/items] Empty RESTful error response
```

본문이 없는 404/500은 이 접두어가 없으면 상태 코드도 URL도 남지 않아 진단이 불가능하다. 반대로 `RESTfulRemoteException` 분기에는 접두어를 붙이지 않는다. 서버가 이미 원인을 설명했고, 원본은 `getRemoteErrorEntity()`로 그대로 꺼낼 수 있기 때문이다.

### 6.4 `RESTfulErrorEntity`의 두 가지 변환

`code`에는 보통 서버 예외의 FQCN이 담긴다. 이를 다루는 방법이 두 가지이고, **안전성이 서로 다르다.**

| 메소드 | 동작 | 클래스 로딩 |
|---|---|---|
| `toRemoteException()` | `code`/`message`를 보유한 `RESTfulRemoteException` 반환 | 하지 않음 (안전) |
| `toClientException()` | `code`가 가리키는 클래스를 로드해 원래 예외 타입을 복원 | **함** |

`toClientException()`은 `message`가 있으면 `(String)` 생성자를, 없으면 no-arg 생성자를 호출한다. 복원에 실패하면 반환이 아니라 예외를 던진다.

> **`HttpRESTfulClient`의 기본 경로는 `toRemoteException()`을 사용한다.** 즉 `{code, message}` 에러 응답을 받아도 클라이언트는 `code`가 지정한 클래스를 로드하지 않는다. `toClientException()`은 원래 예외 타입이 반드시 필요한 호출자가 직접 쓰는 용도이며, 신뢰할 수 있는 서버를 상대할 때만 사용한다.
>
> 참고로 `toClientException()`은 클라이언트 기본 경로에 쓸 수 없다. 복원 실패 시 반환이 아니라 **예외를 던지므로** "예외를 만들어 반환한다"는 `toRESTfulClientException`의 계약을 깨고, 또한 `code`가 `RuntimeException` 하위가 아니면(예: `{"code":"java.io.IOException"}`) `ClassCastException`으로 터진다.

`TypedServerErrorMessage.toException()`은 반대로 클래스를 로드하지 않고 항상 `RESTfulRemoteException`을 반환한다.

**서버 예외를 타입으로 잡아야 하면 허용 목록으로 등록한다.** 클래스를 로딩하지 않고도 알려진 `code`만 원래
예외로 복원할 수 있다. 등록한 `code`의 에러 응답은 `RESTfulRemoteException` 대신 지정한 예외로 던져지며,
`{code, message}` 포맷과 `{messageType, code, text}` 포맷에 모두 적용된다. 생성 함수는 에러 메시지
(`message` 또는 `text`)를 받는다.

```java
HttpRESTfulClient client = HttpRESTfulClient.builder()
        .remoteException("mdt.model.ResourceNotFoundException", ResourceNotFoundException::new)
        .remoteException("mdt.model.ResourceAlreadyExistsException", ResourceAlreadyExistsException::new)
        .build();

try {
    client.post(url, body, deser);
}
catch ( ResourceAlreadyExistsException e ) {      // 등록하지 않았다면 RESTfulRemoteException 이다
    ...
}
```

등록하지 않은 `code`는 기존처럼 `RESTfulRemoteException`이다. 생성 함수가 실패하거나 `null`을 반환해도
`RESTfulRemoteException`으로 돌아가며, 등록한 예외가 `RESTfulException` 하위가 아니면 HTTP 상태 코드
(`getHttpStatusCode()`)는 실리지 않는다.

### 6.5 서버 측에서 에러 엔티티 만들기

```java
RESTfulErrorEntity.of(exception);                    // code=예외 FQCN, message=예외 메시지
RESTfulErrorEntity.of("작업 실패", cause);            // message에 원인 메시지를 합침
RESTfulErrorEntity.ofMessage("잘못된 요청");           // code 없이 메시지만

TypedServerErrorMessage.from(exception);             // messageType=Exception, code=FQCN, text, timestamp
new TypedServerErrorMessage("E1001", "재고 부족");
```

---

## 7. 함정 (gotchas)

### 7.1 문자열 본문은 미디어 타입을 명시해서 보낸다

**본문을 받는 메소드는 모두 `RequestBody`를 받는다.** 문자열을 직접 받는 오버로드는 하나도 없다.

문자열을 받아 `text/plain`으로 조용히 전송하던 `post(url, String, deser)` / `put(url, String, deser)`는 제거되었다. JSON 문자열을 넘기면 `Content-Type: text/plain; charset=utf-8`으로 나가 서버가 415를 반환하는 함정이었다. 그 대안으로 `postJson`/`putJson`을 두었다가 이것도 제거했다 — **어떤 미디어 타입을 고르든, 문자열 파라미터가 미디어 타입을 대신 정해주는 형태 자체가 문제**이기 때문이다. 미디어 타입은 항상 호출부에 드러나야 한다.

```java
// JSON 문자열
RequestBody body = HttpRESTfulClient.newJsonRequestBody(mapper.writeValueAsString(item));
client.post(url, body, deser);

// text/plain 본문
client.post(url, HttpRESTfulClient.newTextRequestBody(text), deser);

// 그 외 미디어 타입은 직접 구성
RequestBody csvBody = RequestBody.create(csv, MediaType.get("text/csv; charset=utf-8"));
client.post(url, csvBody, deser);
```

`String`은 `RequestBody`로 암묵 변환되지 않으므로, 제거된 오버로드를 쓰던 코드는 조용히 동작이 바뀌지 않고 컴파일 에러로 드러난다.

### 7.2 Spring Boot 에러는 `RESTfulIOException`이 된다

Spring Boot 기본 에러 포맷(`{status, path, ...}`)으로 응답하는 서버라면, 404·500 같은 **명백한 서버 측 에러도 `RESTfulRemoteException`이 아니라 `RESTfulIOException`으로 전달된다.** 예외 타입으로 "네트워크 문제 vs. 서버 거부"를 구분하려던 코드는 이 경우 오작동한다. 두 경우를 함께 잡으려면 `RESTfulException`을 catch한다.

### 7.3 기본 헤더 적용 규칙이 경로마다 다르다

| 경로 | 동작 |
|---|---|
| `get`/`post`/`put`/`patch`/`delete`/`head` (URL 기반) | 기본 헤더를 **무조건 추가**한다 |
| `callAndGetHeaders(Request, deser)` | 요청에 같은 이름의 헤더가 **없을 때만** 추가한다 (호출자 헤더 우선) |

### 7.4 `toClientException()`은 직접 호출할 때만 클래스를 로드한다

[6.4절](#64-restfulerrorentity의-두-가지-변환) 참조. 클라이언트 기본 경로는 `toRemoteException()`을 쓰므로 클래스 로딩이 없다. `toClientException()`을 직접 호출한다면 **신뢰할 수 있는 서버의 응답에 대해서만** 사용하고, 복원 실패 시 반환이 아니라 예외가 던져진다는 점을 감안한다.

### 7.5 인터럽트·타임아웃·연결 실패의 예외 타입이 모두 같다

전부 `RESTfulIOException`이다 ([6.2절](#62-네트워크-단계의-실패) 참조). 예외 타입으로는 구분되지 않고 메시지("interrupted" / "timed out" / "Failed to connect")만 다르므로, 취소를 처리해야 한다면 `Thread.interrupted()`를 직접 확인한다. 호출자가 `RESTfulIOException`을 광범위하게 삼키면 **취소 신호가 유실**된다.

### 7.6 응답 본문은 항상 전부 메모리로 읽힌다

`parseResponse`가 `response.body().string()`으로 전체를 문자열화한다. 대용량 다운로드에는 이 클라이언트 대신 `getHttpClient()`로 얻은 OkHttp 클라이언트를 직접 사용해 스트리밍한다.

---

## 8. 테스트

HTTP 테스트는 OkHttp의 `mockwebserver`를 사용한다(테스트 classpath에 포함).

```java
MockWebServer server = new MockWebServer();
server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"name\":\"a\"}"));
server.start();

HttpRESTfulClient client = HttpRESTfulClient.newDefaultClient();
Item item = client.get(server.url("/items/1").toString(), itemDeser);

RecordedRequest req = server.takeRequest();
assertEquals("GET", req.getMethod());
server.shutdown();
```

기존 테스트: `HttpRESTfulClientTest`, `HttpRESTfulClientCallTest`, `OkHttpClientUtilsTest`, `RESTfulErrorEntityTest`, `SpringBootErrorResponseTest`, `TypedServerErrorMessageTest`.

---

## 9. API 요약

| 타입 | 역할 |
|---|---|
| [`HttpRESTfulClient`](../src/main/java/utils/http/HttpRESTfulClient.java) | 동기 REST 클라이언트. `Builder`, `ResponseBodyDeserializer`, `ErrorEntityDeserializer`, `STRING_DESER`, `MEDIA_TYPE_*` 포함 |
| [`OkHttpClientUtils`](../src/main/java/utils/http/OkHttpClientUtils.java) | `newClient()`, `newTrustAllOkHttpClient()`, `newTrustAllOkHttpClientBuilder()` |
| [`HttpClientProxy`](../src/main/java/utils/http/HttpClientProxy.java) | 클라이언트 + 엔드포인트 쌍 인터페이스 |
| [`JacksonResponseBodyDeserializer`](../src/main/java/utils/http/JacksonResponseBodyDeserializer.java) | Jackson 기반 본문 역직렬화 |
| [`JacksonErrorEntityDeserializer`](../src/main/java/utils/http/JacksonErrorEntityDeserializer.java) | Jackson 기반 에러 엔티티 역직렬화 (기본값) |
| [`RESTfulErrorEntity`](../src/main/java/utils/http/RESTfulErrorEntity.java) | `{code, message}` 포맷. `of`/`ofMessage` 팩토리, `toRemoteException`/`toClientException` |
| [`SpringBootErrorResponse`](../src/main/java/utils/http/SpringBootErrorResponse.java) | Spring Boot 기본 에러 포맷 DTO |
| [`TypedServerErrorMessage`](../src/main/java/utils/http/TypedServerErrorMessage.java) | `{messageType, code, text, timestamp}` 커스텀 포맷. `MessageTypeEnum` = Info/Warning/Error/Exception |
| [`RESTfulException`](../src/main/java/utils/http/RESTfulException.java) | 공통 부모 unchecked 예외 |
| [`RESTfulRemoteException`](../src/main/java/utils/http/RESTfulRemoteException.java) | 서버 구조화 에러. `getRemoteErrorEntity()` |
| [`RESTfulIOException`](../src/main/java/utils/http/RESTfulIOException.java) | 네트워크/파싱 실패 |

---

## 10. 관련 문서

* 패키지 개발 노트: [src/main/java/utils/http/CLAUDE.md](../src/main/java/utils/http/CLAUDE.md)
* 이 패키지 위의 RPC 계층: 루트 [CLAUDE.md](../CLAUDE.md)의 `utils.rpc.restful` 항목
* JSON 헬퍼: `utils.json` (`JacksonUtils`, `JacksonSerializable(s)`)
* 라이브러리 전체 구조: [CLAUDE.md](../CLAUDE.md)
