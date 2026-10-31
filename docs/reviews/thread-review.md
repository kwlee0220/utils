# `utils.thread` 검토 항목

- 대상: `src/main/java/utils/thread/` 전체(17개 파일, 약 3,600줄)와 관련 테스트(`src/test/java/utils/thread/`, `src/test/java/utils/async/TimerTest.java`)
- 기준 커밋: `6d9a8d9` (2026-10-31)
- 상태 값: `대기` · `진행` · `완료` · `보류`(결정 필요) · `기각`
- 항목을 처리하면 요약표의 상태를 바꾸고, 해당 항목의 **결과**에 커밋과 처리 내용을 적는다. 모든 항목이 끝나면 이 문서를 지운다.

## 사용처 (우선순위 판단 근거)

`common/`, `mdt/`, `marmot/`, `jarvey/`의 Java 소스를 검색한 결과다.

| 클래스 | utils 안 | utils 밖 | 비고 |
|---|---|---|---|
| `Guard` | 19개 파일 | 21개 파일 | 핵심 타입. `awaitCondition` 호출 74곳은 모두 람다·메소드 참조 |
| `Timer` | `Executions`, `EventDrivenExecution` | — | `Execution.setTimeout()`을 통해 utils·MDT 33곳이 간접 사용 |
| `CallerRunsSerialExecutor` | `StateChart` 등 | — | |
| `RecurringScheduleThread` / `RecurringSchedule` / `RecurringWork` | 패키지 내부 | **marmot** 3개 파일 | marmot은 utils를 소스로 포함해 빌드(2026-06 최근 커밋) |
| `ExecutorAware` | `AbstractAsyncExecution` | — | |
| `CompletionFStream` | 테스트만 | — | |
| `CamusExecutor` / `CamusExecutorImpl` / `RecurringScheduleImpl` | 패키지 내부만 | — | |
| `SingleWaiterExecutor`, `LengthOneOverridingQueue`, `InterThreadShareSupplier`, `AsyncConsumer`, `ConditionVariable`, `InterruptableWork` | 없음 또는 패키지 내부만 | — | `ConditionVariable`은 `InterThreadShareSupplier`만 사용 |

## 요약

| # | 분류 | 심각도 | 제목 | 상태 |
|---|------|--------|------|------|
| 1 | 버그 | 높음 | `InterThreadShareSupplier`: 대기 제한을 정하지 않으면(기본값) 진행 중인 생성을 기다리지 않음 | 완료 |
| 2 | 버그 | 높음 | `Timer`: 64개 상한에서 조용히 버리고, 끝난 실행이 자리를 계속 차지해 살아 있는 타임아웃이 사라짐 | 완료 |
| 3 | 버그 | 중간 | `Timer`: 타이머 lock을 쥔 채 `cancel()`·`isDone()` 호출 → 모든 타이머 지연, lock 순서 역전 | 완료 |
| 4 | 버그 | 높음 | `RecurringScheduleThread.waitForStopped(long)`: 작업 스레드에서 부르면 lock을 놓지 않고 예외 | 완료 |
| 5 | 버그 | 중간 | `RecurringScheduleThread.setLogger()` 조건 반전 | 완료 |
| 6 | 버그 | 중간 | `RecurringScheduleThread.getState()`가 `WORKING`을 돌려주지 않음 | 완료 |
| 7 | 버그 | 중간 | `RecurringScheduleThread.start()`: `onStarted` 실패를 예외로 알리지 않음 | 완료 |
| 8 | 버그 | 낮음 | `RecurringScheduleThread.stop()`: 시작 전에 부르면 NPE 또는 `STOPPING`에 영구 정지 | 완료 |
| 9 | 버그 | 높음(사용처 없음) | `CamusExecutorImpl` / `RecurringScheduleImpl`: 교착, 잘못된 대기, `ClassCastException` 등 다수 | 대기 |
| 10 | 버그 | 중간 | `SingleWaiterExecutor`: `start()`가 예외를 던지면 이후 작업이 영원히 대기 | 대기 |
| 11 | 버그 | 중간 | `Guard`: `InterruptedException`을 던지면서 인터럽트 플래그도 다시 설정 | 대기 |
| 12 | 버그 | 낮음 | `Guard`: `Date` 기한 대기가 시간 초과 직후 조건을 다시 확인하지 않음 | 대기 |
| 13 | 버그 | 낮음 | `Guard`: `*Checked` 종결 메소드가 `Error`(VME 포함)까지 `ExecutionException`으로 감쌈 | 대기 |
| 14 | 버그 | 낮음 | `CompletionFStream`: 실행 lock을 쥔 동기 종료 리스너 안에서 버퍼가 차면 블록 | 대기 |
| 15 | 정리 | — | 쓰이지 않는 클래스 정리(삭제 또는 유지 결정) | 보류 (결정 필요) |
| 16 | 정리 | 낮음 | `Guard`: 두 대기 빌더의 중복 구현, 의미 없는 `protected`, 일부 인자 검사 누락 | 대기 |
| 17 | 정리 | 낮음 | `ExecutorAware`의 위치(`utils.async`가 유일한 사용처) | 대기 |
| 18 | 인터페이스 | — | `Guard` 조건을 `Supplier<Boolean>` → `BooleanSupplier` | 대기 |
| 19 | 인터페이스 | — | `Timer`를 `ScheduledThreadPoolExecutor` 기반으로 다시 구현하고 실행이 끝나면 예약 취소 | 완료 |
| 20 | 인터페이스 | — | `RecurringSchedule`: `long` 밀리초 → `Duration`, 예외 계약 정리 | 대기 |
| 21 | 인터페이스 | — | `LengthOneOverridingQueue`: Java 관례와 다른 `poll()`(블록) | 대기 |
| 22 | 이름 | — | 클래스·메소드 이름 변경 | 대기 |
| 23 | 테스트 | — | 테스트 공백 | 대기 |
| 24 | 문서 | 낮음 | README·CLAUDE.md를 수정 결과에 맞추기 | 대기 |
| 25 | 버그 | 높음 | `RecurringScheduleThread`(외부 검토): lock 안 `interrupt()`, 초기화 중 `stop()` 교착, 주기 값 검사·넘침, `performNow()`의 지연 보정 왜곡 | 완료 |

