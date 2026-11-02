# `utils.json` 사용자 가이드

`utils.json`은 Jackson으로 JSON을 다룰 때 반복되는 일을 줄이는 도구 모음이다.

- 라이브러리 표준 설정의 매퍼
- `JsonNode`에서 필드를 타입별로 꺼내는 조회 메소드
- `@type` 필드로 구체 타입을 골라 객체를 만드는 등록부
- 그 등록부를 Jackson `@JsonDeserialize`에 연결하는 어댑터

| 클래스 | 역할 |
|---|---|
| [`JacksonUtils`](JacksonUtils.java) | 공유 매퍼 `MAPPER`, 매퍼 생성 `newJsonMapper`, 필드 조회 메소드, 상수 `TYPE_FIELD`(`"@type"`) |
| [`JsonScalars`](JsonScalars.java) | JSON 스칼라를 수치·불리언 값으로 값을 잃지 않고 변환(`STRICT`/`TEXT_TOLERANT` 정책) |
| [`JacksonDeserializationException`](JacksonDeserializationException.java) | 필드 누락·타입 불일치·미등록 타입을 알리는 예외(`IOException` 계열) |
| [`JsonNodeDeserializer<T>`](JsonNodeDeserializer.java) | `JsonNode` → 객체 함수형 인터페이스 |
| [`JsonNodeSerDe<T>`](JsonNodeSerDe.java) | 양방향 변환. `JsonNodeDeserializer<T>`에 `serialize`를 더한다 |
| [`MultiTypeJsonNodeDeserializer<T>`](MultiTypeJsonNodeDeserializer.java) | 타입 이름 필드 값에 따라 등록된 deserializer를 고르는 불변 등록부 |
| [`JsonNodeStdDeserializer<T>`](JsonNodeStdDeserializer.java) | `JsonNodeDeserializer`를 Jackson `StdDeserializer`로 감싸는 어댑터 |

---

## 1. 매퍼

```java
JsonNode node = JacksonUtils.MAPPER.readTree(text);          // 공유 매퍼
JsonMapper pretty = JacksonUtils.newJsonMapper(true);        // 같은 설정 + 들여쓰기
```

두 매퍼의 설정은 같다.

- `java.time` 지원: `JavaTimeModule`을 항상 등록하고, 클래스패스의 다른 모듈도 찾아 등록한다.
- 날짜·시각(`Instant`, `LocalDateTime` 등)은 ISO-8601 문자열로 쓴다. 읽을 때는 예전 숫자 형식도 받는다.

**`MAPPER`의 설정은 바꾸지 않는다.** `configure`·`enable`·`registerModule`로 바꾸면 이 매퍼를 쓰는 JVM 안의 모든 코드가 영향을 받는다. 설정이 다른 매퍼가 필요하면 `newJsonMapper(...)`로 새로 만든 뒤 바꾼다(`rebuild()`도 된다).

---

## 2. 필드 조회

모든 조회 메소드는 같은 규칙을 따른다.

| 형태 | 필드가 없거나 JSON `null` | 타입이 맞지 않음 |
|---|---|---|
| `getXField(node, name)` | 예외 | 예외 |
| `getXFieldOrNull(node, name)` | `null` | 예외 |
| `getXFieldOrDefault(node, name, default)` | `default` | 예외 |

| `X` | 받는 값 | 반환형 (필수 / `OrNull`) |
|---|---|---|
| (없음) | 모든 노드 | `JsonNode` / `JsonNode` |
| `String` | 문자열과 숫자·불리언 같은 스칼라(텍스트로 읽음). 객체·배열은 거부 | `String` |
| `Text` | **JSON 문자열만**. 숫자·불리언도 거부한다(식별자처럼 스키마상 문자열인 필드용, 2026-11-01 추가) | `String` |
| `Boolean` | JSON 불리언, 대소문자 무관 `"true"`/`"false"` | `boolean` / `Boolean` |
| `Int` | `int` 범위의 JSON 정수. 문자열·소수는 거부 | `int` / `Integer` |
| `Long` | `long` 범위의 JSON 정수 | `long` / `Long` |
| `Double` | JSON 숫자(정수 포함). 문자열과 `double` 범위를 넘는 값(예: `1e400`)은 거부 | `double` / `Double` |
| `Array` | JSON 배열 | 수정할 수 없는 `List<JsonNode>` |

