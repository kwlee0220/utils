# `utils.stream` 검토: `FStream`·`KeyValueFStream`과 관련 코드

- 검토일: 2026-11-02
- 대상: [FStream.java](../../src/main/java/utils/stream/FStream.java), [KeyValueFStream.java](../../src/main/java/utils/stream/KeyValueFStream.java)와 관련 코드
  - [FStreams.java](../../src/main/java/utils/stream/FStreams.java), [FStreamIterator.java](../../src/main/java/utils/stream/FStreamIterator.java)
  - [MatchFStream.java](../../src/main/java/utils/stream/MatchFStream.java), [MatchKVFStream.java](../../src/main/java/utils/stream/MatchKVFStream.java)
  - [KeyedGroups.java](../../src/main/java/utils/stream/KeyedGroups.java), [TopKPickedFStream.java](../../src/main/java/utils/stream/TopKPickedFStream.java)
  - 기본형 스트림(`IntFStream`, `LongFStream`, `FloatFStream`, `DoubleFStream`, `BooleanFStream`)
- 상태 값: 대기 · 진행 · 완료 · 보류 · 기각
- 기준: 패키지 [CLAUDE.md](../../src/main/java/utils/stream/CLAUDE.md)의 닫기 규약.
  1. 종단 연산은 `finally`에서 스트림을 닫는다.
  2. 중간 연산은 `close()`를 원본에 전달한다.
  3. `close()`는 여러 번 불러도 안전하다.
- 확인 방법: "실험"은 임시 코드로 실행하거나 컴파일해 확인한 것이다. 실험 코드는 저장소 밖에 두었다. "코드"는 구현을 읽고 확인한 것이다. 검토 시점에 `gradle test --tests 'utils.stream.*'`는 모두 통과했다.
- 타이밍 의존 테스트: 검토 중 `utils.stream` 전체 실행에서 매번 다른 비동기 테스트 하나가 간헐적으로 실패했다. 실패한 것은 `FlatMapAsyncFStreamTest.test31`, `MapAsyncTest.test0`(시각 1ms 차이), `SuppliableStreamTest.test5`다. 셋 다 수정 대상과 무관하며 단독 재실행에서는 통과한다. 수정 결과를 판단할 때는 해당 테스트 클래스를 따로 확인한다. 이 테스트들을 안정화하는 일은 #7과 함께 본다. #7을 마친 뒤에도 `MapAsyncTest.test0`이 1ms 차이로 한 번 실패했다(2026-11-02, 재실행 세 번은 통과). 안정화는 아직 하지 않았다.
- 진행 현황(2026-11-02): 결함·컴파일·문서 항목 #1–#18과 #26을 마쳤다. 수정은 utils `7ee5fb3`(#2–#18, #26)로
  커밋했고, 사용처 수정은 mdt-client `aaf321e`, mdt-cli `b830ebe`, marmot·marmot.core·marmot.common·marmot.data
  (`474c59c`, `16fec19`, `7b1056a`, `9fc5f29`)로 커밋했다. 남은 것은 설계·이름 항목 #19–#24와 문서 정리 #25다.
- 사용처 수는 utils, utilsx, mdt/*의 `*.java`를 grep해 이름이 같은 다른 API를 손으로 거른 근사치다. 이 저장소는 이름을 바꿀 때 별칭을 두지 않으므로, 사용처 수가 곧 수정 비용이다.

## 항목 요약

| # | 분류 | 심각도 | 제목 | 상태 |
|---|---|---|---|---|
| 1 | 결함 | 높음 | `match`·`flatMatch`·`lookup` 결과를 닫아도 원본이 닫히지 않음 | 완료 |
| 2 | 결함 | 높음 | `onClose`로 감싼 스트림을 닫아도 원본이 닫히지 않음, 문서와 반대 | 완료 |
| 3 | 결함 | 높음 | `foldLeak` 결과가 틀림 | 완료 |
| 4 | 결함 | 중간 | `takeTopK`가 큰 값이 아니라 작은 값 k개를 고름 | 완료 |
| 5 | 결함 | 중간 | `FStreamIterator`: 빈 스트림 누수, null 원소에서 반복이 잘림 | 완료 |
| 6 | 결함 | 중간 | `mapOrThrow` 계열의 검사 예외를 `catch`할 수 없음 | 완료 |
| 7 | 결함 | 중간 | `flatMapAsync`·`flatMapCheckedAsync`가 mapper 실패를 흔적 없이 버림 | 완료 |
| 8 | 결함 | 중간 | `lookup`과 `match`의 일치 판정 기준이 다름 | 완료 |
| 9 | 결함 | 중간 | `flatMatch`의 null 목록·빈 목록 처리가 inner/outer 의미와 맞지 않음 | 완료 |
| 10 | 결함 | 중간 | `toListMap`·`groupByKey`: 예외 시 닫지 않음, null 처리 불일치, 중복 | 완료 |
| 11 | 결함 | 낮음~중간 | `KeyedGroups`가 내부 맵을 그대로 노출 | 완료 |
| 12 | 결함 | 중간 | 기본형 스트림: `of` 오버로드 모호, `FloatFStream.sum` 정밀도, 기능 불균형 | 완료 |
| 13 | 컴파일 | 중간 | 제네릭 변성(`? super`/`? extends`) 누락으로 흔한 호출이 컴파일되지 않음 | 완료 |
| 14 | 컴파일 | 낮음~중간 | `Function`/`BiFunction` 오버로드가 메소드 참조에서 모호함 | 완료 |
| 15 | 결함 | 낮음 | 인자 검증·오류 메시지 문제 | 완료 |
| 16 | 결함 | 낮음 | `reduceByKey`: reducer가 null을 내면 누적이 다시 시작됨 | 완료 |
| 17 | 결함 | 낮음 | 작은 결함 모음 | 완료 |
| 18 | 문서 | 낮음 | Javadoc과 코드 불일치 | 완료 |
| 19 | 설계 | — | key-value 변환 진입점이 다섯 개로 중복 | 완료 |
| 20 | 설계 | — | 실패 처리 연산의 이름 체계가 제각각 | 대기 |
| 21 | 설계 | — | `KeyValueFStream`의 중간 연산 대부분이 `FStream<KeyValue>`로 타입을 잃음 | 대기 |
| 22 | 설계 | — | 빠진 연산 | 대기 |
| 23 | 설계 | — | 반환 타입: `Optional`과 구체 컬렉션 클래스 | 대기 |
| 24 | 이름 | — | 이름 변경 제안 | 진행 (2·3·4·5·19번 완료) |
| 25 | 문서 | — | README·CLAUDE.md 갱신 | 진행 (항목마다 갱신) |
| 26 | 결함 | 중간 | `keepOrder=true`인 `flatMapAsync`를 중간에 닫으면 만들어 둔 하위 스트림이 닫히지 않음 | 완료 |

## 권장 수정 순서

1. **자원 누수: #1, #2, #5.**
   - 닫기 규약 위반이라 JDBC 커넥션 같은 실제 자원이 샐 수 있다.
   - #2는 호출처가 내부 `from(Stream)` 한 곳뿐이라 지금 고치는 비용이 작다.
2. **틀린 결과지만 사용처가 0인 것: #3, #4.** 고치거나 지워도 영향이 없다. #3은 #24의 `scan` 제안과 함께 정한다.
3. **예외·실패 처리: #6, #7.** 실패가 숨거나 잡을 수 없는 문제다. #6의 이름 변경은 #20·#24와 함께 정한다.
4. **key-value 대응 연산의 의미 통일: #8, #9, #10, #11.** `match`·`lookup`·`flatMatch`·`groupByKey`가 같은 규칙을 따르게 한다.
5. **컴파일 문제: #12, #13, #14.**
   - #13은 시그니처만 넓히므로 기존 호출은 그대로 컴파일된다.
   - #12의 `of` 모호성은 기본형 스트림을 리터럴로 만들 수 없게 하므로 이 단계에서 가장 먼저 고친다.
6. **작은 결함과 문서: #15, #16, #17, #18.**
7. **설계·이름: #19 → #20 → #21 → #22 → #23 → #24.** 사용처가 많은 이름 변경은 마지막에 하고, 결정이 필요하면 보류한다.
8. **#25 문서 갱신.** 각 단계에서 동작이 바뀔 때마다 README·CLAUDE.md를 함께 고치고, 마지막에 남은 것을 정리한다.

---

## 결함

### #1 `match`·`flatMatch`·`lookup` 결과를 닫아도 원본이 닫히지 않음 — 높음

- 위치: [MatchKVFStream.java](../../src/main/java/utils/stream/MatchKVFStream.java)와 [MatchFStream.java](../../src/main/java/utils/stream/MatchFStream.java)의 `closeInGuard() { }`
- 두 클래스 모두 원본 스트림(`m_stream`)을 들고 있지만 `closeInGuard()`가 비어 있다. 닫기 규약 2번을 어긴다.
- 재현(실험): `KeyValueFStream.from(src.onClose(..)).match(Map.of("a","A")).toList()`의 결과는 맞지만 원본은 닫히지 않는다. `lookup`·`flatMatch`도 같다.
- 영향: 원본이 `ResultSetFStream`처럼 자원을 가진 스트림이면 커넥션이 샌다. MDT의 `ElementCollectionValue`, mdt-cli의 `Run*Command` 등에서 `match`를 쓴다.
- **제안:** 두 클래스의 `closeInGuard()`에서 `m_stream.close()`를 호출한다. 다른 구현처럼 `SingleSourceStream` 계열로 바꾸는 방법도 있다. 닫힘을 확인하는 테스트를 추가한다.
- 결과: 완료(2026-11-02).
  - `MatchFStream`·`MatchKVFStream`의 `closeInGuard()`가 원본(`m_stream`)을 닫는다. `flatMatch`는 `match` 위에 만들어지므로 함께 해결된다.
  - 테스트: `KVMatchTest.matchClosesSource`를 추가했다. `match`·`flatMatch`·`lookup`을 끝까지 읽었을 때와, 한 원소만 읽고 닫았을 때 원본이 닫히는지 확인한다.
  - 확인: `utils.stream` 테스트 254개, mdt-client 337개 통과.
  - 참고: 첫 실행에서 `FlatMapAsyncFStreamTest.test31`이 한 번 실패했다(`m_count` 기대 1, 실제 2). 이 테스트는 `match`/`lookup`과 무관하고, 단독 3회와 전체 재실행에서는 통과했다. 비동기 생성기가 몇 번 시작되는지에 따라 결과가 흔들리는 테스트로 보이며, #7을 다룰 때 함께 본다.

### #2 `onClose`로 감싼 스트림을 닫아도 원본이 닫히지 않음, 문서와 반대 — 높음

- 위치: [FStreams.java](../../src/main/java/utils/stream/FStreams.java) `CloserAttachedStream.close()`, [FStream.java](../../src/main/java/utils/stream/FStream.java) `onClose`
- `close()`는 `m_closingTask.run()`만 하고 `m_base.close()`를 부르지 않는다. 닫힌 뒤 `next()`도 `IllegalStateException` 대신 빈 값을 돌려준다.
- Javadoc("본 스트림의 close 작업이 완료된 직후 실행")과 CLAUDE.md 닫기 규약 4번이 실제 동작과 반대다. README 10.2절은 현재 동작을 적고 있다.
- 재현(실험): `src.onClose(t).toList()` 뒤에 `src`가 닫히지 않는다.
- **제안:** `close()`에서 원본을 먼저 닫고(`try`), 그다음 작업을 실행한다(`finally`). 닫힌 뒤의 `next()`는 다른 스트림처럼 `IllegalStateException`을 던진다. 호출처는 `from(Stream)`과 테스트뿐이다.
- 결과: 완료(2026-11-02). 제안대로 처리했다.
  - `CloserAttachedStream.close()`는 원본을 먼저 닫고 등록한 작업을 실행한다. 원본 닫기가 실패해도 작업은 실행되며, 둘 다 실패하면 작업의 예외를 원본 예외의 suppressed로 붙여 원본 예외를 던진다.
  - 연달아 등록한 작업(`onClose(a).onClose(b)`)은 바깥 래퍼가 안쪽 래퍼를 닫으므로 `a`, `b` 순서로 모두 실행된다. 이전에는 가장 바깥 작업만 실행됐다.
  - 닫은 뒤 `next()`는 빈 값 대신 `IllegalStateException`을 던진다(`AbstractFStream`과 같은 메시지).
  - 문서: `FStream.onClose` Javadoc, README 10.1 표·10.2절(제목을 "현재 제약"에서 "동작"으로)·문제 진단 표의 해당 행, CLAUDE.md 닫기 규약 4번을 실제 동작에 맞췄다.
  - 테스트: `OnCloseTest`에 세 개를 추가했다.
    - `closesSourceBeforeTask`: 원본이 먼저 닫힌다.
    - `chainedTasksRunInRegistrationOrder`: 연달아 등록한 작업이 순서대로 실행되고, 중복 close는 무시되며, 닫은 뒤 `next()`는 예외다.
    - `taskRunsEvenIfSourceCloseFails`: 원본 닫기 실패 시에도 작업이 실행되고 예외가 suppressed로 붙는다.
  - 확인: `utils.stream` 테스트 258개 통과. 호출처는 `FStream.from(Stream)`과 테스트(`CountTest`·`FindLastTest`·`TakeLastTest`·`OnCloseTest`)뿐이고, utilsx·MDT에는 사용처가 없다.

### #3 `foldLeak` 결과가 틀림 — 높음 (사용처 0)

- 위치: [FStreams.java](../../src/main/java/utils/stream/FStreams.java) `FoldLeftLeakFStream`
- `m_combine.apply(m_state, m_last)`가 입력값 `v`를 버리고 직전 출력을 다시 결합한다. 초기값도 첫 원소에 적용되지 않는다. `m_last == null`을 "첫 호출" 표시로 쓰므로 결합 결과가 null이면 상태가 처음으로 돌아간다.
- 재현(실험): `[1,2,3,4].foldLeak(0, (s,t) -> (s+t, s+t))`는 `[1,3,6,10]`이어야 하는데 `[1,1,2,4]`가 나온다.
- **제안:** 사용처가 0이므로 둘 중 하나를 고른다.
  - 지운다.
  - `scan(init, f)`(누적값을 차례로 내는 연산, #24)로 다시 구현한다. 이때 첫 호출 여부는 별도 플래그로 판단한다.
- 결과: 완료(2026-11-02). #24의 2번에 따라 `foldLeak`을 지우고(별칭 없음, 사용처 0) `scan(init, folder)`로 다시 구현했다(`FoldScannedStream`). 첫 호출 플래그 없이 매 원소마다 `init`부터 누적하므로 초기값이 첫 원소에 적용되고, `null` 누적값도 그대로 이어진다. 테스트 `ScanTest`.

### #4 `takeTopK`가 큰 값이 아니라 작은 값 k개를 고름 — 중간 (비교자 오버로드는 marmot 8곳)

- 위치: [TopKPickedFStream.java](../../src/main/java/utils/stream/TopKPickedFStream.java), [FStream.java](../../src/main/java/utils/stream/FStream.java) `takeTopK`
- `MinMaxPriorityQueue.maximumSize(k)`는 넘칠 때 가장 큰 원소부터 버린다. 그래서 작은 값 k개가 오름차순으로 남는다.
- 재현(실험): `[5,1,3,4].takeTopK(2)`는 `[1,3]`, `takeTopK(2, true)`는 `[5,4]`다. Javadoc("큰 값 k개 / reverse = 하위 k개 / 정렬되지 않을 수 있음")과 반대다.
- **제안:** 비교자를 뒤집어 큐에 넣고, 출력 순서(내림차순)를 문서로 정한다. 테스트를 추가한다.
- 결과: 완료(2026-11-02). "사용처 0"은 틀렸다 — marmot이 `takeTopK(k, Comparator)`를 8곳에서 쓰며, 모두 지금 의미("비교자 순서상 앞쪽 k개", 큰 값은 뒤집은 비교자로)에 맞춰져 있다.
  - 순서를 지정하는 `SortOrder { ASCENDING, DESCENDING }`(새 enum, `utils.stream`)을 추가했다. `takeTopK(k)`·`takeTopK(k, keyer)`는 기본 `DESCENDING`(큰 값 k개를 큰 값부터), `takeTopK(k, order)`·`takeTopK(k, keyer, order)`로 지정한다. `boolean reverse` 오버로드 2개는 삭제(사용처 0, 별칭 없음).
  - `takeTopK(k, Comparator)`는 의미 유지: `sort(cmp).take(k)`와 같다고 Javadoc에 명시. marmot 수정 불필요.
  - 결과는 정렬된 순서로 나온다고 문서로 정했다(옛 Javadoc의 "정렬되지 않을 수 있음"은 틀림).
  - `TopKPickedFStream`: 생성자에서 원본을 읽던 것을 첫 `next()`로 미뤘고, `k == 0`이면 `hasNext()`에서 NPE가 나던 메소드를 없앴으며(인터페이스에 없는 public 메소드), 비교자 `null`을 거부한다.
  - 테스트 `TakeTopKTest`(10개).
  - 이어서 `sort`·`quasiSort`도 `SortOrder`로 맞췄다. `sort(keyer, boolean)`·`quasiSort(n, keyer, boolean)`을 `SortOrder` 오버로드로 바꾸고(사용처는 `QuasiSortTest` 한 곳, 별칭 없음), 자연 순서용 `sort(SortOrder)`·`quasiSort(n, SortOrder)`를 추가했다. 기본은 오름차순 그대로. 비교자 생성은 `FStream`의 private static `naturalComparator`/`keyComparator`로 모았다(`takeTopK`도 사용). 테스트 `SortTest`에 4개 추가.

### #5 `FStreamIterator`: 빈 스트림 누수, null 원소에서 반복이 잘림 — 중간

- 위치: [FStreamIterator.java](../../src/main/java/utils/stream/FStreamIterator.java)
- 생성자에서 첫 원소를 미리 읽는데, 이때 비어 있으면 스트림을 닫지 않는다. `for (x : 빈스트림)`은 원본을 닫지 않는다.
- `m_next == null`을 "끝"으로 쓰기 때문에 원소가 null이면 거기서 반복이 끝난다. `stream()`도 이 반복자를 쓴다.
- 재현(실험): `[a, null, c]`를 for-each로 돌면 `[a]`만 나온다. `stream().count()`는 1이다.
- README에는 "빈 입력에서 정리가 빠질 수 있음"만 적혀 있고, `stream()`이 잘린다는 점은 없다.
- **제안:** 끝 여부를 별도 플래그(또는 `FOption`)로 들고, 끝에 닿는 즉시 닫는다(생성자 포함). 첫 원소를 미리 읽지 않고 `hasNext()`에서 읽는 방식도 가능하다.
- 결과: 완료(2026-11-02). 제안의 두 방식 중 "미리 읽지 않고 `hasNext()`에서 읽는" 방식을 택했다.
  - `FStreamIterator`는 다음 원소를 `FOption`으로 들고, `hasNext()`가 처음 불릴 때 하나씩 읽는다. 끝 여부를 `FOption`으로 판단하므로 null 원소도 그대로 반환한다.
  - 끝에 닿는 즉시 원본을 닫는다. 처음부터 빈 스트림도 마찬가지다. `close()`는 끝에 닿지 않았을 때만 원본을 닫는다.
  - 미리 읽지 않으므로 for 문에서 `break`해도 그다음 원소는 소비되지 않는다. 이전에는 생성할 때와 각 `next()`에서 미리 읽었다.
  - `stream()`도 같은 반복자를 쓰므로 null 원소에서 잘리지 않는다.
  - 문서: `FStream.iterator()` Javadoc, `FStreamIterator` 클래스 Javadoc, README 0절의 null 설명·10.1 표·10.3절, CLAUDE.md "Common gotchas"의 반복자 항목을 고쳤다.
  - 테스트: `IteratorTest`에 세 개를 추가했다.
    - `nullElementIsNotEndOfStream`: for-each와 `stream().count()`가 `[a, null, c]`를 모두 센다.
    - `closesSourceAtEnd`: 빈 스트림과 끝까지 순회한 스트림이 닫힌다.
    - `readsLazily`: `iterator()` 생성과 `next()`가 다음 원소를 미리 읽지 않는다.
  - 확인: `utils.stream` 테스트 261개, mdt-client 337개 통과.

### #6 `mapOrThrow` 계열의 검사 예외를 `catch`할 수 없음 — 중간

- 위치: [FStream.java](../../src/main/java/utils/stream/FStream.java) `mapOrThrow`, `mapToKeyValueOrThrow`
- 검사 예외 `X`는 나중의 종단 연산(`toList` 등)에서 sneaky-throw로 던져진다. 그런데 종단 연산은 `throws X`를 선언하지 않는다.
- 재현(실험): `try { s.mapOrThrow(io).toList(); } catch (IOException e)`는 "IOException is never thrown" 컴파일 오류가 난다. 호출자는 `catch (Exception)`을 쓰거나, 같은 블록에 다른 `IOException` 경로가 있을 때만 이 예외를 잡을 수 있다.
- 이름("OrThrow")과 같은 계열의 `forEachOrThrow`는 `throws X`를 제대로 선언하므로 사용자가 같은 동작을 기대한다.
- **제안:** 둘 중 하나를 고른다.
  - **(a)** 이름을 동작에 맞게 바꾼다(`mapSneaky` 등, #24). 사용처는 약 23곳(+테스트 2), `mapToKeyValueOrThrow`는 3곳이다.
  - **(b)** 이름을 유지하고, Javadoc에 "검사 예외는 종단 연산에서 선언 없이 던져지므로 `catch (Exception)`이나 `Throwables`로 다뤄야 한다"를 명시한다.
  - 권장은 (b)를 먼저 하고, 이름 변경은 #20의 이름 체계와 함께 정하는 것이다.
- 결과: 완료(2026-11-02), (b)안. 코드·이름은 그대로 두고 문서만 고쳤다.
  - `mapOrThrow` Javadoc: 예외가 나는 시점(원소를 꺼낼 때), 감싸지 않은 sneaky throw이며 종단 연산이 `throws`를 선언하지 않는다는 점, 종단 연산이 닫으므로 원본은 닫힌다는 점, 잡는 방법(`throws` 선언 / 같은 `try`에 `X` 선언 호출이 있을 때만 `catch (X)` / `catch (Exception)` + `instanceof`·`Throwables.throwIfInstanceOf`), 대안(`forEachOrThrow`, `tryMap`), "OrThrow"가 `throws` 선언을 뜻하지 않는다는 점.
  - `mapToKeyValueOrThrow` Javadoc은 `mapOrThrow`를 가리킨다.
  - README 4.1에 설명과 예제, 12장 문제 상황 표에 "`catch (IOException)` never thrown" 행. CLAUDE.md Common gotchas에 한 줄.
  - 이름 변경((a)안)은 #20·#24에서 정한다.

### #7 `flatMapAsync`·`flatMapCheckedAsync`가 mapper 실패를 흔적 없이 버림 — 중간

- 위치: [FStream.java](../../src/main/java/utils/stream/FStream.java) `flatMapCheckedAsync`·`flatMapAsync`, `MergeParallelFStream`
- `keepOrder=true`이면 `flatMapTry`가 실패를 버린다. `false`이면 워커 실행이 실패한 뒤 `whenFinished → startNext`로 다음으로 넘어간다.
- 재현(실험): `[1,2,3]`에서 2의 mapper가 예외를 던지면 결과는 `[10,30]`이고, 예외도 로그도 남지 않는다.
- `mapAsync`가 실패를 `Try`로 드러내는 것과 정책이 다르다.
- **제안:** 둘 중 하나를 고른다.
  - 실패를 결과 스트림의 오류로 전파한다. 종단 연산에서 예외가 나고 나머지는 취소한다.
  - `mapAsync`처럼 `Try`를 노출하는 변형만 남긴다.
  - 최소한 실패를 로그로 남기고 Javadoc에 적는다.
- 결과: 완료(2026-11-02). 첫째 안(결과 스트림의 오류로 전파)을 택했다.
  - 이유: 동기 `flatMap`과 같은 동작, 하위 스트림을 읽다가 일부를 낸 뒤의 실패는 입력당 `Try`로 표현할 수 없음, 실패 허용은 mapper 안에서 할 수 있음(유일한 사용처 marmot `RangeQuery`가 이미 그렇게 함).
  - `keepOrder=true`: `flatMapTry` 대신 `flatMap(t -> t._2().get())` — `Try.get()`이 원래 예외를 sneaky throw한다.
  - `keepOrder=false`(`MergeParallelFStream`): 처음 실패를 `m_failure`에 기록하고 출력 채널을 `endOfSupply(e)`로 끝낸다(`failInGuard`). 이후 새 내부 스트림은 시작하지 않고, 다른 워커는 다음 push에서 실패해 멈춘다(이 실패와 소비자가 닫은 뒤의 실패는 무시). `nextInGuard`가 채널이 감싼 `RuntimeException`을 원래 예외로 바꿔 던진다. 팩토리에서 다음 내부 스트림을 꺼내다 난 예외, 워커 인터럽트도 실패로 처리한다(인터럽트는 전에 경고 로그만 남겼다).
  - 예외 형태: 감싸지 않은 원래 예외(검사 예외는 `mapOrThrow`처럼 선언 없이). `SuppliableFStream` 자체의 감싸기 정책은 바꾸지 않았다.
  - 테스트: `FlatMapAsyncFailureTest`(7개: 두 모드의 검사·비검사 mapper 실패, 하위 스트림 읽기 실패, 실패 전 원소 전달, 실패 뒤 새 입력 미시작, mapper 안에서 실패를 처리하면 나머지 유지). 옛 동작을 고정하던 `FlatMapAsyncFStreamTest.test10`·`MergeParallelFStreamTest.test10`을 실패 전파를 확인하도록 바꿨다. 관련 테스트를 5회 반복 실행해 안정적임을 확인.
  - 셋째 안(로그만 남기기)은 전파로 대체되어 하지 않았다.

### #8 `lookup`과 `match`의 일치 판정 기준이 다름 — 중간

- 위치: [MatchFStream.java](../../src/main/java/utils/stream/MatchFStream.java)의 `value != null`, [MatchKVFStream.java](../../src/main/java/utils/stream/MatchKVFStream.java)의 `containsKey`
- 재현(실험): 참조 맵이 `{a=null}`일 때 결과가 다르다.
  - `FStream.of("a","b").lookup(lut)` → `[]`
  - `KeyValueFStream.of(a=1).match(lut)` → `[a=(1,null)]`
- README는 두 연산을 같은 계열로 소개한다.
- `lookup(Function)`은 키 존재 여부를 알 수 없으므로 null을 "없음"으로 볼 수밖에 없다. 그래서 `Map` 버전만 `containsKey`로 맞출지, 아니면 `match`를 다시 null 기준으로 돌릴지 정해야 한다.
- **제안:** `Map`을 받는 버전은 모두 `containsKey`로 통일한다. `Function` 버전은 "null을 반환하면 일치하지 않음"으로 Javadoc에 명시한다.
- 결과: 완료(2026-11-02). **제안대로** `Map`을 받는 `lookup`은 `containsKey`로 판단한다. 값이 `null`인 키도 일치하며 `(키, null)`을 낸다(`match`와 같은 기준). `Function` 버전은 "`null`을 반환하면 일치하지 않음"을 Javadoc에 명시했다.
  - 경과: 처음에는 사용자 결정으로 `lookup(Map)`을 `lut::get`(null 기준)으로 두고 "값이 null인 키가 없어야 한다"는 제약을 문서화했으나, 다시 제안대로 바꾸기로 했다.
  - `MatchFStream`: 내부 판정을 `Function<K,FOption<V>>`로 일원화했다. `Map` 생성자는 `get()` 결과가 `null`일 때만 `containsKey`를 확인하고(대부분 한 번 조회), `Map<? super K,? extends V>`로 넓혔다. 클래스 Javadoc에 두 판정 기준과, `keepUnmatched`일 때 값이 `null`인 키와 없는 키가 결과에서 구별되지 않는다는 점을 적었다.
  - `FStream.lookup` 세 오버로드에 Javadoc을 붙였다(변성 확장은 #13에서).
  - 테스트: `KVMatchTest.lookupTreatsNullValueAsUnmatched`를 `lookupByMapMatchesKeysWithNullValues`로 바꿨다(맵 버전은 `a=null`도 일치, 함수 버전은 불일치, `match`와 같은 결과).
  - 영향: `lookup(Map)` 호출처 두 곳(utils `RESTfulCommandExecutionServer`, mdt-instance `InternalSegmentRecordsReader`)은 값이 `null`일 수 없는 맵이라 결과가 바뀌지 않는다.

### #9 `flatMatch`의 null 목록·빈 목록 처리가 inner/outer 의미와 맞지 않음 — 중간

- 위치: [KeyValueFStream.java](../../src/main/java/utils/stream/KeyValueFStream.java) `flatMatch`
- 재현(실험): 참조 맵이 `{a=null, b=[]}`이고 키가 a·b·c일 때 결과가 다음과 같다.
  - `flatMatch(lut, false)` → `[a=(1,null)]`. 일치하는 것만 원했는데 참조 값이 null인 행이 나온다.
  - `flatMatch(lut, true)` → `[a=(1,null), c=(3,null)]`. 모두 유지하라고 했는데 **b가 사라진다**.
- 현재 동작은 어제 쓴 Javadoc에 그대로 적혀 있지만, inner/outer join 감각과 맞지 않는다.
- **제안:** null 목록은 빈 목록과 같이 0행으로 처리한다. `keepUnmatched=true`이면 목록이 비어 있거나 키가 없을 때 `(v, null)` 1행을 낸다(outer join). Javadoc을 함께 고친다.
- 결과: 완료(2026-11-02). 제안대로 고쳤다.
  - 규칙: 참조 값이 하나도 없는 원소(빈 목록, `null` 목록, 없는 키)는 모두 "일치하지 않음"이다. `keepUnmatched=false`(inner join)는 0행, `true`(left outer join)는 `(v, null)` 1행. 값이 n개면 n행.
  - 구현: `match`를 거치지 않고 `flatMap`으로 직접 처리한다. 비었는지 보려고 `iterator()`를 따로 부르지 않고 한 번 얻은 반복자로 확인·순회하므로, 한 번만 순회할 수 있는 `Iterable`도 동작한다. `lut`의 `null`을 거부한다.
  - Javadoc: 두 오버로드 모두 다시 썼다. 상태별 결과 행 표, `match`와의 차이(`null` 목록), `keepUnmatched`의 `(v, null)`이 목록 안 `null` 원소와 구별되지 않는다는 점, 목록 순회 횟수, lazy·닫기 전파, 옛 동작.
  - 테스트: `KVMatchTest.flatMatchTreatsEmptyAndNullListsAsUnmatched`(재현 사례 `{a=null, b=[]}`의 inner/outer 결과), `flatMatchIteratesAOneShotIterableOncePerElement`. 기존 `test1`·`test3`은 그대로 통과한다.
  - 영향: `flatMatch` 사용처는 utils 테스트뿐이다(MDT·marmot 0).

### #10 `toListMap`·`groupByKey`: 예외 시 닫지 않음, null 처리 불일치, 중복 — 중간

- 위치: [KeyValueFStream.java](../../src/main/java/utils/stream/KeyValueFStream.java) `toListMap`·`groupByKey`, [KeyedGroups.java](../../src/main/java/utils/stream/KeyedGroups.java) `add`
- `toListMap`은 for-each로 돌기 때문에 도중에 예외가 나면 원본을 닫지 않는다(규약 1번 위반, 실험으로 확인). `collect(...)`로 바꾸면 된다.
- null 값 처리가 서로 다르다. `KeyValue`는 null 값을 허용한다.
  - `groupByKey()`는 null 값을 IAE("value is null")로 거부한다.
  - `toListMap()`은 `{a=[null]}`로 받는다.
- 두 연산은 같은 일을 한다(`toListMap()` ≈ `groupByKey().asMap()`). `toListMap`의 사용처는 mdt에 1곳이다.
- **제안:** `toListMap`을 `collect` 기반으로 바꾼다. null 값 정책은 허용으로 통일하는 것을 권장한다. `toListMap`을 지우고 `groupByKey()`로 일원화할지도 정한다.
- 결과: 완료(2026-11-02).
  - `toListMap`을 `collect` 기반으로 바꿔 도중 예외에도 원본을 닫는다.
  - null 값은 허용으로 통일했다. `KeyedGroups.add`의 `value != null` 검사(IAE)를 없애, `groupByKey()`도 `toListMap()`처럼 `{a=[null]}`을 낸다. 검사를 없애는 쪽이라 기존 호출자는 깨지지 않는다.
  - `toListMap`은 지우지 않았다(mdt-instance `AssertVariableBasedPersistence` 1곳이 사용, `HashMap` 반환). 두 연산이 같은 결과라는 점을 서로의 Javadoc에 적었다. 삭제 여부는 #24의 8번에서 정한다.
  - 테스트 `GroupByKeyTest`(4개). 옛 구현으로 돌려 null 값·`toListMap` 닫기 테스트가 실패함을 확인했다(`groupByKey`는 원래 `collect` 기반이라 닫기는 문제가 없었다).

### #11 `KeyedGroups`가 내부 맵을 그대로 노출 — 낮음~중간

- 위치: [KeyedGroups.java](../../src/main/java/utils/stream/KeyedGroups.java)
- `asMap()`이 내부 `HashMap`을 그대로 돌려주고, `get()`은 가변 리스트를 돌려준다.
- 재현(실험):
  - `asMap().put("z", null)` 뒤에 `size()`를 부르면 NPE가 난다.
  - `asMap().put("e", [])`를 하면 빈 그룹이 생긴다.
- 키 순서는 `HashMap` 순서라 스트림에 나온 순서와 다르다.
- **제안:**
  - `asMap()`과 `get()`은 수정 불가 뷰를 돌려준다.
  - 내부를 `LinkedHashMap`으로 바꿔 처음 나온 순서를 유지한다.
  - `size()`의 의미(전체 값 개수)를 Javadoc에 적는다.
- 결과: 완료(2026-11-02).
  - `asMap()`은 수정 불가 라이브 뷰(`unmodifiableMap(Maps.transformValues(..., unmodifiableList))`)를, `keySet()`·`get()`·`getOrDefault()`도 수정 불가 뷰를 돌려준다. 내부 상태는 `add`/`remove`로만 바뀐다.
  - 내부를 `LinkedHashMap`으로 바꿔 그룹이 처음 나온 순서를 유지한다(`fstream()`·`ungroup()`·`keySet()`도 이 순서).
  - `size()`(전체 값 개수)와 `groupCount()`(키 수)의 의미를 Javadoc에 적었다. 클래스와 모든 public 메소드에 Javadoc을 붙였다.
  - **`fstream()`의 값 목록은 내부 목록 그대로 둔다.** marmot `InMemoryKeyedRecordSetFactory.sortValues`가 이 목록을 제자리에서 정렬하기 때문이다. 이 점을 Javadoc에 주의 사항으로 적었다.
  - 사용처 확인: marmot의 `asMap()` 사용(조인 3곳)과 `get`·`getOrDefault`·`keySet` 사용(`PointerPartition`, 샘플), mdt의 `fstream()`·`switcher()` 사용은 모두 읽기만 한다.
  - 테스트 `KeyedGroupsTest`(5개: 읽기 전용 뷰, 라이브 뷰, 순서, 없는 키, `fstream()` 제자리 수정).

### #12 기본형 스트림: `of` 오버로드 모호, `FloatFStream.sum` 정밀도, 기능 불균형 — 중간

- `LongFStream.of(long...)`와 `of(Long...)`가 함께 있다. Float·Double도 같다.
  - 재현(실험): `LongFStream.of(1L, 2L)`, `FloatFStream.of(1f, 2f)`, `DoubleFStream.of(1d, 2d)`는 모두 "ambiguous" 컴파일 오류가 난다. 리터럴로는 만들 수 없다.
  - 제안: 박싱 버전을 `ofBoxed(...)` 또는 `from(Long[])`로 이름을 바꾸거나 지운다.
- `FloatFStream.sum()`은 `double`을 반환하지만 실제로는 `float`로 누적한다.
  - 재현(실험): 0.1f 10개의 합이 1.0000001192다.
  - 제안: `double`로 누적한다.
- 기능 불균형:
  - `maxValue`/`minValue`는 Int에만 있다.
  - `from(List)`/`downcast`는 Float·Double에만 있다.
  - `range`는 Int(`FStream.range`)와 Long에만 있다.
  - 원소에 null이 있으면 `sum`에서 NPE가 난다.
  - `IntFStream`은 Guava와 `utils.Preconditions`를 섞어 쓴다.
- 결과: 완료(2026-11-02). Int·Long·Float·Double 네 스트림을 같은 구조로 다시 썼다.
  - 모호성: 박싱 버전 `of(Long...)`·`of(Float...)`·`of(Double...)`를 `from(Long[])` 등으로 이름을 바꿨다(별칭 없음, #24의 19번). `from(List)`과 짝을 맞춘 이름이다. 이제 `LongFStream.of(1L, 2L)` 등 리터럴 호출이 컴파일된다. marmot 사용처 6곳(`marmot.core` `Functions` 4곳, `marmot.common` `PBValueProtos` 2곳)을 `from(...)`으로 고쳤다(marmot.core `16fec19`, marmot.common `7b1056a`로 커밋. 빌드는 확인하지 못함 — 단순 이름 변경).
  - `FloatFStream.sum()`은 `double`로 누적한다.
  - 기능 균형: 네 타입 모두 `of(prim...)`, `from(Boxed[])`, `from(Iterable)`(전에는 Float·Double의 `from(List)`만), `downcast`, `maxValue`/`minValue`, `sum`, `average`, `toArray`를 갖는다. `range`는 Int(`IntFStream.range` 추가, `FStream.range`와 같음)와 Long. 내부 어댑터 이름을 `FStreamAdaptor`로 통일했다.
  - null 원소: 처리하지 않고 수치 연산이 NPE를 던진다고 Javadoc에 적었다.
  - `IntFStream`의 `utils.Preconditions` 혼용(정규화 이름 호출)을 정리했다. 클래스 Javadoc의 "box 비용 없이"는 사실과 달라 고쳤다(원소는 박싱된다).
  - 범위 밖: `BooleanFStream`은 손대지 않았다.
  - 테스트 `PrimitiveFStreamTest`(8개).

### #13 제네릭 변성(`? super`/`? extends`) 누락으로 흔한 호출이 컴파일되지 않음 — 중간

모두 javac로 컴파일 실패를 확인했다. 시그니처를 넓히는 것이므로 기존 호출은 그대로 컴파일된다(오버로드 해석이 바뀌는지만 확인).

| 위치 | 컴파일되지 않는 호출 | 수정안 |
|---|---|---|
| `flatMap`, `flatMapIterable`, `flatMapArray`, `flatMapTry` | `FStream<Number> r = ints.flatMap(i -> FStream<Integer>)` | `? extends V` |
| `concatWith(FStream<T>)`, `concatWith(Iterable<T>)` | `FStream<Number>.concatWith(FStream<Integer>)` | `? extends T` |
| `startsWith(FStream<T>)` | `FStream<Number>.startsWith(FStream<Integer>)` | `FStream<? extends T>` |
| `distinct(Function<T,K>)`, `maxMultiple`/`minMultiple(Function<T,K>)` | 매개변수가 `Object`인 keyer | `? super T` (같은 이름의 `max(Function<? super T,…>)`와 맞춤) |
| `lift(Function<FStream<T>,FStream<S>>)` | 와일드카드 함수 | `Function<? super FStream<T>, ? extends FStream<S>>` |
| `lookup(Function<T,V>, …)`, `lookup(Map<T,V>, …)` | `Function<Object,String>` | `Function<? super T,? extends V>`. null 검사·Javadoc도 추가 |
| `mapToKeyValue(Function<T,KeyValue<K,V>>)` | `Function<Object,KeyValue<..>>` (같은 일을 하는 `toKeyValueStream`은 통과) | `? super T` (#19와 함께) |
| `KeyValueFStream.match(Map<K,R>)`, `flatMatch` | `KVFS<String,..>.match(Map<CharSequence,..>)`. mdt `ElementCollectionValue`가 `(Map<String,Object>)` 캐스트로 우회 중 | `Map<? super K, ? extends R>` |
| `KeyValueFStream.from(Iterable<KeyValue<K,V>>)`, `from(FStream<…>)` | `KVFS<String,Number> x = from(List<KeyValue<String,Integer>>)` | `? extends KeyValue<? extends K, ? extends V>` |
| `toMap(M extends Map<K,V>)` | `toMap(new HashMap<CharSequence,Integer>())` | `M extends Map<? super K, ? super V>` |
| `liftKeyValues(Function<KVFS<K,V>,KVFS<K2,V2>>)` | 와일드카드 함수 | 변성 추가, 또는 삭제(#24) |
| `toArray(Class<S>)` | `FStream<Integer>.toArray(String.class)`가 컴파일되고 런타임에 `ArrayStoreException` | `Class<? super T>`로 제한 |

- 결과: 완료(2026-11-02). 표의 수정안대로 넓혔다(`toArray` 제외).
  - `FStream`: `flatMap`(`? extends FStream<? extends V>`), `flatMapIterable`, `flatMapArray`, `flatMapTry`, `concatWith(FStream|Iterable<? extends T>)`, `startsWith(FStream<? extends T>)`, `distinct`/`maxMultiple`/`minMultiple`의 keyer(`? super T`), `lift`, `lookup` 세 오버로드(+ null 검사, Javadoc은 #8에서), `mapToKeyValue`.
  - `KeyValueFStream`: `from(FStream|Iterable<? extends KeyValue<? extends K,? extends V>>)`, `match`·`flatMatch`(`Map<? super K, ...>`), `toMap(M extends Map<? super K,? super V>)`, `liftKeyValues`. 내부 `MatchKVFStream`·`FlatMapTry`도 맞췄다.
  - 내부 구현에 넘길 때는 private `narrow(...)`로 `FStream<? extends T>`를 `FStream<T>`로 본다(스트림은 생산자 전용, `KeyValue`는 불변이라 안전).
  - `toArray(Class<S>)`: 제안("`Class<? super T>`로 제한")은 할 수 없다. Java는 메소드 타입 매개변수의 하한(`S super T`)을 표현하지 못하고, `T[]`를 돌려주면 상위 타입 배열을 `T[]`로 속이게 된다. Javadoc에 제약과 `ArrayStoreException`을 적는 것으로 대신했다.
  - 재정의 확인: 하위 인터페이스·MDT·marmot에서 이 메소드들을 재정의한 곳은 없다. 넓히기만 했으므로 기존 호출은 그대로 컴파일된다 — utils main/test, MDT(mdt-client·mdt-instance·mdt-cli·mdt-manager·mdt-workflow·mdt-operation-http) 컴파일로 확인했다. mdt `ElementCollectionValue`의 `(Map<String,Object>)` 캐스트 우회는 이제 필요 없지만 그대로 두었다.
  - 테스트 `VarianceTest`(5개): 표의 "컴파일되지 않는 호출"을 그대로 담았다(컴파일되는 것이 검증).

### #14 `Function`/`BiFunction` 오버로드가 메소드 참조에서 모호함 — 낮음~중간

- `KeyValueFStream`의 `mapKey`·`mapValue`·`map`·`forEach`는 인자 1개(`Function`/`Consumer`) 버전과 2개(`BiFunction`/`BiConsumer`) 버전이 모두 있다.
- 람다나 `String::length` 같은 단일 시그니처 메소드 참조는 문제없다. 그러나 오버로드가 있는 메소드 참조는 모호하다는 컴파일 오류가 난다.
  - 재현(실험): `ss.mapKey(Objects::toString)`, `ss.mapValue(Objects::toString)`, `ss.forEach(Objects::requireNonNull)`.
- `FStream.mapAsync`도 비슷하다. 인자 1개 버전은 `CheckedFunction`, 2개 버전은 `Function`을 받는다. 그래서 `Function<Integer,Integer> fn; s.mapAsync(fn)`가 컴파일되지 않는다.
- **제안:**
  - `mapKey(BiFunction)` → `mapKeyWithValue`, `mapValue(BiFunction)` → `mapValueWithKey`. 사용처는 9곳 이하다.
  - `mapAsync`의 정리는 #20에서 함께 한다.
- 결과: 완료(2026-11-02). 제안대로 `KeyValueFStream.mapKey(BiFunction)` → `mapKeyWithValue`, `mapValue(BiFunction)` → `mapValueWithKey`(별칭 없음). `mapKey(Objects::toString)`·`mapValue(Objects::toString)`이 컴파일된다.
  - 사용처(컴파일로 찾음): utils `CIStringMap` 1, mdt-client `Ros2SerDes` 4, mdt-cli `RESTfulAsyncRpcCommand` 1, marmot 4(`LoadJdbcTable`, `JdbcRecordAdaptor` 2곳, `InMemoryKeyedRecordSetFactory`). 키(또는 값)를 쓰지 않는 람다 9곳은 인자 하나짜리 `mapKey`/`mapValue`로, 키를 쓰는 `InMemoryKeyedRecordSetFactory`의 `this::sortValues`만 `mapValueWithKey`로 바꿨다. MDT 전 프로젝트 컴파일 확인. marmot은 grep(메소드 참조 포함)으로 찾았고 빌드는 확인하지 못했다. 커밋: mdt-client `aaf321e`, mdt-cli `b830ebe`, marmot `474c59c`, marmot.common `7b1056a`, marmot.data `9fc5f29`.
  - 범위 밖: `map`·`forEach`의 `BiFunction`/`BiConsumer` 오버로드는 그대로다(`forEach(Objects::requireNonNull)`는 여전히 모호). `utils.KeyValue`의 `mapKey`/`mapValue(BiFunction)` 오버로드도 같은 문제가 있으나 손대지 않았다. `mapAsync`는 #20에서.
  - 테스트 `KeyValueMapOverloadTest`(2개).

### #15 인자 검증·오류 메시지 문제 — 낮음

- `buffer(count, skip)`의 검사는 `count >= 0`인데 `BufferedStream`은 `count > 0`을 요구한다. 그래서 `buffer(0, 1)`은 내부 클래스의 메시지로 실패한다(실험).
- `utils.Preconditions`는 `%s`만 지원하는데 `{}`나 `%d`를 쓴 곳이 있어 메시지가 깨진다. 예: `"count >= 0, but {} [-1]"`(실험). 위치는 `take`, `drop`, `dropLast`, `takeLast`, `repeat`.
- 인자를 생성 시점에 검사하지 않아, 첫 `next()`나 원소가 생길 때에야 NPE가 나는 곳이 있다.
  - `match(null)`, `lookup(null)`, `foldByKey`, `collectByKey`의 null 인자.
  - 빈 스트림에서는 검사 없이 통과한다.
- `mapKey`가 null 키를 내면 IAE("key is null")가 나지만, `KeyValue.mapKey`는 NPE를 던진다.
- 결과: 완료(2026-11-02).
  - `buffer(count, skip)`는 공개 메소드에서 `count > 0`을 검사한다(`BufferedStream`과 같은 조건, 메시지 `count > 0, but: 0`). 사용처 중 `count = 0`은 이를 거부하는 기존 테스트뿐이다.
  - 메시지 자리표시자: `{}`·`%d`를 `%s`로 고쳤다(`take`, `drop`, `dropLast`, `takeLast`, `repeat`, 그리고 #12에서 제가 새로 쓴 `IntFStream`·`LongFStream`의 `RangedStream`).
  - 호출 시점 검사: `match(null)`, `foldByKey`·`collectByKey`의 `null` 인자를 바로 IAE로 거부한다(빈 스트림에서도). `lookup`은 #13에서, `flatMatch`는 #9에서 이미 넣었다. `reduceByKey`는 원래 검사하고 있었다.
  - `null` 키: 스트림의 `mapKey`·`mapKeyWithValue`가 `KeyValue.mapKey`에 위임해, `null` 키를 내면 같은 `NullPointerException("mapped key is null")`이 난다(전에는 IAE "key is null"). `KeyValue.mapKey`의 NPE는 이미 Javadoc·테스트로 정해진 계약이라 그쪽에 맞췄다.
  - 테스트 `ArgumentCheckTest`(5개). 참고: 전체 실행 중 `MapAsyncTest.test0`이 1ms 차이로 한 번 실패했다. 이번 변경과 무관한 타이밍 의존 테스트로 보이며, 세 번 다시 돌려 모두 통과했다.

### #16 `reduceByKey`: reducer가 null을 내면 누적이 다시 시작됨 — 낮음 — 완료

- 위치: [FStream.java](../../src/main/java/utils/stream/FStream.java) `reduceByKey`(`Map.compute` 사용)
- `compute`는 null을 반환하면 키를 지운다. 그래서 그다음 원소부터 누적이 처음부터 다시 시작되고, 예외 없이 틀린 값이 나온다.
- 재현(실험): 첫 reduce가 null을 내도록 하고 `("a","a","a")`를 넣으면 결과가 `{a=a}`다.
- **제안:** reducer의 null 결과를 IAE로 거부하거나, Javadoc에 계약을 적는다.
- 결과: (2026-11-02) IAE로 거부한다(`"reducer returned null for key: <k>"`). Javadoc에 계약과 이전 동작을 적었고
  README 표에 한 줄 더했다. `ReduceByKeyTest.reducerReturningNullIsRejected` 추가. 남은 점: `null` 원소가 키의 첫
  원소이면 여전히 키가 저장되지 않는다(`compute`가 `null`을 받음). `foldByKey`의 `folder`가 `null`을 내도 같은
  방식으로 누적이 다시 시작된다. 두 가지 모두 이번에는 고치지 않았다.

### #17 작은 결함 모음 — 낮음 — 완료

- `takeLast(count)`: `ArrayList.remove(0)` 때문에 O(N·count)다. `ArrayDeque`로 바꾼다.
- `ofExactClass`: null 원소에서 NPE가 난다(`castSafely`는 null을 걸러낸다, 실험).
- `split`: 빈 입력에서 `[[]]`를 낸다(실험). 의도를 정하고 문서에 적는다.
- `join(CSV)`: null 원소에서 NPE가 난다. `join(String)`은 `"null"`을 출력한다.
- `toMap(map)`: `ConcurrentHashMap` 등 null 값을 받지 않는 맵에 null 값이 있으면 NPE가 난다. Javadoc에 한 줄 적는다.
- 결과: (2026-11-02, 완료)
  - `takeLast`: `ArrayDeque`는 `null`을 받지 않으므로 대신 `ArrayList` 순환 버퍼(`set(head)` 후 `Collections.rotate`)로
    바꿨다. 원소당 O(1)이고 `null` 원소도 유지한다. 함께 고친 것: `count == Integer.MAX_VALUE`이면
    `new ArrayList<>(count + 1)`이 넘쳐 IAE가 났다.
  - `ofExactClass`: `null` 원소를 `castSafely`처럼 걸러낸다.
  - 테스트: `TakeLastTest`에 3개 추가(여러 바퀴 돈 뒤 순서와 `null` 유지, 큰 `count`, `ofExactClass`의 `null`).
  - `split`: 빈 입력은 빈 스트림을 낸다. 구분자 주변의 빈 구간은 그대로 둔다(`[d, a, d]` → `[], [a], []`).
    Javadoc과 README에 적었다.
  - `join(CSV)`: `String::valueOf`를 써서 `null` 원소를 `join(String)`처럼 `"null"`로 쓴다. `csv` 인자의 `null`
    검사도 더했다.
  - `toMap(map)`: Javadoc에 적었다. `null`을 받지 않는 맵에 `null` 키나 값을 넣으면 그 맵의 NPE가 나고, 앞서 넣은
    원소는 맵에 남는다. 동작은 바꾸지 않았다.
  - 테스트: `SplitJoinTest`(4개). 처음 구현에서 구분자가 없는 입력이 빈 스트림이 되었는데, 기존
    `SplitFStreamTest.test01`이 이를 잡아 고쳤다.

### #18 Javadoc과 코드 불일치 — 낮음 — 완료

- `FStream` 클래스 Javadoc의 "box 비용 없는 변환": `mapToInt` 등은 `MappedStream<T,Integer>`이고 매퍼도 `Function<?,Integer>`라서 박싱이 일어난다.
- `repeat(value, count)` Javadoc은 "무한 스트림"이라고 하지만 실제로는 유한하다.
- `FloatFStream.sum()`의 "double 정밀도"는 #12에서 고치면 맞게 된다.
- #2, #4, #9의 Javadoc은 각 항목에서 함께 고친다.
- 결과: (2026-11-02, 완료)
  - 클래스 Javadoc의 "box 비용 없는 변환"을 "수치 종단 연산을 더한 스트림으로 변환한다. 원소는 박싱된 값이므로
    박싱 비용이 있다"로 고쳤다. README와 CLAUDE.md에는 이미 박싱한다고 적혀 있었다.
  - `repeat(value, count)`: "무한" → "유한"으로 고쳤다. `count`가 0이면 빈 스트림이라는 점과 `@throws`도 더했다.
  - `FloatFStream.sum()`, #2, #4, #9: 각 항목에서 이미 고쳤다(새로 바꾼 것 없음).

### #26 `keepOrder=true`인 `flatMapAsync`를 중간에 닫으면 만들어 둔 하위 스트림이 닫히지 않음 — 중간 — 완료

- 위치: [FStream.java](../../src/main/java/utils/stream/FStream.java) `flatMapAsync`·`flatMapCheckedAsync`의 `keepOrder=true` 경로, [OrderedMapAsyncStream.java](../../src/main/java/utils/stream/OrderedMapAsyncStream.java) `closeInGuard`
- 2026-11-02 #7·Javadoc 작업 중 코드를 읽다가 발견했다(테스트로 재현하지는 않음).
- `keepOrder=true`이면 `mapAsync(mapper, options).flatMap(t -> t._2().get())`로 동작한다. `mapAsync`는 소비보다 앞서 최대 `workerCount`개의 mapper를 실행하므로, 하위 스트림이 미리 만들어져 출력 채널이나 진행 중인 실행에 쌓인다.
- 결과 스트림을 중간에 닫으면 `OrderedMapAsyncStream.closeInGuard`는 출력 채널을 닫고(쌓인 결과를 버림) 입력을 닫을 뿐이다. 버려진 결과의 하위 스트림은 닫히지 않는다. 진행 중인 mapper가 닫은 뒤에 만든 하위 스트림도 버려진다.
- 하위 스트림이 파일·커넥션 같은 자원을 쥐면 샌다. `keepOrder=false`(`mergeParallel`)는 하위 스트림을 워커가 끝까지 읽거나(`forEachOrThrow`가 닫음) 아직 만들지 않으므로 이 문제가 없다.
- 지금은 Javadoc과 README 8.4에 주의 사항으로만 적었다.
- **제안:** 순서 보존 경로에서 버려지는 결과를 정리한다. 예를 들어 `flatMapAsync`의 `keepOrder=true` 경로에서 하위 스트림 결과를 담은 실행에 "닫힌 뒤 도착하거나 버려지면 `close`" 처리를 붙이거나, `OrderedMapAsyncStream`에 버린 결과를 처리하는 훅(`onDiscarded`)을 두어 flatMap 경로가 `FStream::closeQuietly`를 넘긴다. 먼저 재현 테스트를 만든다.
- 결과: (2026-11-02, 완료) 두 번째 안(`onDiscarded` 훅)으로 처리했다.
  - 먼저 `FlatMapAsyncCloseTest` 2개로 재현했다. 기존 코드에서는 둘 다 하위 스트림 5개 중 1개만 닫혔다.
    - 끝났지만 읽지 않은 결과
    - 닫을 때 진행 중이던 mapper의 결과
  - `OrderedMapAsyncStream`
    - 시작했지만 넘기지 않은 실행을 `m_undelivered`에 둔다.
    - 닫을 때 그 실행마다 `whenFinishedAsync`로 성공 결과를 훅에 넘긴다. 이미 끝난 실행은 바로, 진행 중인
      실행은 끝날 때 넘긴다.
    - 닫은 뒤에는 새 매핑을 시작하지 않는다.
    - 다른 쓰레드가 닫는 동안 `next()`가 결과를 기다리고 있었다면 그 결과를 넘기지 않고 빈 값을 낸다. 같은 결과를
      훅이 정리하므로, 넘기면 소비자가 닫힌 스트림을 받게 된다.
    - 훅의 예외는 로그만 남긴다.
  - `flatMapAsync`·`flatMapCheckedAsync`의 `keepOrder=true` 경로는 `OrderedMapAsyncStream`을 직접 만들어
    `FStream::closeQuietly`를 넘긴다. `mapAsync`는 훅 없이 그대로다.
  - 남은 점: 정리는 비동기이므로 `close()` 직후에는 일부 하위 스트림이 아직 닫히지 않았을 수 있다.
  - Javadoc, README 8.4, CLAUDE.md를 갱신했다.

---

## 설계

### #19 key-value 변환 진입점이 다섯 개로 중복

| 진입점 | 하는 일 | 사용처 |
|---|---|---|
| `tagKey(keyer)` | `(keyer(t), t)` | utils 1, mdt 28, marmot 9 |
| `toKeyValueStream(mapper)` | `mapper(t)`가 `KeyValue`. 반환 타입이 `KeyValue<K,V>` 그대로라 `KeyValue` 하위 클래스를 내는 매퍼를 받지 못함(실험) | utils 본체 4, 테스트 2, marmot 13 |
| `mapToKeyValue(mapper)` | 위와 같음. `? extends KeyValue<K,V>` | utils 테스트 1, mdt 15 |
| `toKeyValueStream(keyer, valuer)` | `(keyer(t), valuer(t))` | utils 1, utils.grpc 1, mdt 1, marmot 8 |
| `KeyValueFStream.from(FStream<KeyValue>)` | 감싸기. 타입을 되찾는 캐스트 용도로도 쓰임 | 다수 |

- **제안:**
  - `mapToInt`·`mapToLong`과 같은 `mapToX` 체계로 통일한다. `mapToKeyValue(mapper)`(`? super T`)와 `mapToKeyValue(keyer, valuer)`만 남긴다.
  - 1인자 `toKeyValueStream`은 지운다(호출처 약 8곳 수정).
  - `tagKey`는 사용처가 많으니 유지하는 것을 권장한다(#24 참고).
- 재확인(2026-11-02):
  - 사용처는 marmot까지 다시 셌다(위 표). 처음 수는 utils·utilsx·mdt만 센 것이었다. mdt의
    `toKeyValueStream(mapper)` 1곳(`ProcessBasedMDTOperation`)은 그사이 `mapToKeyValueOrThrow`로 바뀌어 0이다.
  - 변성이 좁은 쪽은 `mapToKeyValue`가 아니라 `toKeyValueStream(mapper)`다.
  - 중복은 두 쌍이다. `toKeyValueStream(mapper)` ≡ `mapToKeyValue(mapper)`, 그리고 `toKeyValueStream(keyer, v -> v)` ≡
    `tagKey(keyer)`(예: utils `TableBindings`). utils 본체 4곳 중 `KeyedGroups.ungroup()`의 `toKeyValueStream(v -> v)`는
    타입만 되찾는 감싸기라 `KeyValueFStream.from`이면 된다.
  - marmot을 여기서 빌드할 수 없으므로 단계적으로 진행한다.
    1. `mapToKeyValue(keyer, valuer)` 추가
    2. `toKeyValueStream` 두 개를 `@Deprecated`로 두고 `mapToKeyValue`에 위임
    3. utils·utils.grpc·mdt 9곳 이전
    4. marmot 21곳 이전 후 삭제
- 결과:
  - 1단계 완료(2026-11-02, 미커밋): `mapToKeyValue(keyer, valuer)`를 추가했다(`? super T`/`? extends K`·`V`).
    테스트 `MapToKeyValueTest`(3개). README 6절 예시를 `mapToKeyValue`로 바꾸고 CLAUDE.md에 진입점 정리를 적었다.
  - 2단계 완료(2026-11-02, 미커밋): `toKeyValueStream(mapper)`·`toKeyValueStream(keyer, valuer)`를 `@Deprecated`로
    표시하고 `mapToKeyValue(mapper)`·`mapToKeyValue(keyer, valuer)`에 위임했다. 시그니처는 그대로 두었으므로
    `toKeyValueStream(mapper)`는 여전히 `KeyValue` 하위 클래스를 내는 매퍼를 받지 못한다(지울 메소드라 넓히지 않음).
    남은 사용처에서는 deprecation 경고만 난다.
  - 3·4단계 대신 바로 삭제(2026-11-02, 미커밋, 사용자 결정): `toKeyValueStream` 두 개를 별칭 없이 지웠다.
    - utils 본체 5곳과 mdt-instance 1곳은 사용자가 먼저 `mapToKeyValue`·`mapToKeyValueOrThrow`로 옮겨 두었다.
    - 나머지는 `mapToKeyValue`가 인자 1·2개 버전을 모두 가지므로 이름만 바꿨다: utils 테스트 2, utils.grpc `PBUtils` 1,
      marmot 21(marmot 5, marmot.common 6, marmot.data 2, marmot.sample 4, marmot.spark2 4, marmot.core 1 — 처음
      표의 수에 marmot.data의 `utils.grpc.PBUtils` 사본이 더해졌다), jarvey 7(jarvey 1, jarvey.streams 6 — 검토 때
      세지 않은 프로젝트; 1곳은 주석).
    - 확인: utils stream 테스트 통과, MDT 5개 프로젝트 컴파일. utils.grpc는 이번 변경과 무관한 미커밋 파일
      3개(`UnitUtils.parseDurationMillis` 없음) 때문에 컴파일되지 않지만 `PBUtils`에는 오류가 없다. marmot·jarvey는
      여기서 빌드할 수 없다.

### #20 실패 처리 연산의 이름 체계가 제각각

| 동작 | 현재 이름 |
|---|---|
| 실패를 무시 | `mapOrIgnore`, `forEachOrIgnore` |
| `Try`로 감싸기 | `tryMap`(결과는 `Try`), `mapAsync`·`mapCheckedAsync`(결과는 `Tuple<T,Try>`) |
| 전파 | `mapOrThrow`(sneaky, #6), `KeyValueFStream.mapOrThrow(CheckedBiFunctionX)`(sneaky, 2026-11-02 추가), `forEachOrThrow`(선언된 전파), `mapToKeyValueOrThrow`, `flatMapAsync`·`flatMapCheckedAsync`(sneaky, #7) |

- "Checked"가 들어간 `mapCheckedAsync`는 이름과 달리 실패를 전파하지 않고 `Try`로 감싼다.
- `mapAsync`는 인자 1개 버전이 `CheckedFunction`, 2개 버전이 `Function`을 받는다(#14).
- 같은 "Checked"라도 `flatMapCheckedAsync`는 #7 이후 실패를 전파하므로 `mapCheckedAsync`(`Try`로 감쌈)와 규칙이 다르다.
- `KeyValueFStream.mapOrThrow(CheckedBiFunctionX)`는 상속한 `mapOrThrow(CheckedFunctionX)`와 오버로드다. 오버로드가
  있는 메소드 참조는 #14처럼 모호할 수 있다(Javadoc에 적음). 이름 체계를 정할 때 함께 본다.
- **제안(결정 필요):** 접미사 규칙을 하나로 정한다.
  - `OrIgnore` = 실패한 원소는 건너뜀
  - `OrThrow` = 선언된 예외로 전파
  - `ToTry` = `Try`로 감쌈
  - 비동기 버전도 같은 규칙을 따른다. 예: `mapAsync(Function)`, `mapCheckedAsync(CheckedFunction)`, 인자 1·2개 모두 제공.
  - 구체적인 이름은 #24 표를 참고한다.
- 결과:

### #21 `KeyValueFStream`의 중간 연산 대부분이 `FStream<KeyValue>`로 타입을 잃음

- `peek`만 `KeyValueFStream`을 반환하도록 재정의되어 있다. `filter`, `take`, `drop`, `sort`, `distinct`, `mapOrThrow` 등은 `FStream<KeyValue<K,V>>`를 반환하므로, 다시 `KeyValueFStream.from(...)`으로 감싸야 한다.
- 재현(실험): `KeyValueFStream r = s.filter(..)`가 컴파일되지 않는다.
- **제안:** 자주 쓰는 연산을 재정의해 `KeyValueFStream`을 반환한다. `filter(Predicate)`, `filter(BiPredicate)`, `take`/`drop`, `sortByKey(Comparator)` 정도.
- 결과:

### #22 빠진 연산

- `KeyValueFStream.reduceByKey(BinaryOperator<V>)`가 없다. 지금은 `FStream` 쪽의 keyer를 받는 버전뿐이다.
- 중복 키를 다루는 `toMap`이 없다. merge 함수를 받는 `toMap(merger)`, 또는 중복 키면 실패하는 `toUniqueMap()`. 지금의 `toMap`은 나중 값이 이긴다.
- `swap()`(키·값 교환)이 없다.
- 순서를 보존하는 수집이 없다. `toMap(new LinkedHashMap<>())`으로 대신할 수 있으므로 우선순위는 낮다.
- **제안:** 실제 사용처가 생길 때 추가한다. `reduceByKey`와 `toMap(merger)`는 지금 넣어도 좋다.
- 결과:

### #23 반환 타입: `Optional`과 구체 컬렉션 클래스

- `max`/`min`은 `java.util.Optional`을 반환하고, 나머지 API는 `FOption`을 반환한다. CLAUDE.md에 예외로 적혀 있다. FStream 쪽 호출은 4~6곳이라 `FOption`으로 바꾸는 비용이 작다.
- `toList()`/`toSet()`은 `ArrayList`/`HashSet`을 반환한다. 반환값을 구체 클래스 변수에 대입하는 호출처는 0곳이라 `List`/`Set`으로 바꿔도 비용이 거의 없다.
- `toMap()`/`toListMap()`은 `HashMap`을 반환한다. `Map`으로 바꾸려면 `HashMap`으로 받는 호출처를 확인해야 한다.
- 결과:

## 이름 변경 제안 (#24)

| # | 현재 → 제안 | 이유 | 사용처 | 권장 |
|---|---|---|---|---|
| 1 | `reduceLeak` → `scan` (또는 `runningReduce`) | Scala/Kotlin의 표준 이름. "Leak"은 자원 누수로 읽힌다 | 본체 0, 테스트 3 | 변경 |
| 2 | `foldLeak` → 삭제, 또는 고쳐서 `scan(init, f)` | #3 | 0 | **완료**: `scan(init, f)`로 다시 구현 |
| 3 | `toKeyValueStream(mapper)` → 삭제하고 `mapToKeyValue`로 통일 | #19 | 약 8 | **완료**: 삭제 |
| 4 | `toKeyValueStream(keyer, valuer)` → `mapToKeyValue(keyer, valuer)` | `mapToX` 체계 | 3 | **완료**: 삭제 |
| 5 | `mapKey(BiFunction)` → `mapKeyWithValue`, `mapValue(BiFunction)` → `mapValueWithKey` | #14 모호성 제거 | 9 이하 | 변경 |
| 6 | `liftKeyValues` → 삭제 | 사용처 0, `lift`와 이름만 다름 | 0 | 변경 |
| 7 | `asynchronouslyFrom` → `generate(gen, len, threadName)` 오버로드 | `generate`와 같은 기능의 이름 중복 | 0 | 변경 |
| 8 | `toListMap` → 삭제하고 `groupByKey()` 사용 | #10 중복 | mdt 1 | 변경 |
| 9 | `tryMap` → `mapToTry` | `flatMapTry`·`mapToX`와 어순을 맞춤 | 테스트 3 | 변경 |
| 10 | `castSafely` → `ofType` (또는 `filterInstanceOf`) | 하는 일은 걸러내기+캐스트. "Safely"가 모호함 | mdt 15 | 결정 필요 |
| 11 | `ofExactClass` → `ofExactType` | 10번과 짝 | 0 | 10번을 따름 |
| 12 | `lift` → `transform` | 실제로는 `f.apply(this)`일 뿐 FP의 lift가 아님 | 0 (README 예시 1) | 변경 |
| 13 | `flatMapNullable` → `mapNotNull` | Kotlin 이름. flatMap이 아님 | 5 | 결정 필요 |
| 14 | `lookup(...)` → 이름 유지, 의미만 #8로 통일 | 종단 검색처럼 읽히지만 사용처가 적어 바꿀 이득이 작음 | 3 | 유지 |
| 15 | `mapOrThrow`·`mapToKeyValueOrThrow` → `mapSneaky` 등 | #6 (a)안을 택할 경우 | 23+3 | 결정 필요 |
| 16 | `tagKey` → `keyBy` | Spark `keyBy`와 같은 뜻 | 29 | 보류 권장 |
| 17 | `max`/`min` 반환 `Optional` → `FOption` | #23 | 4~6 | 변경 |
| 18 | `toList`/`toSet` 반환 → `List`/`Set` | #23 | 대입문 깨지는 곳 0 | 변경 |
| 19 | 기본형 `of(Long...)` 등 → `ofBoxed(...)` 또는 삭제 | #12 모호성 | marmot 6 | **완료**: `from(Boxed[])`로 변경 |

- 유지를 권장하는 이름: 널리 쓰이는 `forEach(BiConsumer)`, 그리고 `sortByKey`, `fromKeyed`, `foldByKey`, `collectByKey`. 뒤의 넷은 사용처가 0이라 시그니처를 자유롭게 고쳐도 된다.
- 결과:
  - 2번 완료(2026-11-02). `foldLeak(S, BiFunction<S,T,Tuple<S,T>>)`를 지우고 `<S> FStream<S> scan(S init, BiFunction<? super S,? super T,? extends S> folder)`를 추가했다. 결과 길이는 입력과 같고 초기값은 방출하지 않는다(`reduceLeak`과 같은 길이 규칙; Scala `scanLeft`·Kotlin `runningFold`와 다름 — Javadoc·README에 명시). 1번(`reduceLeak` → `scan(f)`)은 아직 하지 않았다.
  - 5번 완료(2026-11-02, #14): `mapKeyWithValue`·`mapValueWithKey`. 사용처 10곳을 고쳤다.
  - 19번 완료(2026-11-02, #12): 박싱 배열용 `of(Long...)` 등을 `from(Long[])` 등으로 바꿨다. marmot 6곳을 고쳤다.
  - 8번(`toListMap` 삭제)은 하지 않았다. #10에서 `toListMap`을 `collect`로 다시 구현해 남겨 두었다.
  - 11번(`ofExactClass`): 이름은 그대로이고, #17에서 `null` 원소를 걸러내도록만 고쳤다.

## 문서 (#25)

- 각 항목에서 동작이 바뀌면 [README.md](../../src/main/java/utils/stream/README.md)와 [CLAUDE.md](../../src/main/java/utils/stream/CLAUDE.md)를 함께 고친다. 특히 다음 부분이다.
  - CLAUDE.md 닫기 규약 4번(#2)
  - README 10.1·10.2절(#2, #5)
  - `match`/`lookup` 설명(#8, #9)
  - 이름 변경(#24)
- 마지막에 남은 불일치를 정리하고 이 문서를 지운다.
- 결과: (2026-11-02, 진행) #1–#18과 #26은 항목마다 README·CLAUDE.md를 함께 고쳤다. #19–#24를 마친 뒤 남은
  불일치를 정리한다.