권장 순서: 15(삭제 결정) → 2·3·19(`Timer`, 영향 범위가 가장 큼) → 11(`Guard`) → 4~8(marmot 사용) → 1·10·9(삭제하지 않기로 한 것만) → 나머지.
삭제하기로 한 클래스는 그 클래스의 버그 항목을 `기각`(삭제로 해소)으로 바꾼다.

---

## 버그

### #1 `InterThreadShareSupplier`: 기본 설정에서 기다리지 않음 — 높음

- 위치: (옛 `InterThreadShareSupplier.java` 88-104줄, 지금은 [CoalescingSupplier.java](../../src/main/java/utils/thread/CoalescingSupplier.java)로 다시 구현)
- 문제: 대기 분기가 `if ( m_waitTimeout != null ) { 시간 제한 대기 } ` 안에만 있다. `else` 쪽(시간 제한 대기가 성공한 경우)에서 다시 무제한 대기를 하는데, 정작 **`m_waitTimeout == null`(기본값, 무제한 대기)일 때는 아무 대기도 하지 않는다.** 그래서 다른 스레드가 생성 중이면:
  - 이전 생성 결과가 남아 있으면 그 **낡은 값**을 반환하고,
  - 없으면 `throw m_cause`를 실행하는데 첫 생성 전에는 `m_cause`가 `null`이라 `NullPointerException`이 난다.
- 함께 있는 문제:
  - 내부 supplier가 `null`을 정상 반환하면 `new ExecutionException(null)`로 실패 처리한다(옛 140줄). `null` 결과를 지원하지 않는다는 말이 문서에 없다.
  - 내부 supplier의 `InterruptedException`이 `ExecutionException`으로 감싸지면서 인터럽트 플래그가 사라진다.
  - 생성이 끝난 직후 다른 스레드가 새 생성을 시작하면, 기다리던 스레드는 앞 결과 대신 다음 생성까지 기다린다(세대 구분 없음).
  - 주석 "영상이 capturing 중임을 알려…"는 다른 프로젝트에서 옮겨 온 흔적이다.