- JSON `null`은 필드가 없는 것과 똑같이 다룬다.
- 배열에는 `OrDefault` 대신 `getArrayFieldOrEmpty`(빈 목록)가 있다.
- `node` 인자 자체가 `null`이면 모든 조회 메소드가 `IllegalArgumentException`을 던진다.
- 필드를 가진 노드(부모)가 JSON 객체가 아니면(숫자·문자열·배열·JSON `null`) `OrNull`·`OrDefault`도 기본값을 돌려주지 않고 예외를 던진다. 잘못된 구조의 설정을 정상으로 받아들이지 않기 위해서다. 예외는 저수준 조회인 `getFieldOrNull`로, 부모를 검사하지 않고 `null`을 돌려준다.

```java
String url     = JacksonUtils.getStringField(jnode, "url");                 // 필수
Integer port   = JacksonUtils.getIntFieldOrNull(jnode, "port");             // 선택
boolean pooled = JacksonUtils.getBooleanFieldOrDefault(jnode, "pooled", false);
List<JsonNode> cols = JacksonUtils.getArrayFieldOrEmpty(jnode, "columns");
```

### 수치·불리언 변환: `JsonScalars`

필드 조회 메소드의 수치·불리언 변환은 [`JsonScalars`](JsonScalars.java)에 맡긴다. 필드가 아니라 JSON 노드 하나를 바로 바꿀 때도 쓴다(예: AAS 데이터 타입 변환, 장치 값 검사).

```java
int n = JsonScalars.toInt(node, JsonScalars.Policy.STRICT);
BigDecimal d = JsonScalars.toDecimal(node, JsonScalars.Policy.TEXT_TOLERANT);
```

| 정책 | 정수(`byte`·`short`·`int`·`long`·`BigInteger`) | 실수(`float`·`double`·`BigDecimal`) | 불리언 |
|---|---|---|---|
| `STRICT` | 범위 안의 JSON 정수 | 유한한 JSON 숫자 | JSON 불리언, `"true"`/`"false"` |
| `TEXT_TOLERANT` | + 소수부가 0인 실수(`3.0`), 숫자 문자열(`"42"`) | + 숫자 문자열 | + `"1"`/`"0"` |

- 두 정책 모두 값을 잃는 변환(`"abc"`, `3.9`→정수, 범위 초과, `NaN`·무한대)은 `JacksonDeserializationException`으로 거부한다. Jackson의 `asInt()` 등은 이런 값을 0이나 잘린 값으로 바꾼다.
- 숫자 문자열은 `BigDecimal` 문법만 받는다(`"1f"`, `"0x10"`, `"NaN"`은 거부).
- 메시지는 `int expected, but string`, `out of int range`처럼 맥락 없이 만들어진다. 호출자가 필드 이름이나 타입을 앞에 붙인다.
- 필드 조회 메소드는 `STRICT`를 쓴다.

---

## 3. 예외

조회 메소드와 등록부는 실패를 모두 [`JacksonDeserializationException`](JacksonDeserializationException.java)으로 알린다.

- **검사 예외다.** Jackson `JsonMappingException`을 상속하므로 `IOException`의 하위 타입이다. `throws IOException`을 선언한 메소드에서는 그대로 쓰고, `catch (IOException e)`로 함께 잡힌다.
- **Jackson이 감싸지 않는다.** Jackson deserializer 안에서 던져도 `readValue`·`treeToValue`가 이 예외를 그대로 전달한다. 반면 일반 `IOException`은 `treeToValue`가 `IllegalArgumentException`으로 감싼다. deserializer 안에서 직접 예외를 만들 때도 이 예외를 쓰는 것이 좋다.
- **메시지에 값을 넣지 않는다.** 필드 이름과, 노드에 있는 필드 이름 목록(최대 10개)만 넣는다. 설정의 비밀번호 같은 값이 로그에 남지 않게 하기 위해서다.

| 상황 | 메시지 예 |
|---|---|
| 필드 누락 | `Field not found: field=user, available fields=[url, password]` |
| 타입 불일치 | `invalid field 'port': int expected, but string` |
| 부모가 객체가 아님 | `invalid node for field 'port': object expected, but number` |
| 타입 이름 필드 누락 | `'@type' field is missing: base=Shape, available fields=[radius]` |
| 타입 이름이 문자열이 아님 | `invalid field '@type': string expected, but number` |
| 미등록 타입 | `Unregistered Shape type: triangle (registered: [circle, square])` |

2026-11-01 이전에는 이 예외가 `RuntimeException`이었고, 필드 누락만 일반 `IOException`이었다.

---

## 4. `@type` 기반 다형 역직렬화

`{"@type": "circle", "radius": 3}`처럼 타입 이름 필드로 구체 타입을 고르는 JSON은 `MultiTypeJsonNodeDeserializer`로 읽는다. 리플렉션 없이 타입 이름마다 `JsonNodeDeserializer`를 등록한다.

