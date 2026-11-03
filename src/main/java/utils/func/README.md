# `utils.func` 사용자 가이드

`utils.func`는 값의 유무, 연산의 성공·실패, 지연 적재, 검사 예외를 던지는 람다를 다루는 함수형 도구 모음이다. 거의 모든 다른 패키지(`utils.stream`, `utils.async` 등)가 이 패키지를 쓴다.

1장에서 상황별로 쓸 타입을 고르고, 2~5장에서 각 타입을 설명한다. 6장은 검사 예외를 던지는 람다를 다루는 방법, 7장은 이름 규칙과 예외 규칙, 8장은 그 밖의 도우미다. 예제의 `Item`, `loadConfig()` 등은 설명을 위한 이름이다.

---

## 1. 어떤 타입을 사용할까

| 필요한 것 | 사용할 타입 | 선택 기준 |
|---|---|---|
| 값이 있을 수도 없을 수도 있음 | [`FOption`](FOption.java) | `Optional`과 달리 `null` 값도 "있음"으로 담을 수 있다. `FStream`으로 순회 가능 |
| `null`일 수 있는 값을 감싸지 않고 바로 처리 | [`Nullables`](Nullables.java) | `Nullables.orElse(x, y)`처럼 static 도우미. `java.util.Optional` 도우미도 여기에 있다 |
| 성공 또는 예외 | [`Try`](Try.java) | 검사 예외를 던지는 작업의 결과를 값으로 다룬다 |
| 성공·실패·취소 | [`Result`](Result.java) | 비동기 실행(`Execution`)의 결과. 취소 상태가 따로 있다 |
| 둘 중 하나의 값 | [`Either`](Either.java) | Left/Right 중 정확히 하나를 가진다 |
| 처음 쓸 때 계산하고 재사용 | [`Lazy`](Lazy.java) | thread-safe. 인터페이스를 감싼 지연 프록시(`wrap`)도 만든다 |
| 일정 시간이 지나면 다시 계산 | [`TimedLazy`](TimedLazy.java) | TTL이 지나면 다음 `get()`에서 다시 적재 |
| 한 스레드에서만 쓰는 지연 값 | [`UnsafeLazy`](UnsafeLazy.java) | `Lazy`와 API가 같고 동기화만 하지 않는다 |
| 검사 예외를 던지는 람다 | `Checked*`, `Checked*X` | [6장](#6-검사-예외를-던지는-람다) |

---

## 2. `FOption`과 `Nullables` — 값의 유무

### 2.1 `FOption`

```java
FOption<Item> item = FOption.ofNullable(findItem(id));   // null이면 빈 값
String name = item.map(Item::name).getOrElse("unknown");
item.ifPresent(this::register)
    .ifAbsent(() -> log.warn("not found: {}", id));
```

- `FOption.of(null)`은 **`null` 값을 가진 존재 상태**다(`isPresent()`가 `true`, `get()`이 `null`). 값이 없음은 `FOption.empty()`로만 표현한다. `toOptional()`은 이 차이를 잃는다(`null` 값이면 `Optional.empty()`).
- 값 꺼내기
  - `get()`: 없으면 `NoSuchValueException`(`NoSuchElementException`의 하위 타입).
  - `getOrNull()`, `getOrElse(T)`, `getOrElse(Supplier)`.
  - `getOrThrow(Supplier<X>)`: 없으면 그 예외를 던진다.
  - `getOrElseThrow(CheckedSupplierX)`: 없으면 검사 예외를 던질 수 있는 함수로 대체값을 만든다.
  - `getOrElse(null)`은 오버로드가 모호해 컴파일되지 않는다. 기본값이 `null`이면 `getOrNull()`을 쓴다.
- 대체: `or(FOption)`, `or(Supplier<FOption>)`은 `FOption`을, `orElse(T)`는 그 값을 감싼 `FOption`을 돌려준다.
- 변환: `map`(결과가 `null`이면 `null` 값을 가진 존재 상태), `mapNotNull`(결과가 `null`이면 빈 값), `flatMap`, `filter`, `fold(초기값, 함수)`, `cast(Class)`.
- `equals`·`hashCode`는 값으로 비교하므로 `HashMap`의 키로 쓸 수 있다.

### 2.2 `Nullables`

`FOption`으로 감싸지 않고 `null`일 수 있는 값을 바로 다루는 static 도우미다. 이름은 JDK를 따른다.

| 메소드 | 하는 일 |
|---|---|
| `orElse(value, elseValue)` | `value`가 `null`이면 `elseValue`(`null`이어도 됨) |
| `orElseGet(value, supplier)` | `value`가 `null`일 때만 `supplier` 호출 |
| `getOrElseThrow(value, checkedSupplier)` | `orElseGet`과 같고, `supplier`가 검사 예외를 던질 수 있다 |
| `ifNotNull(value, consumer)` / `ifNotNullOrThrow(value, checkedConsumer)` | `null`이 아니면 그 값으로 호출 |
| `ifNotNullRun(value, runnable)` | `null`이 아니면 실행(값은 넘기지 않음) |
| `ifNull(value, runnable)` | `null`이면 실행 |
| `map(value, f)` / `mapOrElse(value, f, elseValue)` / `mapOrElseGet(value, f, supplier)` | `null`이 아니면 `f` 적용 |

```java
m_logger = Nullables.orElse(logger, s_defaultLogger);
m_dir = Nullables.orElseGet(dir, FileUtils::getCurrentWorkingDirectory);
Nullables.ifNotNull(m_codecName, upload::compressionCodecName);
```

`ifNotNullRun`을 `ifNotNull`의 오버로드로 두지 않은 것은 getter·setter가 같은 이름인 메소드 참조(위의 `upload::compressionCodecName`)가 `Runnable`과 `Consumer` 양쪽에 맞아 모호해지기 때문이다.

`java.util.Optional`을 다루는 도우미 `orElse(Optional, Supplier)`, `doIfPresent`, `mapOrThrow`, `whenTrue`, `transform`도 이 클래스에 있다.

---

## 3. `Try`와 `Result` — 성공과 실패

### 3.1 `Try`

```java
Try<Config> conf = Try.of(() -> loadConfig(path));        // 검사 예외도 잡는다
int port = conf.map(Config::port).getOrElse(8080);
Try.run(() -> Files.delete(tmp)).ifFailed(e -> log.warn("delete failed", e));
```

- 만들기: `Try.of(CheckedSupplier)`, `Try.run(CheckedRunnable)`, `Try.accept(input, CheckedConsumer)`, `Try.success(v)`, `Try.failure(e)`.
- 함수로 끌어올리기: `liftSupplier`, `liftRunnable`, `liftFunction`(각각 `Try`를 돌려주는 함수로 바꾼다).
- `map`·`flatMap`은 mapper가 던진 `Exception`을 `Failure`로 잡는다(`Error`는 그대로 전파).
- 꺼내기: `get()`은 실패 원인을 감싸지 않고 그대로(검사 예외도) 던진다. `getUnchecked()`는 검사 예외만 `RuntimeException`으로 감싸 던진다.
- 복구: `recover(Function<Throwable,T>)`, `recover(CheckedSupplier)`. 실패 원인 바꾸기: `mapFailure`.
- `equals`: `Success`는 값으로, `Failure`는 원인(같은 예외 객체)으로 비교한다.

### 3.2 `Result`

`Execution`의 종료 결과다. 성공(`success`), 실패(`failure`), 취소(`cancelled`) 세 상태를 가진다.

```java
exec.whenFinished(r -> r.ifSuccessful(this::use)
                        .ifFailed(e -> log.error("failed", e))
                        .ifCancelled(() -> log.info("cancelled")));
```

- `get()`: 실패면 `ExecutionException`(원인 포함), 취소면 `CancellationException`.
- `getUnchecked()`: 실패 원인을 `ExecutionException`으로 감싸지 않고 바로 던진다(검사 예외만 `RuntimeException`으로). 취소면 `CancellationException`.
- `getOrThrow(Supplier)`, `getOrThrow(Function<Throwable,X>)`: 성공이 아니면 그 예외. 취소 상태에서는 함수에 `CancellationException`이 전달된다.
- `map`·`flatMap`·`filter`는 함수가 던진 `Exception`을 실패로 바꾼다. `filter`가 거짓이면 취소 상태가 된다.
- `toTry()`: 취소는 `CancellationException`을 원인으로 하는 실패가 된다.
- `failure(null)`도 허용한다(원인을 모르는 실패).

---

## 4. `Either`

```java
Either<String,Integer> e = Either.left("a");
String s = e.fold(l -> "left:" + l, r -> "right:" + r);
e.left().ifPresent(...);          // left()·right()는 FOption
```

`left`/`right`는 `null`을 받지 않는다. `mapLeft`, `mapRight`, `flatMapLeft`, `flatMapRight`, `swap`, `match(leftConsumer, rightConsumer)`를 제공한다.

---

## 5. 지연 값

| | `Lazy` | `TimedLazy` | `UnsafeLazy` |
|---|---|---|---|
| 스레드 안전 | O (경합 시 supplier가 두 번 이상 불릴 수 있음) | O (만료 후 재적재는 한 번) | X |
| 만들기 | `of(supplier)`, `ofValue(v)` | `of(ttl, supplier)` | `of(supplier)`, `ofValue(v)` |
| 비우기 | `invalidate()`, `invalidate(dtor)` | `invalidate()` | `invalidate()`, `invalidate(dtor)` |
| 직접 넣기 | `set(v)` | `set(v)` | `set(v)` |
| supplier가 `null`을 냄 | `IllegalStateException` | `null`을 적재 | `IllegalStateException` |
| 적재 없이 보기 | `isLoaded()` | `peek()`(`FOption`), `peekOrNull()` | `isLoaded()` |

- `ofValue(v)`로 만든 인스턴스를 `invalidate()`한 뒤 `get()`하면 supplier가 없으므로 `IllegalStateException`이다.
- `TimedLazy.reload()`는 TTL과 상관없이 바로 다시 적재한다.
- `Lazy.wrap(supplier, Interface.class)`는 그 인터페이스의 메소드가 처음 불릴 때 실제 객체를 만드는 JDK 동적 프록시를 돌려준다. 상위 인터페이스·default 메소드도 위임된다. `equals`는 프록시 동일성, `hashCode`는 identity hash, `toString`은 `Lazy[...]`이며 셋 다 supplier를 부르지 않는다.

---

## 6. 검사 예외를 던지는 람다

| 하고 싶은 일 | 방법 |
|---|---|
| 검사 예외를 던지는 람다 선언 | `CheckedFunction`, `CheckedSupplier`, `CheckedConsumer`, `CheckedRunnable`, `CheckedPredicate`, `CheckedBi*`(모두 `throws Exception`), 예외 타입을 지정하려면 `Checked*X<…, X>` |
| 결과를 `Try`로 받기 | `checked.tryApply(x)`, `tryGet()`, `tryAccept(x)`, `tryRun()`, `tryTest(x)` |
| 표준 `java.util.function` 인터페이스로 바꾸기 | `UncheckedFunction.sneakyThrow(f)`, `UncheckedConsumer.ignore(c)`, `Unchecked*.lift(f, handler)` |
| 지금 바로 실행 | `Unchecked.getOrRTE(s)`, `runOrIgnore(r)`, `applyOrDefault(x, f, d)`, `getOrThrowSneakily(s)` 등 |

- `sneakyThrow`: 원래 예외를 감싸지 않고 선언 없이 다시 던진다.
- `*OrRTE`: `RuntimeException`·`Error`는 그대로, 검사 예외는 `RuntimeException`으로 감싸 던진다.
- `ignore`는 반환값이 없는 `UncheckedConsumer`·`UncheckedRunnable`·`UncheckedBiConsumer`에만 있다. 값을 돌려주는 래퍼에서 실패 처리기가 예외 없이 돌아오면 원래 예외를 원인으로 하는 `IllegalStateException`이 난다.
- 실패 처리기: `FailureHandlers.ignore()`, `throwSneakily()`, `count()`(`CountingFailureHandler`, thread-safe), `collect()`(`CollectingFailureHandler`). 처리기는 실패한 입력과 원인을 담은 `FailureCase`(`getData()`, `getCause()`)를 받는다.

---

## 7. 규칙

### 7.1 예외

- **`Exception`은 잡고 `Error`는 그대로 전파한다.** `Try.of`·`map`, `Checked*.try*`, `Unchecked*` 래퍼, `Unchecked.*OrRTE`, `Result.map` 모두 이 규칙이다. `Error`는 실패 처리기에도 넘어가지 않는다.
- 검사 예외를 감쌀 때는 한 번만 감싼다(`RuntimeException(원인)`).

### 7.2 이름

| 이름 | 뜻 |
|---|---|
| `getOrThrow(Supplier<X>)` | 값이 없으면 그 예외를 던진다(`FOption`·`Try`·`Result`) |
| `…OrThrow(Checked*X)` | 검사 예외를 던질 수 있는 함수를 받는다(`mapOrThrow`, `ifPresentOrThrow`, `getOrElseThrow`, `ifNotNullOrThrow`) |
| `getUnchecked()` | 실패 원인을 비검사 예외로 바꿔 던진다(`Try`·`Result`) |
| `orElse` / `orElseGet` | 대체값을 바로 / 함수로 준다(`Nullables`) |
| `ToTry`, `toFOption`, `toTry` | 결과를 `Try`·`FOption`으로 바꾼다 |

### 7.3 인자 검증

항상 쓰는 인자(`ifPresent`·`ifNotNull`의 consumer, `map`의 mapper)는 항상 검사하고, 대체(fallback) 인자(`getOrElse`·`orElseGet`의 supplier, `ifAbsent`·`ifNull`의 action)는 쓸 때만 검사한다. 위반하면 `IllegalArgumentException`이다.

---

## 8. 그 밖의 도우미

- [`Funcs`](Funcs.java): 컬렉션 도우미(`findFirst`, `getFirst`, `getLast`, `exists`, `filter`, `map`, `replaceFirst`, `replaceOrInsertFirst`, `removeFirstIf`, `removeIf`, `partition`, `branch`, `intersects` 등). 찾기 계열은 없으면 `null`을 돌려주므로, 원소가 `null`일 수 있으면 `FStream`의 `findFirst()` 등을 쓴다.
- [`MultipleCases`](MultipleCases.java): 키별 분기. `MultipleCases.of(map).when(k1).then(c1).when(k2).then(c2).otherwise().forEach(...)`. 남은 case는 입력 맵의 순서로 나온다.
- [`Slice`](Slice.java): `FStream.slice`용 구간(`start` 포함, `end` 제외, `step`). step은 `null`(= 1)이거나 양수여야 한다.
- `SerializablePredicate`, `NoSuchValueException`.
