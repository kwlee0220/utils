# `utils.stream` 사용자 가이드

Pull 기반의 lazy·자원-인식 함수형 스트림 라이브러리. `java.util.stream.Stream`과 유사한 연산 집합을 제공하되, 자원 해제(`AutoCloseable`), `FOption`/`Tuple`/`KeyValue` 일체화, 비동기 병렬 매핑, push→pull 브릿지를 추가로 지원한다.

이 가이드는 [`FStream`](../src/main/java/utils/stream/FStream.java)을 중심으로 패키지 사용법을 설명한다.

---

## 1. 개요

| 특성 | 내용 |
|---|---|
| **Pull 모델** | 소비자가 [`next()`](../src/main/java/utils/stream/FStream.java#L124)를 호출해 원소를 하나씩 당겨온다. 더 없으면 `FOption.empty()`. |
| **`AutoCloseable`** | 모든 스트림은 자원을 보유할 수 있다. terminal 연산이 자동으로 close한다. |
| **일회성** | 소진된 스트림은 재소비 불가. 재사용하려면 `toList()` 후 다시 `FStream.from()`. |
| **`FOption` 기반** | `null` 대신 `FOption`으로 유무를 표현한다. |
| **비동기 매핑** | `mapAsync` / `flatMapAsync` / `mergeParallel`로 워커 수·순서 보존을 지정한 병렬 처리. |
| **primitive 지원** | `IntFStream` / `LongFStream` / `FloatFStream` / `DoubleFStream` / `BooleanFStream`. |

핵심 타입은 다음과 같다.

| 타입 | 역할 |
|---|---|
| [`FStream<T>`](../src/main/java/utils/stream/FStream.java) | `Iterable<T>` + `AutoCloseable`. 거의 모든 연산이 `default` 메소드로 정의되어 있다. |
| [`KeyValueFStream<K,V>`](../src/main/java/utils/stream/KeyValueFStream.java) | `FStream<KeyValue<K,V>>`의 특화. key/value를 따로 다루는 연산 제공. |
| [`KeyedGroups<K,V>`](../src/main/java/utils/stream/KeyedGroups.java) | 키별 그룹핑 결과(`Map<K,List<V>>` 래퍼). |
| [`SuppliableFStream<T>`](../src/main/java/utils/stream/SuppliableFStream.java) | 외부에서 push하고 스트림으로 pull하는 bounded buffer. |
| [`AsyncExecutionOptions`](../src/main/java/utils/stream/AsyncExecutionOptions.java) | 비동기 연산의 워커 수/순서 보존/executor 설정. |
| [`FStreams.AbstractFStream<T>`](../src/main/java/utils/stream/FStreams.java#L52) | 사용자 정의 스트림 구현을 위한 베이스 클래스. |

---

## 2. 빠른 시작

```java
import utils.stream.FStream;

// 생성 → 중간 연산 → 종단 연산
List<String> names = FStream.from(persons)
                            .filter(p -> p.getAge() >= 20)
                            .map(Person::getName)
                            .sort()
                            .toList();          // toList()가 스트림을 close

// 자원을 보유한 스트림은 try-with-resources
try ( FStream<String> lines = FStream.from(reader.lines()) ) {
    lines.filter(l -> !l.isBlank())
         .forEach(System.out::println);         // forEach도 close
}
```

`next()`를 직접 호출하는 경우에는 close 책임이 호출자에게 있다.

```java
FStream<Integer> strm = FStream.of(1, 2, 3);
FOption<Integer> v;
while ( (v = strm.next()).isPresent() ) {
    System.out.println(v.get());
}
strm.close();       // 명시적 close 필요
```

---

## 3. 스트림 생성

### 3.1 컬렉션·배열로부터

```java
FStream.empty()                       // 빈 스트림
FStream.of("a", "b", "c")             // 가변 인자
FStream.from(list)                    // Iterable
FStream.from(iterator)                // Iterator
FStream.from(javaStream)              // java.util.stream.Stream
FStream.of(new int[] {1, 2, 3})       // → IntFStream
KeyValueFStream.from(map)             // Map → KeyValueFStream
```

### 3.2 생성 함수로부터

```java
FStream.range(0, 10)                          // IntFStream: 0..9
FStream.repeat("x")                           // 무한 반복
FStream.repeat("x", 5)                        // 5회 반복
FStream.generate(1, n -> n * 2)               // 1, 2, 4, 8, ... (무한)

// unfold: (상태) → Tuple(다음 상태, 방출 값). null 반환 시 종료
FStream.unfold(1, n -> (n > 100) ? null : Tuple.of(n * 2, n));
```

### 3.3 별도 쓰레드의 생성기로부터

[`Generator<T>`](../src/main/java/utils/stream/Generator.java)는 `Suppliable<T>` 채널로 데이터를 밀어 넣는 인터페이스다. 생성은 별도 쓰레드에서 수행되고, 소비자는 bounded buffer를 통해 pull한다.

```java
FStream<Row> rows = FStream.generate(channel -> {
    try {
        while ( resultSet.next() ) {
            channel.supply(toRow(resultSet));   // 버퍼가 full이면 대기
        }
        channel.endOfSupply();                  // 정상 종료
    }
    catch ( SQLException e ) {
        channel.endOfSupply(e);                 // 에러 종료
    }
}, 64);                                         // 버퍼 길이
```

> ⚠️ 생성기 기반 스트림([`GeneratorBasedFStream`](../src/main/java/utils/stream/GeneratorBasedFStream.java))은 **단일 consumer만 지원**한다. 여러 쓰레드가 동시에 `next()`/`close()`를 호출하면 생성 쓰레드 중복 시작·데이터 중복 등의 race가 발생한다.

### 3.4 결합

```java
FStream.concat(strm1, strm2)                  // 이어 붙이기
FStream.concat(streamOfStreams)               // FStream<FStream<T>> 평탄화
FStream.zip(listA, listB)                     // Tuple 스트림, 짧은 쪽에서 종료
FStream.zip(listA, listB, true)               // 긴 쪽까지, 부족분은 null
FStream.mergeParallel(streamOfStreams, 4, executor)   // 4개 워커로 병렬 소비·병합
```

---

## 4. 중간 연산

중간 연산은 새 스트림을 반환하며, close 책임을 소스 스트림으로 위임한다.

### 4.1 매핑·필터

| 메소드 | 설명 |
|---|---|
| `map(f)` / `filter(p)` / `filterNot(p)` | 기본 변환·선별 |
| `mapOrIgnore(f)` | `CheckedFunction` 적용. 예외가 난 원소는 결과에서 제외 |
| `tryMap(f)` | 결과를 `Try<S>`로 감싸 성공/실패를 모두 노출 |
| `mapSelectively(pred, f)` | `pred`를 만족하는 원소만 변환, 나머지는 그대로 통과 |
| `peek(effect)` | 통과하는 원소에 부수효과 적용 |
| `cast(cls)` | 전부 캐스팅 (실패 시 `ClassCastException`) |
| `castSafely(cls)` | 해당 타입 인스턴스만 골라 캐스팅 |
| `ofExactClass(cls)` | 정확히 그 클래스인 원소만 선별 |
| `mapToInt/Long/Float/Double/Boolean(f)` | primitive 스트림으로 변환 |

### 4.2 flatMap 계열

| 메소드 | 매핑 결과 | 동작 |
|---|---|---|
| `flatMap(f)` | `FStream<V>` | 평탄화 |
| `flatMapIterable(f)` | `Iterable<V>` | 평탄화 |
| `flatMapArray(f)` | `V[]` | 평탄화 |
| `flatMapNullable(f)` | `V` | `null` 결과 제거 |
| `flatMapFOption(f)` | `FOption<R>` | filter + map 통합 (empty면 제외) |
| `flatMapTry(f)` | `Try<V>` | 성공한 결과만 통과 |

### 4.3 부분 선택

```java
strm.take(10)                 // 앞에서 10개
strm.drop(3)                  // 앞 3개 버림
strm.dropLast(2)              // 뒤 2개 버림
strm.takeWhile(p)             // 조건이 깨지기 직전까지
strm.dropWhile(p)             // 조건이 깨지는 지점부터
strm.slice(Slice.builder().start(2).end(8).step(2).build())   // utils.func.Slice 구간 적용
strm.distinct()               // 전체 중복 제거 (HashSet 누적)
strm.distinct(keyer)          // 키 기준 중복 제거
strm.unique()                 // 인접 중복만 제거 (Unix uniq)
strm.unique(keyer)            // 키 기준 인접 중복 제거
```

### 4.4 결합·인덱싱

```java
strm.concatWith(other)                  // 스트림/Iterable/단일 원소 이어 붙이기
strm.zipWith(other)                     // Tuple<T,S> 스트림
strm.zipWith(other, zipper)             // 결합 함수 지정
strm.zipWith(other, true)               // 긴 쪽 기준
strm.zipWithIndex()                     // Indexed<T> 스트림 (0부터)
strm.zipWithIndex(1)                    // 시작 인덱스 지정
```

### 4.5 정렬·윈도우·샘플링

```java
strm.sort()                             // 자연 순서
strm.sort(comparator)
strm.sort(keyer)                        // 키 추출 후 자연 순서
strm.sort(keyer, true)                  // 역순
strm.quasiSort(1000)                    // 길이 1000의 우선순위 큐 기반 근사 정렬
strm.takeTopK(10)                       // 상위 10개
strm.takeTopK(10, keyer, true)          // 키 기준 하위 10개

strm.buffer(3, 1)                       // 슬라이딩 윈도우: [0,1,2], [1,2,3], ...
strm.buffer(3, 3)                       // 비중첩 청크
strm.split(p)                           // p를 만족하는 원소를 구분자로 분할(구분자는 제외)

strm.sample(0.1)                        // 각 원소를 10% 확률로 통과
strm.sample(total, 0.1)                 // 전체 개수를 알 때 정확히 total*0.1개 보장
strm.shuffle()                          // 전량 적재 후 무작위 방출
```

**`quasiSort`**: 길이 `queueLength`의 우선순위 큐를 채운 뒤 최소값부터 방출한다. 큐 길이를 넘어서는 순서 뒤집힘은 교정되지 않으므로 *거의 정렬된* 스트림에 쓴다. 전량 적재 없이 무한 스트림에도 적용 가능한 것이 `sort()`와의 차이다.

### 4.6 누적을 노출하는 스트림

```java
strm.reduceLeak((a, b) -> a + b)        // 중간 누적값을 모두 방출 (running total)
strm.foldLeak(accum, (s, t) -> ...)     // (새 누적값, 방출 원소) 쌍을 생성
```

### 4.7 파이프라인 합성

```java
// s.lift(f) === f.apply(s). 메소드 체인을 끊지 않고 외부 변환 함수를 끼워 넣는다.
FStream<Report> reports = FStream.from(rows)
                                 .lift(Normalizers::dropOutliers)
                                 .map(Report::from);
```

---

## 5. 종단 연산

종단 연산은 스트림을 소비하고 **정상/예외 흐름 모두에서 자동으로 close**한다.

### 5.1 순회·집계

```java
strm.forEach(effect)                    // 전체 순회
strm.forEachOrIgnore(checkedEffect)     // 예외를 무시하고 순회
strm.count()
strm.reduce((a, b) -> ...)              // FOption<T>
strm.fold(init, (accum, t) -> ...)      // S
strm.fold(init, stopper, folder)        // 누적값이 stopper와 같아지면 조기 종료
strm.foldRight(init, folder)            // 우측 결합 fold
strm.collect(mutableAccum, (acc, t) -> acc.add(t))
```

### 5.2 검색·검증

```java
strm.findFirst()          strm.findFirst(pred)      strm.findLast()
strm.exists()             strm.exists(pred)
strm.allMatch(pred)       strm.noneMatch(pred)
strm.startsWith(prefixStream)
strm.max()  strm.max(cmp)  strm.maxMultiple(cmp)    // 동률 전부 반환
strm.min()  strm.min(keyer) strm.minMultiple(keyer)
```

### 5.3 수집

```java
strm.toList()                           // ArrayList<T>
strm.toSet()                            // HashSet<T>
strm.toCollection(new TreeSet<>())
strm.toArray(String.class)
strm.takeLast(5)                        // 마지막 5개 List
strm.join(", ")   strm.join(", ", "[", "]")   strm.join(csv)
strm.stream()                           // java.util.stream.Stream으로 변환
```

### 5.4 키 기반 집계

```java
strm.reduceByKey(keyer, (a, b) -> ...)              // Map<K,T>
strm.foldByKey(keyer, init, folder)                 // Map<K,S>
strm.collectByKey(keyer, supplier, collector)       // Map<K,S>
```

---

## 6. Key-value 스트림

[`KeyValueFStream<K,V>`](../src/main/java/utils/stream/KeyValueFStream.java)는 `FStream<KeyValue<K,V>>`를 상속하여 key/value를 나누어 다루는 연산을 제공한다.

```java
// 생성
KeyValueFStream.from(map)
FStream.from(persons).tagKey(Person::getId)                  // 키 부여
FStream.from(rows).toKeyValueStream(Row::getId, Row::getValue)
KeyValueFStream.fromKeyed(keyedObjects)                      // Keyed<K> 구현체로부터
```

| 메소드 | 설명 |
|---|---|
| `keys()` / `values()` | 한쪽만 뽑아 `FStream`으로 |
| `filterKey(p)` / `filterValue(p)` | 한쪽 기준 선별 |
| `mapKey(f)` / `mapValue(f)` / `mapKeyValue(f)` | 한쪽 또는 양쪽 변환 (`BiFunction` 오버로드 제공) |
| `map(BiFunction)` / `flatMap(BiFunction)` / `forEach(BiConsumer)` | key·value를 별도 인자로 받는 형태 |
| `groupByKey()` | [`KeyedGroups<K,V>`](../src/main/java/utils/stream/KeyedGroups.java) 반환 |
| `sortByKey()` | 키의 자연 순서로 정렬 |
| `match(lut)` / `flatMatch(lut)` | 룩업 맵과 조인. `keepUnmatched=true`면 미매칭도 유지(값은 `null`) |
| `toMap()` / `toMap(map)` / `toListMap()` | `HashMap<K,V>` / 지정 맵 / `HashMap<K,List<V>>` |

```java
// 키별 그룹핑 후 값 집계
Map<String,Integer> countByDept = FStream.from(employees)
                                         .tagKey(Employee::getDept)
                                         .groupByKey()
                                         .fstream()           // KeyValueFStream<String,List<Employee>>
                                         .mapValue(List::size)
                                         .toMap();
```

`FStream` 쪽에도 룩업 연산이 있다 — `lookup(Map)` / `lookup(Function)`은 각 원소를 키로 보고 값을 붙여 `KeyValueFStream`을 만든다.

---

## 7. Primitive 스트림

박싱 비용 없이 처리하기 위한 5종. 각각 `FStream<Wrapper>`를 상속한다.

| 타입 | 생성 | 고유 연산 |
|---|---|---|
| [`IntFStream`](../src/main/java/utils/stream/IntFStream.java) | `FStream.range(a,b)`, `FStream.of(int[])`, `mapToInt(f)` | `sum()`, `average()`, `maxValue()`, `minValue()`, `toArray()` |
| [`LongFStream`](../src/main/java/utils/stream/LongFStream.java) | `LongFStream.range(a,b)`, `mapToLong(f)` | `sum()`, `average()`, `toArray()` |
| [`DoubleFStream`](../src/main/java/utils/stream/DoubleFStream.java) | `FStream.of(double[])`, `mapToDouble(f)` | `sum()`, `average()`, `toArray()` |
| [`FloatFStream`](../src/main/java/utils/stream/FloatFStream.java) | `FloatFStream.of(float[])`, `mapToFloat(f)` | `sum()`, `average()`, `toArray()` |
| [`BooleanFStream`](../src/main/java/utils/stream/BooleanFStream.java) | `BooleanFStream.of(boolean[])`, `mapToBoolean(f)` | `andAll()`, `orAll()`, `toArray()` |

```java
double avg = FStream.from(persons)
                    .mapToInt(Person::getAge)
                    .average()
                    .getOrElse(0.0);

// 객체 스트림으로 되돌리기
FStream<String> labels = FStream.range(0, 5).mapToObj(i -> "item-" + i);
```

---

## 8. 비동기 연산

### 8.1 `AsyncExecutionOptions`

불변 값 객체이며 `setXXX`는 새 인스턴스를 반환한다.

| 항목 | 기본값 | 의미 |
|---|---|---|
| `keepOrder` | `false` | 출력 순서를 입력 순서에 맞출지 |
| `workerCount` | `max(1, CPU 수 - 2)` | 동시 매핑 최대 개수 |
| `executor` | `null` (기본 풀) | 매핑 실행에 사용할 `Executor` |
| `timeoutMillis` | `-1` (무제한) | 제한 시간 |

```java
AsyncExecutionOptions opts = AsyncExecutionOptions.WORKER_COUNT(8)
                                                  .setKeepOrder(true)
                                                  .setExecutor(myPool);
```

### 8.2 `mapAsync`

각 원소에 매핑 함수를 병렬 적용한다. 결과 원소는 **`Tuple<T, Try<S>>`** — 입력 원소와 `Try`로 감싼 결과의 쌍이다. mapper가 예외를 던져도 스트림이 조기 종료되지 않고 `Try.failure`로 노출된다.

```java
FStream.from(urls)
       .mapAsync(this::fetch, AsyncExecutionOptions.WORKER_COUNT(8))
       .forEach(t -> t._2().ifSuccessful(body -> save(t._1(), body))
                           .ifFailed(e -> log.warn("failed: {}", t._1(), e)));
```

| `keepOrder` | 구현체 | 특성 |
|---|---|---|
| `true` | [`OrderedMapAsyncStream`](../src/main/java/utils/stream/OrderedMapAsyncStream.java) | 입력 순서 보존. 느린 작업 하나가 뒤따르는 결과들의 방출을 막는다 (head-of-line blocking). |
| `false` | [`UnorderedMapAsyncStream`](../src/main/java/utils/stream/UnorderedMapAsyncStream.java) | 완료 순서로 방출. 처리량이 높다. |

`mapCheckedAsync(CheckedFunction, options)`는 checked 예외를 던지는 매핑 함수용이다. 인자 없는 `mapAsync(mapper)`는 기본 옵션을 사용한다.

### 8.3 `flatMapAsync`

매핑 결과가 `FStream`인 경우의 비동기 버전.

```java
FStream<Record> all = FStream.from(files)
                             .flatMapAsync(this::readRecords,
                                           AsyncExecutionOptions.WORKER_COUNT(4));
```

`keepOrder=true`이면 `mapAsync` 후 순서대로 평탄화하고, `false`이면 `mergeParallel`로 여러 하위 스트림의 원소를 뒤섞어 방출한다.

### 8.4 `mergeParallel`

`FStream<FStream<T>>`를 워커 여러 개로 병렬 소비하여 하나로 병합한다. 워커는 하위 스트림 하나를 끝까지 소비한 뒤 다음 하위 스트림을 받아 재사용된다.

```java
FStream<T> merged = FStream.mergeParallel(streamOfStreams, 4, executor);
```

### 8.5 close 의미론 (중요)

| 구현 | `close()` 시 동작 |
|---|---|
| `OrderedMapAsyncStream` | 진행 중인 작업에 **cancel을 전파하지 않는다.** 작업은 끝까지 실행되고 결과는 silent drop. |
| `UnorderedMapAsyncStream` | 진행 중인 작업에 `cancel(true)`를 전파한다. 다만 `CompletableFuture.cancel`은 실행 중인 supplier 쓰레드를 interrupt하지 않으므로, 매핑 함수가 협조하지 않으면 끝까지 실행된다. |

두 경우 모두 close 이후 도착하는 결과는 조용히 버려진다. 매핑 함수가 외부 자원을 잡는다면 함수 자체에서 정리 책임을 져야 한다.

---

## 9. push → pull 브릿지 — `SuppliableFStream`

생산자가 데이터를 push하고 소비자가 `FStream`으로 pull하는 thread-safe bounded buffer. 모든 public 메소드가 thread-safe하여 다수의 producer/consumer가 동시에 사용할 수 있다.

```java
SuppliableFStream<Event> channel = new SuppliableFStream<>(100);

// producer
new Thread(() -> {
    try {
        for ( Event ev : source ) {
            channel.supply(ev);            // 버퍼가 full이면 대기
        }
        channel.endOfSupply();
    }
    catch ( Exception e ) {
        channel.endOfSupply(e);
    }
}).start();

// consumer
channel.forEach(this::handle);
```

| 메소드 | 설명 |
|---|---|
| `supply(v)` / `supply(v, timeout, unit)` | 적재. full이면 대기 / 제한 시간 대기 |
| `next()` / `next(timeout, unit)` | 소비. 비어 있으면 대기 / 제한 시간 대기 ([`TimedFStream`](../src/main/java/utils/stream/TimedFStream.java)) |
| `poll()` | 대기 없이 소비. 비어 있으면 `FOption.empty()` |
| `setSupplyListener(runnable)` | supply 시점에 호출될 listener 등록 |
| `capacity()` / `size()` / `emptySlots()` | 버퍼 상태 조회 |
| `endOfSupply()` / `endOfSupply(error)` | producer 측 종료 |
| `close()` | consumer 측 종료. 잔여 데이터를 **즉시 폐기**하고 이후 `supply`는 `IllegalStateException` |

**종료 규칙**:
- `endOfSupply()` 후에도 이미 적재된 데이터는 모두 소비 가능하다. 소진되면 `next()`가 `empty()`를 반환한다.
- `endOfSupply(error)`로 종료하면 잔여 데이터를 모두 소비한 뒤 해당 에러가 `RuntimeException`으로 래핑되어 던져진다.
- producer 측 종료 호출끼리는 **멱등**이다. `endOfSupply()`가 먼저 호출되면 이후의 `endOfSupply(error)`는 무시된다 — 에러 종료가 의도라면 반드시 `endOfSupply(error)`를 먼저 호출해야 한다.

---

## 10. 자원 관리와 close 규약

### 10.1 규칙

1. **terminal 연산은 자동 close한다** — `forEach`, `count`, `toList`, `toSet`, `reduce`, `fold`, `collect`, `findFirst`, `join` 등. `try/finally`로 감싸져 있어 예외 발생 시에도 close된다.
2. **intermediate 연산은 close를 소스로 위임한다** — `map`/`filter` 결과를 close하면 원본 스트림까지 닫힌다.
3. **close는 멱등하다** — 두 번째 이후의 `close()`는 무시된다.
4. **close 이후 `next()`는 `IllegalStateException`** — 소진(end-of-stream)과 close는 다른 상태다. 소진된 스트림의 `next()`는 계속 `FOption.empty()`를 반환한다.
5. **`closeQuietly()`** 는 예외를 던지지 않고 `Try<Void>`로 감싸 반환한다.

### 10.2 `onClose`

```java
FStream<Row> rows = FStream.from(resultSetIterator)
                           .onClose(() -> statement.close())
                           .onClose(() -> connection.close());   // 등록 순서대로 실행
```

`closingTask`는 원본 스트림의 close가 끝난 **직후** 실행된다.

### 10.3 사용자 정의 스트림

[`FStreams.AbstractFStream<T>`](../src/main/java/utils/stream/FStreams.java#L52)를 상속하고 두 메소드를 구현한다. `close()`는 `final`이라 멱등성과 상태 관리를 베이스가 보장한다.

```java
public class MyStream<T> extends FStreams.AbstractFStream<T> {
    @Override protected void initialize() { /* 첫 next() 직전에 1회 호출 (선택) */ }
    @Override protected FOption<T> nextInGuard() { ... }
    @Override protected void closeInGuard() throws Exception { ... }
}
```

---

## 11. 지연 평가 vs 즉시 평가

대부분의 중간 연산은 lazy하지만, **일부는 호출 시점 또는 첫 `next()` 시점에 소스를 전량 소비**한다. 무한 스트림이나 대용량 스트림에서 특히 중요하다.

| 연산 | 평가 시점 | 비고 |
|---|---|---|
| `map`, `filter`, `flatMap`, `take`, `drop`, `peek`, `distinct`, `unique`, `zipWith`, `buffer`, `split` | lazy | 원소당 처리 |
| `quasiSort` | lazy (큐 길이만큼 선행 적재) | **무한 스트림 가능** |
| `sample`, `slice` | lazy | |
| **`sort(...)`** | **호출 즉시** | `toList()` → 정렬 → 재스트림. 호출 시점에 소스가 소비·close된다 |
| **`takeTopK(...)`** | **호출 즉시** | 생성자에서 `forEach`로 전량 소비 |
| **`shuffle()`** | 첫 `next()` 시점 | 전량 `toList()` 후 무작위 방출 |
| **`dropLast(n)`, `takeLast(n)`** | 버퍼링 필요 | 마지막 n개를 알기 위해 선행 소비 |
| `mapAsync`, `flatMapAsync`, `mergeParallel` | lazy하되 선행 실행 | 소비 전에 워커 수만큼 작업이 미리 시작된다 |

즉, `sort()`와 `takeTopK()`는 "중간 연산처럼 보이지만 그 줄에서 소스를 다 읽는" 연산이다.

---

## 12. 함정 (gotchas)

### 12.1 for-each 중 `break`하면 close되지 않는다

`FStream`은 `Iterable`이므로 향상된 for 문을 쓸 수 있다. 반복이 끝까지 진행되면 자동 close되지만, `break`/`return`으로 중간에 빠져나오면 **close되지 않는다**.

```java
// 위험: break 시 close 누락
for ( Row row : stream ) {
    if ( row.isTerminator() ) break;   // ← stream이 열린 채로 남는다
    process(row);
}

// 안전: try-with-resources로 감싼다
try ( FStream<Row> s = stream ) {
    for ( Row row : s ) {
        if ( row.isTerminator() ) break;
        process(row);
    }
}
```

또한 `iterator()`는 호출 즉시 첫 원소를 미리 당겨온다.

### 12.2 스트림은 일회성

```java
FStream<String> s = FStream.from(list);
long n = s.count();          // 여기서 소비 + close
List<String> l = s.toList(); // IllegalStateException — 이미 close됨
```

두 번 쓰려면 `toList()`로 실체화한 뒤 다시 감싼다.

### 12.3 `distinct()`와 `unique()`는 다르다

`distinct()`는 스트림 전체에서 중복을 제거하며 `HashSet`에 본 값을 모두 누적한다(메모리 주의). `unique()`는 **인접한** 중복만 제거한다(Unix `uniq`).

### 12.4 `mapAsync`의 결과는 `Try`다 — 예외가 조용히 흡수된다

`mapAsync`/`mapCheckedAsync`는 mapper 예외로 스트림을 실패시키지 않는다. `Try.failure`를 확인하지 않으면 실패가 눈에 띄지 않는다. 실패 시 파이프라인을 중단하려면 직접 검사해야 한다.

### 12.5 무한 스트림에 전량 연산 금지

`FStream.repeat`, `generate`, `unfold`가 만드는 무한 스트림에 `sort()`, `shuffle()`, `takeTopK()`, `count()`, `toList()`를 적용하면 종료하지 않는다. `take(n)`으로 먼저 유한하게 만든다.

### 12.6 join 스트림은 아직 연결되어 있지 않다

패키지에 `InnerJoinedFStream` / `OuterJoinedFStream` 클래스가 있으나 둘 다 package-private이고, 진입점인 `KeyValueFStream.outerJoin`은 [주석 처리된 상태](../src/main/java/utils/stream/KeyValueFStream.java#L181)다. 현재 조인이 필요하면 `match` / `flatMatch` / `lookup`을 사용한다.

---

## 13. API 요약

### 13.1 공개 타입

| 타입 | 설명 |
|---|---|
| [`FStream<T>`](../src/main/java/utils/stream/FStream.java) | 핵심 인터페이스 |
| [`KeyValueFStream<K,V>`](../src/main/java/utils/stream/KeyValueFStream.java) / [`DefaultKeyValueFStream`](../src/main/java/utils/stream/DefaultKeyValueFStream.java) / [`KeyValueFStreams`](../src/main/java/utils/stream/KeyValueFStreams.java) | key-value 스트림 |
| [`KeyedGroups<K,V>`](../src/main/java/utils/stream/KeyedGroups.java) | 키별 그룹 컬렉션 |
| [`IntFStream`](../src/main/java/utils/stream/IntFStream.java) / [`LongFStream`](../src/main/java/utils/stream/LongFStream.java) / [`FloatFStream`](../src/main/java/utils/stream/FloatFStream.java) / [`DoubleFStream`](../src/main/java/utils/stream/DoubleFStream.java) / [`BooleanFStream`](../src/main/java/utils/stream/BooleanFStream.java) | primitive 스트림 |
| [`SuppliableFStream<T>`](../src/main/java/utils/stream/SuppliableFStream.java) / [`TimedFStream<T>`](../src/main/java/utils/stream/TimedFStream.java) | push→pull 브릿지 |
| [`Generator<T>`](../src/main/java/utils/stream/Generator.java) | 별도 쓰레드 데이터 생성기 |
| [`PrependableFStream<T>`](../src/main/java/utils/stream/PrependableFStream.java) | `prepend(v)`로 원소를 되돌리고 `peekNext()`/`hasNext()`로 미리 볼 수 있는 스트림. `toPrependable()`로 생성 |
| [`AsyncExecutionOptions`](../src/main/java/utils/stream/AsyncExecutionOptions.java) | 비동기 연산 옵션 |
| [`FStreams`](../src/main/java/utils/stream/FStreams.java) | 구현 버킷. 공개 멤버는 `AbstractFStream` |
| [`FStreamable<T>`](../src/main/java/utils/stream/FStreamable.java) | `fstream()`을 제공하는 객체의 인터페이스 |
| [`FStreamException`](../src/main/java/utils/stream/FStreamException.java) | 스트림 처리 예외 |

### 13.2 내부 구현 클래스

다음은 모두 package-private이며 `FStream`의 메소드를 통해서만 생성된다: `OrderedMapAsyncStream`, `UnorderedMapAsyncStream`, `FlatMapUnorderedAsyncStream`, `MergeParallelFStream`, `QuasiSortedFStream`, `ShuffledFStream`, `SlicedFStream`, `ZippedFStream`, `ConcatedStream`, `BufferedStream`, `MatchFStream`, `MatchKVFStream`, `GeneratorBasedFStream`, `FStreamIterator`, `InnerJoinedFStream`, `OuterJoinedFStream`.

---

## 14. 관련 문서

* 패키지 개발 노트: [src/main/java/utils/stream/CLAUDE.md](../src/main/java/utils/stream/CLAUDE.md)
* 비동기 실행 프레임워크: [src/main/java/utils/async/CLAUDE.md](../src/main/java/utils/async/CLAUDE.md) — `mapAsync` 계열이 내부적으로 `StartableExecution`을 사용한다.
* 라이브러리 전체 구조: [CLAUDE.md](../CLAUDE.md)