```java
static final MultiTypeJsonNodeDeserializer<Shape> DESERIALIZER
	= MultiTypeJsonNodeDeserializer.builder(Shape.class)     // 타입 필드 기본값: "@type"
		.register("circle", Circle::deserializeFields)       // 메소드 참조나 람다
		.register("square", Square::deserializeFields)
		.build();

Shape shape = DESERIALIZER.deserialize(jnode);
```

- 등록된 deserializer에는 타입 이름 필드를 포함한 노드 전체가 넘어간다.
- 타입 이름은 JSON 문자열만 받는다. 일반 문자열 조회와 달리 `{"@type":1}`이나 `{"@type":true}`는 거부한다.
- 타입 필드 이름은 `.typeField("kind")`로 바꿀 수 있다. `getTypeField()`로 읽을 수 있으므로, 직렬화 쪽에서도 같은 이름을 쓰게 할 수 있다.
- `build()` 결과의 등록 내용은 불변이다. 만든 뒤 빌더에 더 등록해도 바뀌지 않는다. 여러 쓰레드가 함께 쓰려면 등록된 deserializer도 쓰레드 안전해야 한다. 상태 없는 정적 메소드 참조를 등록하는 보통의 경우는 그대로 안전하다.
- 빌더는 같은 타입 이름의 중복 등록과 `null` 인자를 `IllegalArgumentException`으로, 아무것도 등록하지 않은 `build()`를 `IllegalStateException`으로 거부한다.
- 등록부 자체도 `JsonNodeDeserializer`다. 그래서 다른 등록부에 하나의 deserializer로 넣어 중첩할 수 있다(예: 바깥은 `family`, 안쪽은 `@type`으로 고름).
- `getTypeNames()`가 돌려주는 집합과 오류 메시지의 등록 목록은 순서가 정해져 있지 않다.

양방향 변환이 필요하면 `JsonNodeSerDe<T>`를 구현한다. `deserialize`는 `JsonNodeDeserializer`에서 물려받으므로, `JsonNodeSerDe` 객체는 그대로 등록부에 등록할 수 있다.

`JsonNodeDeserializer`·`JsonNodeSerDe`를 직접 구현할 때의 `null` 계약은 다음과 같다.

| 경우 | 계약 |
|---|---|
| Java `null` 인자(`deserialize`, `serialize` 모두) | 호출자는 넘기지 않는다. 구현은 거부한다(`IllegalArgumentException` 권장, `IOException` 허용) |
| JSON `null`(`NullNode`)을 `deserialize`에 넘김 | 받아들일지는 구현이 정하고 문서에 적는다(거부 권장). 등록부는 타입 이름 누락으로 거부하고, Jackson 어댑터 경로에서는 오지 않는다 |
| 반환값 | Java `null`을 돌려주지 않는다. `serialize`는 JSON `null`이 필요하면 `NullNode`를 돌려준다. 단, JSON `null`을 받아들이는 `deserialize` 구현은 그 입력에 `null`을 돌려줄 수 있다 |

---

## 5. Jackson 어노테이션과 연결

모듈에 등록할 때는 `JsonNodeStdDeserializer`를 직접 만들어 넘긴다.

```java
mapper = JacksonUtils.newJsonMapper(false).rebuild()
		.addModule(new SimpleModule().addDeserializer(Shape.class,
				new JsonNodeStdDeserializer<>(Shape.class, DESERIALIZER)))
		.build();
```

`@JsonDeserialize(using = X.class)`로 쓸 때는 하위 클래스를 만든다. Jackson은 인자 없는 생성자로 `X`를 만들기 때문에, 대상 타입과 등록부는 하위 클래스의 생성자에서 넘긴다.

```java
@JsonDeserialize(using = Shapes.Deserializer.class)
public interface Shape { … }

public class Shapes {
	@SuppressWarnings("serial")
	public static class Deserializer extends JsonNodeStdDeserializer<Shape> {
		public Deserializer() {
			super(Shape.class, DESERIALIZER);
		}
	}
}
```

- 어댑터는 입력을 현재 역직렬화 컨텍스트(`ctxt.readTree`)로 `JsonNode`로 읽은 뒤 등록부에 넘긴다. 그래서 `FAIL_ON_TRAILING_TOKENS`를 켠 매퍼에서도 배열 원소나 객체 필드 안에서 정상 동작한다. 등록부의 예외는 3장대로 그대로 전달된다.
- JSON `null`은 Jackson이 처리하므로 등록부까지 오지 않고 `null`이 된다.
- Jackson을 거치지 않고 이미 가진 `JsonNode`를 읽을 때는 등록부의 `deserialize`를 바로 부른다. `treeToValue`로 돌아가면 같은 일을 하면서 노드를 한 번 더 풀었다 읽는다.