- 제안: 사용처가 없으므로 #15에서 삭제를 먼저 검토한다. 유지한다면 `Guard`와 세대 번호(또는 생성마다 새 `CompletableFuture`)로 다시 구현한다. 생성마다 future를 만들면 대기, 시간 제한, 결과·예외 공유, `null` 결과가 모두 자연스럽게 해결된다.
- 결과: 완료(미커밋). 이름과 공개 API(생성자, `setWaitTimeout`, `get()`의 예외)는 유지하고 `Guard` + 생성마다 새 `CompletableFuture`로 다시 구현했다. 진행 중 표시(`m_inflight`)는 future를 완료하기 전에 지운다. 기본 설정에서도 기다리고, 대기자는 자신이 합류한 생성의 결과를 받으며, `null` 결과를 정상 결과로 공유한다. 생성하던 스레드가 인터럽트되면 그 호출은 `InterruptedException`, 대기자는 `ExecutionException`을 받는다. `setWaitTimeout`은 음수를 거부한다. 테스트 `InterThreadShareSupplierTest`(7개) 추가 — 옛 구현으로 돌리면 5개가 실패(대기 생략, `m_cause` NPE, `null` 결과 실패 등)함을 확인했다. README 6.3·7절 제약 표와 CLAUDE.md를 갱신했다. 이로써 `ConditionVariable`의 유일한 사용처가 없어졌다(#15). 외부 검토 반영(미커밋): 생성 함수의 `CancellationException`도 대기자에게 `ExecutionException`으로 전달, 재진입은 `IllegalStateException`으로 즉시 거부, `Duration` 나노초 범위 초과는 `Long.MAX_VALUE`로 제한. 공유 설정 `setWaitTimeout()`을 없애고 호출별 `get(Duration joinTimeout)`으로 바꿈(인자 없는 `get()`은 무제한, `TimeoutException` 없음). 생성자 인자 `CheckedSupplier<? extends T>`. 이름 `m_inFlightResult`/`sharedResult`/`produceAndPublish()`/`clearInFlightIfCurrent()`. 테스트는 `sleep` 대신 패키지 전용 `getWaitingCount()`로 대기자 합류를 확인하도록 다시 쓰고 12개로 늘림(취소, 재진입, 큰 `Duration`, 대기자·생성자 인터럽트, 실패 후 재생성, 동시 `null` 공유).

### #2 `Timer`: 살아 있는 타임아웃이 조용히 사라짐 — 높음

- 위치: [Timer.java:36-39](../../src/main/java/utils/thread/Timer.java#L36-L39), [Timer.java:80-93](../../src/main/java/utils/thread/Timer.java#L80-L93)
- 문제: 두 결함이 겹친다.
  1. 큐가 `MinMaxPriorityQueue.maximumSize(64)`라서 65번째 타이머가 들어오면 **가장 늦은 기한의 타이머를 알림 없이 버린다.** 그 실행은 영영 시간 초과로 취소되지 않는다.
  2. 이미 끝난 실행은 큐의 맨 앞에 왔을 때만 지운다. 예를 들어 1시간 제한을 걸고 1초 만에 끝난 실행은 1시간 동안 자리를 차지한다.
  
  둘이 겹치면, 끝난 실행들이 64칸을 채운 상태에서 새로 건 타임아웃(또는 기한이 긴 살아 있는 타임아웃)이 버려진다. `Execution.setTimeout()` 사용처가 33곳이라 "시간 초과로 끝나야 할 작업이 끝나지 않는" 문제로 나타난다.
- 제안: #19의 재구현으로 해결한다. 최소 수정이라면 크기 제한을 없애고, 실행이 끝날 때(`whenFinished`) 큐에서 지운다.
- 결과: 완료(미커밋). #19의 재구현으로 해결. 예약 수 제한이 없고, `exec.whenFinished(r -> future.cancel(false))`로 끝난 실행의 예약을 곧바로 해제한다(`setRemoveOnCancelPolicy(true)`라 큐에서도 바로 빠진다). 테스트 `more_than_64_pending_timers_all_fire`, `a_finished_execution_releases_its_timer`.

### #3 `Timer`: 타이머 lock 안에서 실행을 취소함 — 중간

- 위치: [Timer.java:82](../../src/main/java/utils/thread/Timer.java#L82), [Timer.java:85](../../src/main/java/utils/thread/Timer.java#L85)
- 문제: 스케줄러 스레드가 타이머 lock을 쥔 채 `exec.isDone()`과 `exec.cancel(true)`를 부른다.
  - `cancel()`은 오래 걸릴 수 있다. 예를 들어 `StateChart`는 차트 lock을 기다리고 상태 `exit()`를 실행하며, RCK 시뮬레이션은 중지 명령 전송을 최대 2초 기다린다. 그동안 모든 타이머의 `setTimer()`와 다른 타임아웃 처리가 멈춘다.
  - lock 순서가 뒤집힐 수 있다. 타이머 스레드는 타이머 lock → 실행 lock(`isDone`/`cancel`) 순서로 잡는다. 반대로 실행 lock을 쥔 스레드가 `setTimeout()`을 부르면(예: 동기 `whenStarted` 리스너 안) 실행 lock → 타이머 lock 순서가 되어 교착될 수 있다.
- 제안: 만료된 항목은 lock 안에서 큐에서 빼기만 하고, `cancel(true)`는 lock 밖에서(가능하면 별도 스레드에서) 부른다. #19의 재구현에서는 구조적으로 해결된다.
- 결과: 완료(미커밋). #19의 재구현으로 해결. 스케줄러 스레드는 시간만 재고 만료를 취소 전용 스레드 풀에 넘기며, `cancel(true)`는 그 풀에서 lock 없이 부른다. 테스트 `a_slow_cancel_does_not_delay_other_timers`.

### #4 `RecurringScheduleThread.waitForStopped(long)`: lock을 놓지 않음 — 높음

- 위치: [RecurringScheduleThread.java:206-214](../../src/main/java/utils/thread/RecurringScheduleThread.java#L206-L214)
- 문제: `m_lock.lock()`을 한 뒤 `try` 블록에 들어가기 **전에** "작업 스레드에서 호출" 검사로 `RuntimeException`을 던진다. 그래서 lock이 풀리지 않는다. 작업 스레드가 lock을 쥔 채 남으므로, 이후 `stop()`·`getState()`·다른 스레드의 `waitForStopped()`가 모두 멈춘다. 인자 없는 `waitForStopped()`는 검사가 `try` 안에 있어 문제가 없다.
- 제안: 검사를 `try` 안으로 옮긴다(또는 lock을 잡기 전에 한다). marmot이 이 클래스를 쓰므로 우선 처리한다.
- 결과: 완료(미커밋). 작업 스레드 검사를 `try` 안으로 옮겼다. 테스트 `timed_wait_for_stopped_from_perform_releases_the_lock` — 옛 구현으로 돌리면 테스트가 끊을 수 없는 `lock()`에서 멈춰 끝나지 않는 것으로 재현을 확인했다.

### #5 `RecurringScheduleThread.setLogger()` 조건 반전 — 중간

- 위치: [RecurringScheduleThread.java:85-87](../../src/main/java/utils/thread/RecurringScheduleThread.java#L85-L87)
- 문제: `m_logger = logger == null ? logger : s_logger;`라서, 로거를 주면 무시하고 기본 로거를 쓰고, `null`을 주면 `null`을 넣어 이후 로그에서 NPE가 난다. `RecurringScheduleImpl.setLogger()`는 `null`을 그대로 넣는다([RecurringScheduleImpl.java:57](../../src/main/java/utils/thread/RecurringScheduleImpl.java#L57)).
- 제안: `logger != null ? logger : s_logger`.
- 결과: 완료(미커밋). `logger != null ? logger : s_logger`. 테스트 `set_logger_uses_the_given_logger_and_null_restores_the_default`. (`RecurringScheduleImpl.setLogger`의 `null` 문제는 #9에서 함께 다룸.)

### #6 `RecurringScheduleThread.getState()`가 `WORKING`을 돌려주지 않음 — 중간

- 위치: [RecurringScheduleThread.java:33-35](../../src/main/java/utils/thread/RecurringScheduleThread.java#L33-L35)
- 문제: `STATE_MAPPING`이 내부 상태 `STATE_RUNNING_WORKING`(3)을 `State.IDLE`로 바꾼다. 인터페이스의 `WORKING`은 이 구현에서 나오지 않는다. `STATE_NOT_STARTED`(0)도 `STARTING`으로 보인다(시작 전인데 "시작 중").
- 제안: 3 → `WORKING`. 시작 전 상태는 `State`에 `NOT_STARTED`를 추가하거나(인터페이스 변경, #20과 함께), 현재처럼 두되 Javadoc에 적는다.
- 결과: 완료(미커밋). 작업 중은 `WORKING`, 시작 전은 `STOPPED`로 보고한다(`RecurringScheduleImpl`과 같음; `NOT_STARTED` 추가는 #20). 테스트 `get_state_reports_working_while_performing`.

### #7 `RecurringScheduleThread.start()`: 시작 실패를 알리지 않음 — 중간

- 위치: [RecurringScheduleThread.java:128-151](../../src/main/java/utils/thread/RecurringScheduleThread.java#L128-L151)
- 문제: 인터페이스는 `onStarted()`가 실패하면 `ExecutionException`을 던진다고 정한다. 이 구현은 작업 스레드에서 `onStarted()`가 실패하면 상태를 `STOPPED`로 바꿀 뿐이고, `start()`는 정상 반환한다. 호출자는 `getFailureCause()`를 따로 확인하지 않으면 시작에 실패한 줄 모른다. `RecurringScheduleImpl.start()`는 계약대로 던진다.
- 제안: 시작 대기에서 깨어난 뒤 상태가 `STOPPED`이고 실패 원인이 있으면 `ExecutionException`을 던진다.
- 결과: 완료(미커밋). `onStarted()` 실패 시 `start()`가 `ExecutionException`을 던진다. 첫 `perform()`이 곧바로 실패해 `start()`가 깨기 전에 `STOPPED`가 되는 경우와 구분하려고 `m_startFailed` 플래그를 둔다. 테스트 `start_throws_when_on_started_fails`, `start_does_not_throw_when_the_first_perform_fails_quickly`. marmot은 `onStarted()`가 실패하지 않는 코드라 영향 없음.

### #8 `RecurringScheduleThread.stop()`: 시작 전 호출 — 낮음

- 위치: [RecurringScheduleThread.java:154-185](../../src/main/java/utils/thread/RecurringScheduleThread.java#L154-L185)
- 문제: 시작 전(`STATE_NOT_STARTED`)에 부르면 상태를 `STOPPING`으로 바꾼다. 작업 스레드가 없으므로 `mayInterruptIfRunning=true`이면 `m_performThread.interrupt()`에서 NPE가 나고, 아니면 `STOPPING`에 영원히 머물러 `waitForStopped()`가 끝나지 않는다.
- 제안: 시작 전이면 바로 `STOPPED`로 바꾸고 반환한다.
- 결과: 완료(미커밋). 시작 전 `stop()`은 바로 `STOPPED`로 바꾸고 반환한다(`onStarted()`를 부르지 않았으므로 `onStopped()`도 부르지 않음). 테스트 `stop_before_start_stops_immediately`.

### #25 `RecurringScheduleThread`: 외부 검토 지적 — 높음

#4~#8 수정본에 대한 외부 검토에서 나온 항목. 넷 다 원래 코드에 있던 문제다.

- (1) `stop()`이 `m_lock`을 쥔 채 `InterruptableWork.interrupt()`를 부른다. 그 메소드가 `perform()`이 끝나기를 기다리는데 작업 스레드가 같은 lock(`getState()`, 종료 처리)을 기다리면 교착된다.
- (2) `stop()`이 `STARTING`이 끝나기를 기다린다. `onStarted()` 안에서 `stop()`을 부르면 자기 자신을 기다려 교착되고 `start()` 호출자도 멈춘다.
- (3) 생성자·`setInterval()`이 값을 검사하지 않고, `m_interval * 1000000`이 넘친다(넘치면 음수가 되어 기다리지 않고 계속 수행).
- (4) 대기에서 깨어난 이유와 관계없이 `경과 - 주기`를 지연 보정 표본으로 기록해, `performNow()`로 일찍 깨우면 다음 대기가 약 두 배가 된다.
- 문서: `waitForStopped(long)` Javadoc이 `TimeoutException`을 적고 있으나 실제로는 `false`를 반환한다.
- 결과: 완료(미커밋).
  - (1) lock 안에서는 `STOPPING` 표시와 대상 확보만 하고, `interrupt()`·쓰레드 인터럽트는 lock 밖에서 한 번만 요청한다(`m_interruptRequested`; `stop(false)` 뒤의 `stop(true)`는 인터럽트로 올릴 수 있다). 작업 쓰레드 자신이 부른 경우는 요청하지 않는다.
  - (2) `STARTING` 중의 `stop()`은 기다리지 않고 `STOPPING`으로 표시한 뒤 반환한다. `start()`는 상태 대신 `m_startCompleted`를 기다리고, 초기화가 끝나면 `perform()` 없이 종료한다(성공했으면 `onStopped()` 호출, 실패했으면 호출하지 않음). 종료 처리의 `STARTING` 분기는 없앴다.
  - (3) 생성 시 `work != null`, `interval > 0`, `initialDelay >= 0`, `setInterval()`도 `> 0`을 검사한다(`IllegalArgumentException`). 나노초 변환은 `TimeUnit.toNanos()`(포화)이고 시각 비교는 모두 차이로 한다.
  - (4) 다음 수행 기한(`dueNano`)과 `m_runNowRequested`를 두고 조건 반복문으로 기다린다. 기한에 도달한 대기만 표본으로 쓰고, 다른 이유로 깨어나면 남은 시간을 다시 기다린다.
  - 함께 결정: `performNow()`가 요청을 받았는지 `boolean`으로 돌려준다(인터페이스 변경; utils 밖 사용처 없음, `RecurringScheduleImpl`은 계속 `UnsupportedOperationException`). 팩토리 Javadoc에 fixed-rate가 밀린 수행을 따라잡지 않는다는 점, `setInterval()`은 다음 대기부터 적용된다는 점을 적었다.
  - 적용하지 않음: `StopMode`/`requestStop`, `requestRunNow`, `Duration`·`NEW` 상태(#20), 클래스·멤버 이름 변경.
  - 테스트: `stop_calls_interruptable_work_outside_the_lock`, `stop_from_on_started_stops_without_performing`, `stop_during_on_started_returns_without_waiting`, `invalid_intervals_are_rejected`, `a_huge_interval_does_not_overflow`, `perform_now_reports_whether_the_request_was_accepted`, `perform_now_does_not_lengthen_the_next_fixed_rate_interval`. 뒤의 셋(검사·넘침·보정)은 옛 구현으로 돌려 실패를 확인했다. 교착 테스트 둘은 옛 구현에서 끊을 수 없는 `lock()`에 걸리므로 돌리지 않았다.

### #9 `CamusExecutorImpl` / `RecurringScheduleImpl`: 다수의 결함 — 높음(단, 사용처 없음)

- 위치: [CamusExecutorImpl.java](../../src/main/java/utils/thread/CamusExecutorImpl.java), [RecurringScheduleImpl.java](../../src/main/java/utils/thread/RecurringScheduleImpl.java)
- 문제:
  - **교착**: `RecurringScheduleImpl.stop(true)`가 `perform()` 중에 불리면, 스케줄 lock을 쥔 채 `DelayedTask.cancel(true)`를 부른다([RecurringScheduleImpl.java:168](../../src/main/java/utils/thread/RecurringScheduleImpl.java#L168)). 이 메소드는 작업이 끝나기를 `wait()`하는데, 작업은 끝나는 길에 같은 스케줄 lock(`shutdown()` → `setState()`)을 잡아야 한다.
  - `RecurringScheduleImpl.waitForStopped(long)`이 남은 시간 대신 **절대 기한**(`dueNanos`)을 `awaitNanos`에 넘긴다([:211](../../src/main/java/utils/thread/RecurringScheduleImpl.java#L211)). 사실상 시간 제한이 없다.
  - `DelayedTask.get()`이 작업 예외를 `(ExecutionException)`으로 형변환해 `ClassCastException`이 난다([CamusExecutorImpl.java:219](../../src/main/java/utils/thread/CamusExecutorImpl.java#L219), 243).
  - `get(timeout)`의 `(int)remainNanos%1000000`은 형변환이 먼저 적용되어, 큰 값에서 음수 나노초가 나와 `IllegalArgumentException`이 난다([:253](../../src/main/java/utils/thread/CamusExecutorImpl.java#L253)).
  - `cancel(true)`는 실행 중인 작업에 대해 `CANCEL_REQUESTED`를 설정하지 않아 항상 `false`를 반환하고, 작업이 끝날 때까지 무기한 기다린다([:292-323](../../src/main/java/utils/thread/CamusExecutorImpl.java#L292-L323)).
  - 기반 `ExecutorService`가 종료된 뒤 예약 작업이 만료되면 `RejectedExecutionException`으로 작업 스레드가 조용히 죽고, 이후 예약은 모두 실행되지 않는다([:176-184](../../src/main/java/utils/thread/CamusExecutorImpl.java#L176-L184)).
  - `RecurringScheduleImpl.getState()`가 lock 없이 읽고, `performNow()`는 지원하지 않는다.
- 제안: utils 밖 사용처가 없으므로 **삭제**를 권한다(#15). JDK `ScheduledThreadPoolExecutor`(`scheduleAtFixedRate`/`scheduleWithFixedDelay`)가 같은 기능을 제공한다. 유지한다면 `ScheduledExecutorService` 위에 다시 구현한다.
- 결과:

### #10 `SingleWaiterExecutor`: 시작 실패 시 영구 정지 — 중간

- 위치: [SingleWaiterExecutor.java:142-146](../../src/main/java/utils/thread/SingleWaiterExecutor.java#L142-L146), [:165-168](../../src/main/java/utils/thread/SingleWaiterExecutor.java#L165-L168)
- 문제: `startInGuard()`는 `m_running = exec`를 먼저 설정하고 `exec.start()`를 부른다. `start()`가 예외를 던져 실행이 끝난 상태로 가지 않으면, 종료 콜백(`onDone`)이 오지 않아 `m_running`이 그대로 남는다. 이후 제출한 작업은 대기 슬롯에서 영원히 기다린다. `onDone()` 안에서 다음 작업의 `start()`가 던지면 그 예외는 비동기 리스너 안에서 사라지고, 대기 슬롯도 비지 않는다. 또 `start()`와 대기 작업의 `cancel(true)`를 내부 lock을 쥔 채 부르므로, 작업 쪽 lock과 순서가 뒤집힐 수 있다.
- 제안: 사용처가 없으므로 #15에서 삭제를 먼저 검토한다. 유지한다면 `start()` 실패 시 `m_running`을 되돌리고, `start()`·`cancel()`은 lock 밖에서 부른다.
- 결과:

### #11 `Guard`: 인터럽트를 이중으로 알림 — 중간

- 위치: [Guard.java:547-549](../../src/main/java/utils/thread/Guard.java#L547-L549), 607-609, 769-771, 834-836, 892-895
- 문제: `TimedAwaitCondition`의 대기와 `*Checked` 종결 메소드는 `InterruptedException`을 잡아 `Thread.currentThread().interrupt()`로 플래그를 다시 켠 뒤 **같은 예외를 다시 던진다.** 관례상 `InterruptedException`을 던지면 플래그는 지워진 상태여야 한다. 이렇게 하면 예외를 잡아 정리하는 호출자가 그 안에서 다시 대기할 때(`finally`의 정리 대기 등) 곧바로 또 인터럽트된다. 무제한 `AwaitCondition.andReturn()/andRun()/andGet()`은 플래그를 다시 켜지 않아 동작이 일관되지 않다.
- 제안: 다시 던지는 곳에서는 플래그를 켜지 않는다(5곳). `Guard`는 사용처가 많으므로, 플래그에 기대는 호출자가 있는지 확인한 뒤 바꾼다(예외를 잡아 삼키면서 플래그를 확인하는 코드).
- 결과:

### #12 `Guard`: `Date` 기한 대기의 재확인 누락 — 낮음

- 위치: [Guard.java:873-879](../../src/main/java/utils/thread/Guard.java#L873-L879)
- 문제: `awaitSignal(due)`가 `false`(기한 경과)를 반환하면 조건을 다시 확인하지 않고 곧바로 `TimeoutException`을 던진다. 기한 직전에 신호와 조건 충족이 겹친 경우 조건이 참인데도 시간 초과가 된다. `Duration` 기반 대기는 반복문 맨 위에서 조건을 다시 확인하므로 이 문제가 없다.
- 제안: `Duration` 쪽처럼 "조건 확인 → 남은 시간 확인 → 대기" 순서로 맞춘다.
- 결과:

### #13 `Guard`: `Error`까지 `ExecutionException`으로 감쌈 — 낮음

- 위치: [Guard.java:551-554](../../src/main/java/utils/thread/Guard.java#L551-L554) 외 3곳
- 문제: `andRunChecked`/`andGetChecked`가 `catch ( Throwable e )`로 `OutOfMemoryError`·`StackOverflowError`도 `ExecutionException`으로 감싼다. 호출자가 `ExecutionException`을 일반 실패로 처리하면 치명적 오류가 묻힌다.
- 제안: `Error`(최소한 `VirtualMachineError`)는 감싸지 않고 다시 던진다. utils 다른 곳(`StateChart`)의 `throwIfFatal` 정책과 맞춘다.
- 결과:

### #14 `CompletionFStream`: 실행 lock 안에서 블록 — 낮음

- 위치: [CompletionFStream.java:82](../../src/main/java/utils/thread/CompletionFStream.java#L82)
- 문제: 결과를 동기 종료 리스너(`whenFinished`)에서 `supply()`로 넣는다. `EventDrivenExecution`의 동기 리스너는 실행 lock(`m_aopGuard`)을 쥔 채 불리므로, 버퍼가 차면 그 lock을 쥔 채 블록한다. 그동안 그 실행의 상태 조회나 `cancel()`도 멈춘다. Javadoc은 "worker가 블록될 수 있다"고만 적고 lock 문제는 적지 않는다.
- 제안: `whenFinishedAsync`로 넣거나, 블록 대신 큐에 넣는 방식으로 바꾼다. 사용처가 테스트뿐이라 #15에서 함께 결정한다.
- 결과:

## 정리

### #15 쓰이지 않는 클래스 정리 — 결정 필요

- 대상과 권고:

  | 클래스 | 상태 | 권고 |
  |---|---|---|
  | `AsyncConsumer` | `// FIXME: 삭제 대상` 표시, 사용처 없음 | 삭제 |
  | `CamusExecutor`, `CamusExecutorImpl`, `RecurringScheduleImpl` | 패키지 내부만, 결함 다수(#9) | 삭제(JDK `ScheduledExecutorService`로 대체) |
  | `ConditionVariable` | `InterThreadShareSupplier`만 사용, 반대 극성, Guava `Preconditions` | 삭제(`Guard`로 대체) |
  | `InterThreadShareSupplier` | 사용처 없음, 기본 설정에서 동작하지 않음(#1) | 삭제, 또는 다시 구현해 유지 |
  | `SingleWaiterExecutor` | 사용처 없음(#10) | 삭제, 또는 고쳐서 유지 |
  | `LengthOneOverridingQueue` | 사용처 없음, 결함 없음 | 유지(이름·API만 #21·#22) |
  | `CompletionFStream` | 테스트만 | 유지(#14만 수정) |
  | `InterruptableWork` | `RecurringSchedule*`만 사용 | `RecurringScheduleThread`와 함께 유지 |

- 결정할 것: 위 권고대로 삭제할지, 또는 다른 프로젝트(utilsx, utils.geo, marmot 이외)에서 쓸 가능성이 있어 남길지. 이번 검색에 포함하지 않은 저장소가 있으면 알려 주어야 한다.
- 결과:

### #16 `Guard` 내부 정리 — 낮음

- 위치: [Guard.java:477-897](../../src/main/java/utils/thread/Guard.java#L477-L897)
- 내용:
  - `AwaitCondition`과 `TimedAwaitCondition`이 종결 메소드 5개(`andReturn`/`andRun`/`andRunChecked`/`andGet`/`andGetChecked`)를 거의 똑같이 두 번 구현한다. "조건 대기" 부분만 다르므로, 공통 기반에 종결 메소드를 두고 대기만 추상화하면 절반으로 줄어든다(#11·#12·#13을 고칠 곳도 한 곳이 된다).
  - `final` 클래스의 `protected final Guard m_guard`는 의미가 없다.
  - `run()`과 `runChecked()`는 인자를 검사하지만 `get`/`getChecked`/`accept`/`acceptChecked`는 검사하지 않는다.
- 결과:

### #17 `ExecutorAware`의 위치 — 낮음

- 내용: 유일한 사용처가 `utils.async.AbstractAsyncExecution`이고, Javadoc은 "컴포넌트 관리자"가 주입한다고 하는데 그런 관리자는 utils에 없다. `utils.async`로 옮기거나, 실제로 주입하는 곳이 없다면 `AbstractAsyncExecution`의 설정 메소드로 흡수한다.
- 결과:

## 인터페이스 제안

### #18 `Guard` 조건을 `BooleanSupplier`로

`awaitCondition(Supplier<Boolean>)`은 매번 박싱하고, 조건이 `null`을 돌려주면 언박싱에서 NPE가 난다. `BooleanSupplier`로 바꾸면 둘 다 없어진다. 사용처 74곳이 모두 람다·메소드 참조라 **소스 호환**이다(다시 컴파일만 하면 된다). `ConditionVariable`을 지우면(#15) 패키지 안에 `Supplier<Boolean>` 조건이 남지 않는다.

- 결과:

### #19 `Timer` 다시 구현

```java
/** Execution의 시간 제한을 관리한다. 실행이 끝나면 예약도 취소된다. */
final class ExecutionTimeouts {
	// ScheduledThreadPoolExecutor(1, daemon "execution-timeout"), setRemoveOnCancelPolicy(true)
	static ScheduledFuture<?> schedule(Execution<?> exec, Duration timeout) {
		ScheduledFuture<?> f = SCHEDULER.schedule(() -> CANCELLER.execute(() -> exec.cancel(true)), …);
		exec.whenFinishedAsync(r -> f.cancel(false));	// 끝난 실행은 즉시 예약 해제
		return f;
	}
}
```

- 크기 제한이 없고, 끝난 실행은 바로 빠지며(#2), 취소는 lock 밖의 별도 스레드에서 한다(#3). 시간은 `System.nanoTime` 기반이라 시계 변경에 영향받지 않는다(현재는 `Date`/`currentTimeMillis`).
- `Execution.setTimeout()`의 반환형을 바꾸지 않아도 된다(내부 구현만 교체). `Executions.getTimer()`와 `Timer` 클래스는 `TimerTest`만 직접 쓰므로 함께 정리한다.
- RCK에서 만든 `RCKExecutors`(타이머 스레드 + 작업 풀)와 같은 구조다.
- 결과: 완료(미커밋). 이름은 `Timer`로 유지했다(#22의 `ExecutionTimeouts`는 별도 결정). 공개 메소드는 그대로이고 `setTimer`가 `void` 대신 `ScheduledFuture<?>`를 돌려준다(호출부 소스 호환). 내부는 데몬 1스레드 `ScheduledThreadPoolExecutor`(`setRemoveOnCancelPolicy(true)`) + 취소 전용 데몬 캐시 풀, 단조 시계 기반. `shutdown()` 뒤의 `setTimer`는 `IllegalStateException`. `Executions.getTimer()`의 비동기화 지연 생성(동시에 처음 부르면 타이머가 둘 이상 생길 수 있었음)도 holder 방식으로 고쳤다. `TimerTest`에 4개 추가(총 8개). `utils.async`·`utils.thread`(548개)·`utils.statechart`(114개) 테스트 통과. README 5.4·7절, 패키지 CLAUDE.md 갱신. 외부 검토 반영(미커밋): `shutdown()`이 남은 예약을 모두 해제(`shutdownNow()`는 future를 완료하지 않음), 취소 스레드를 최대 4개(`MAX_CANCELLATION_THREADS`, 대기열 무제한)로 제한, `Date` 기한 오버플로(`Long.MIN_VALUE`) 수정, 반환형을 좁은 `Timer.TimeoutHandle`(`cancel()`=예약 해제, `isPending`/`isCancelled`/`isExpired`)로 변경, 같은 실행에 다시 예약하면 추가된다는 점과 핸들의 세 상태 구분을 문서화, `cancel(true)` 거부는 상태와 함께 DEBUG 로그. 종료 리스너 누적은 `EventDrivenExecution`이 종료 알림 뒤 목록을 비우고 종료 후 등록은 보관하지 않도록 고쳐 해결(실행 중 반복 예약의 누적은 문서화로 대신). 이름 변경 제안은 반영하지 않음. 테스트: `TimerTest` 12개, `EventDrivenExecution0Test`에 1개 추가. async·thread·statechart·websocket 839개, rpc 69개 통과.

### #20 `RecurringSchedule`: `Duration`과 예외 계약

- `getInterval()`/`setInterval(long millis)`/`waitForStopped(long timeout)`과 팩토리의 `initialDelay`·`interval`이 모두 `long` 밀리초다. `Duration`으로 바꾸면 단위 실수가 없어진다(marmot 3곳 수정 필요).
- `start()`의 예외 계약(#7), `performNow()`를 지원하지 않는 구현(#9), 시작 전 상태(#6)를 인터페이스 Javadoc에 명시한다.
- `waitForStopped`의 `RuntimeException`은 `IllegalStateException`이 더 정확하다.
- 결과:

### #21 `LengthOneOverridingQueue`의 `poll()`

Java의 `Queue.poll()`은 블록하지 않고 비어 있으면 `null`을 돌려준다. 이 클래스의 `poll()`은 항목이 올 때까지 **블록**하고, `poll(Duration)`은 시간 초과 시 `TimeoutException`을 던진다. `BlockingQueue` 관례에 맞추면 `take()`(블록), `poll(Duration)`(시간 초과 시 `null` 또는 `Optional.empty()`), `peek()`(그대로)이 된다. 사용처가 없어 바꾸는 비용이 없다.

- 결과:

## 이름 제안 (#22)

| 현재 | 제안 | 이유 |
|---|---|---|
| `Timer` | `ExecutionTimeoutScheduler` (처음 제안은 `ExecutionTimeouts`) | 실행 시간 제한 전용이다. `java.util.Timer`·`javax.swing.Timer`와 이름이 겹친다 — **기각**: 적용했다가 사용자 요청으로 클래스 이름은 `Timer`로 되돌림(테스트 `TimerTest`, holder `TimerHolder`도 원래대로). 설명형 테스트 메소드 이름은 유지 |
| `Timer.setTimer(exec, …)` | `scheduleTimeout(execution, …)` | 타이머를 "설정"하는 것이 아니라 취소를 예약한다 — **기각**: 적용했다가 사용자 요청으로 `setTimer`/`Executions.getTimer()`와 스레드 이름 `execution-timer(-cancel)`로 되돌림. 인자 이름 `execution`, 지역 변수 `handle`, 설명형 테스트 메소드 이름은 유지 |
| `LengthOneOverridingQueue` | `LatestValueSlot` | 큐가 아니라 최신 값 하나를 담는 칸이다(`Queue`를 구현하지도 않는다) |
| `SingleWaiterExecutor` | `LatestWinsExecutor` | 대기 슬롯 하나에 최신 작업만 남기는 것이 핵심이다 |
| `InterThreadShareSupplier` | `CoalescingSupplier` | 동시 호출을 하나로 합치는(call coalescing) 역할이다 — **완료(미커밋)**: 필드 `m_srcSupplier`→`m_source`, `m_inflight`→`m_inFlight`, `finishProduction`→`clearInFlight`, 인자·지역 변수 `result`/`shared`/`producer`→`production`/`isProducer`도 함께 바꿈 |
| `InterruptableWork` | `InterruptibleWork` | 철자(Java 표준은 interruptible) |
| `Guard.awaitSignal(Date)` | `awaitSignalUntil(Date)` | 같은 이름의 `awaitSignal(Duration)`과 의미(기한 vs 기간)가 다르다 |
| `RecurringScheduleThread` | 유지 | marmot이 쓰고, 의미가 분명하다 |
| `CallerRunsSerialExecutor` | 유지 | |

`Guard`, `CallerRunsSerialExecutor`, `RecurringSchedule*`처럼 사용처가 있는 이름은 바꿀 이득이 작다. 위 표의 변경 대상은 모두 utils 밖 사용처가 없다(`Timer`는 `Executions`·`EventDrivenExecution`·`TimerTest`만 고치면 된다).

- 결과:

## 테스트 (#23)

현재 `Guard`, `CompletionFStream`, `CallerRunsSerialExecutor`, `Timer`(`utils.async.TimerTest`)만 테스트가 있다. 고치는 항목마다 회귀 테스트를 붙인다.

- `Timer`: 65개 이상의 동시 타임아웃, 끝난 실행의 예약 해제, 오래 걸리는 `cancel()`이 다른 타이머를 막지 않는지.
- `RecurringScheduleThread`: `getState()`의 `WORKING`, `start()` 실패 예외, 시작 전 `stop()`, 작업 스레드에서의 `waitForStopped(long)`.
- `Guard`: 인터럽트 후 플래그 상태, `Date` 기한 경계.
- 유지하기로 한 `InterThreadShareSupplier`·`SingleWaiterExecutor`·`LengthOneOverridingQueue`.

- 결과:

## 문서 (#24)

- [CLAUDE.md](../../src/main/java/utils/thread/CLAUDE.md)는 `InterThreadShareSupplier`가 "기본은 무제한 대기"라고 적고 있으나 실제로는 기다리지 않는다(#1). `Timer`의 64개 상한은 "주의"로만 적혀 있다.
- 처리 결과(삭제한 클래스, 바뀐 이름·API)를 [README.md](../../src/main/java/utils/thread/README.md)와 CLAUDE.md, 루트 CLAUDE.md의 `utils.thread` 항목에 반영한다.

- 결과:
