# `utils.io` 사용자 가이드

`utils.io`는 파일·스트림을 다룰 때 자주 쓰는 보조 기능을 모은 패키지다. 대부분은 JDK(`java.nio.file.Files`,
`InputStream`)를 얇게 감싸며, JDK에 없는 것(로그 tail, 쓰기 작업을 입력 스트림으로 바꾸기, 외부에서 데이터를 밀어 넣는
입력 스트림, `.env` 파일 읽기 등)을 더한다. 예제에서 import는 생략했다.

---

## 1. 어떤 클래스를 사용할까

| 필요한 기능 | 사용할 클래스 | 참고 |
|---|---|---|
| 파일을 문자열·byte로 읽고 쓰기, 스트림 복사 | [`IOUtils`](IOUtils.java) | 2장 |
| 경로 합성, 복사·이동, 디렉터리 순회, 실행 파일 찾기 | [`FileUtils`](FileUtils.java) | 3장 |
| 로그 파일 끝을 따라가며 새 줄 받기 (`tail -f`) | [`LogTailer`](LogTailer.java), [`LogTailerListener`](LogTailerListener.java) | 4장 |
| 출력 스트림에 쓰는 코드의 결과를 입력 스트림으로 읽기 | [`InputStreamFromOutputStream`](InputStreamFromOutputStream.java) | 5.1 |
| 다른 스레드가 데이터 조각을 밀어 넣는 입력 스트림 | [`SuppliableInputStream`](SuppliableInputStream.java) | 5.2 |
| 원본에서 정해진 byte 수까지만 읽기 | [`LimitedInputStream`](LimitedInputStream.java) | 5.3 |
| `.env` 파일을 읽어 변수 치환 | [`EnvironmentFileLoader`](EnvironmentFileLoader.java) | 6장 |
| 로컬 파일과 HDFS를 같은 인터페이스로 다루기 | [`FilePath`](FilePath.java), [`LfsPath`](LfsPath.java) | 7장 |
| ZIP 압축·해제·나열 | [`ZipFile`](ZipFile.java) | 8장 |
| 쓰고 나면 지워지는 임시 파일 | [`TempFile`](TempFile.java) | 8장 |
| 객체 ↔ byte 배열 (Java 직렬화) | [`Serializables`](Serializables.java) | 8장 |

---

## 2. `IOUtils` — 파일과 스트림

파일을 다루는 메소드는 JDK `Files`처럼 **대상 파일을 첫 인자**로 받는다. charset을 받지 않는 메소드는 항상 **UTF-8**을
쓴다(실행 환경의 기본 charset을 쓰지 않는다).

```java
String json = IOUtils.readString(confFile);                 // UTF-8
IOUtils.writeString(outFile, json);                         // 덮어쓰기, UTF-8
byte[] bytes = IOUtils.readAllBytes(dataFile);
long n = IOUtils.write(targetFile, inputStream);            // 입력 스트림은 닫지 않는다
long copied = IOUtils.copy(in, out);                        // 두 스트림 모두 닫지 않는다
```

- **스트림 소유권:** 스트림을 인자로 받는 메소드는 그 스트림을 닫지 않는다. 닫는 것은 호출자의 몫이다.
- **정확히 읽기:** `readFully(is, buf)`는 버퍼를 다 채우지 못하면 `EOFException`을 던진다. `readUpTo(is, buf)`는
  가능한 만큼 읽고, 하나도 못 읽고 EOF면 `-1`을 반환한다.
- **압축:** `compress`/`decompress`는 `Deflater`/`Inflater`로 byte 배열을 압축·복원한다. 잘린 데이터나 사전(dictionary)이
  필요한 데이터는 `DataFormatException`으로 실패한다.
- **비동기 복사:** `copyAsync(from, to)`는 별도 스레드에서 복사하는 `CopyStream`을 만든다(`start()`로 시작). 취소하면
  입력 스트림을 닫아 블록된 `read`를 깨우고, 결과는 취소 상태가 된다.
- **기타:** `closeQuietly(...)`(예외를 무시하고 닫기), `stringify`/`destringify`(Base64), `pipe(size)`.

---

## 3. `FileUtils` — 경로와 디렉터리

```java
File conf = FileUtils.path(homeDir, "conf", "app.json");
List<File> shapes = FileUtils.walk(dataDir, "*.shp").toList();   // 파일 이름에 glob 적용
File python = FileUtils.findExecutable("python3")
                       .getOrThrow(() -> new IllegalStateException("python3 not found"));
```

- **`walk`의 심볼릭 링크:** glob을 받는 `walk(start, glob)`은 링크를 **따라가고**(순환하면 `FileSystemLoopException`),
  `walk(start)`는 따라가지 않는다. glob은 경로가 아니라 **파일 이름**에만 적용된다.
- **스트림 닫기:** `walk`가 돌려주는 `FStream`은 디렉터리 핸들을 쥐고 있다. `toList()`처럼 끝까지 소비하지 않고
  중간에 그만둘 때는 `close()`로 닫는다.
- **`findExecutable`:** `PATH`에서 실행 가능한 파일을 찾아 `FOption<File>`(절대 경로)로 돌려준다. `PATH`가 없거나
  못 찾으면 빈 값이다.
- 복사·이동·삭제(`copyFile`, `copyDirectory`, `moveFile`, `moveDirectory`, `deleteDirectory`)는 commons-io에,
  `createDirectory`는 `Files.createDirectories`에 위임한다.

---

## 4. `LogTailer` — 로그 파일 따라가기

```java
LogTailer tailer = LogTailer.builder()
                            .file(logFile)
                            .startAtBeginning(true)          // 기본: 현재 끝부터
                            .sampleInterval(Duration.ofSeconds(1))
                            .timeout(Duration.ofMinutes(1))  // 기본: 무한
                            .listener(line -> !line.contains("server started"))
                            .build();
tailer.run();   // listener가 false를 반환하거나 timeout까지 호출 스레드를 점유한다
```

- **listener:** 반드시 지정한다(`build()`에서 검사). 새 줄 처리(`handleLogTail`)만 필수이고, rewind(`logFileRewinded`)와
  변화 없음(`handleLogFileSilence`) 콜백은 기본 구현이 계속(`true`)이므로 람다로 줄 수 있다. 어느 콜백이든 `false`를
  반환하면 정상 종료한다.
- **줄 단위:** `\n`, `\r\n`, `\r`로 끝난 줄만 UTF-8로 디코딩해 전달한다. 아직 개행이 기록되지 않은 마지막 조각은
  보관했다가 다음 주기에 이어 붙인다.
- **변화 없음:** 한 주기 동안 파일 크기가 그대로이면 `handleLogFileSilence`가 호출된다. 개행 없는 조각만 늘어난 것도
  변화로 본다. 감시 대상 프로세스가 죽었는지 확인하는 데 쓰기 좋다.
- **rewind:** 파일 크기가 마지막으로 읽은 위치보다 작아지면 파일을 다시 열어 처음부터 읽는다. 크기만으로 판단하므로,
  다음 폴링 전에 다시 그 이상으로 커지거나 rename 후 재생성하는 로그 로테이션은 감지하지 못한다.
- **실패:** 타임아웃은 `TimeoutException`, listener가 던진 예외는 `ExecutionException`으로 감싸 던진다.

---

## 5. 스트림

### 5.1 `InputStreamFromOutputStream` — 쓰기 코드를 입력 스트림으로

출력 스트림에 쓰는 코드(직렬화, 압축 등)의 결과를 다른 쪽에 입력 스트림으로 넘길 때 쓴다. 쓰기 함수만 넘기면 된다.

```java
InputStream is = InputStreamFromOutputStream.of(out -> {
    for ( Record r: records ) {
        r.writeDelimitedTo(out);
    }
});
upload(is);
```

- 쓰기 함수는 별도 스레드에서 실행되고, 끝나면(실패해도) 출력 스트림이 자동으로 닫힌다.
- **실패 전달:** 쓰기 함수가 던진 예외는 읽는 쪽 `read`에서 `IOException`(원인 연결)으로 나온다. 데이터가 중간에
  끊겼는데 정상 EOF로 보이는 일은 없다. 취소된 경우도 `IOException`(원인은 `CancellationException`)이다.
- `close()`는 쓰기 작업을 취소하고 pipe를 닫는다.
- 실행기나 취소 방식을 직접 정해야 하면 생성자에 `Function<OutputStream, Execution<Void>>`를 넘긴다. 이때 pump는
  기록을 마치거나 실패하면 출력 스트림을 닫아야 하고, 닫은 뒤에는 곧 끝나야 한다.

### 5.2 `SuppliableInputStream` — 밀어 넣는 입력 스트림

생산자 스레드가 `supply(ByteBuffer)`로 조각을 넣고, 소비자 스레드가 `read`로 읽는다. 네트워크로 조각 단위 데이터를
받아 입력 스트림 API로 넘길 때 쓴다.

```java
SuppliableInputStream is = SuppliableInputStream.create(16);   // 큐 길이 상한: 넘치면 supply가 대기
producer: is.supply(chunk); ... is.endOfSupply();              // 실패면 endOfSupply(cause)
consumer: is.readAllBytes();
```

- `endOfSupply()` 뒤의 `supply`는 조각을 버리고 `false`를 반환한다. `close()` 뒤의 `supply`는 `StreamClosedException`.
- `endOfSupply(cause)`로 끝내면 남은 조각을 다 읽은 뒤 `read`가 그 원인을 던진다.
- `available()`은 현재 읽고 있는 조각에 남은 byte 수다.
- 읽기 대기 중 인터럽트되면 인터럽트 상태를 유지한 채 `InterruptedIOException`을 던진다.
- 공급한 `ByteBuffer`는 복사하지 않으므로, 공급한 뒤 바꾸면 안 된다.

### 5.3 `LimitedInputStream`

원본에서 최대 `limit` byte까지만 읽고 그 뒤로는 EOF(`-1`)를 반환한다. 닫아도 원본은 닫히지 않는다.

---

## 6. `EnvironmentFileLoader` — `.env` 파일

```java
Map<String,String> env = EnvironmentFileLoader.from(new File(".env"))
                                              .withFacts(Map.of("HOME", home))
                                              .load();         // 파일 순서 유지
```

- 한 줄에 `KEY=VALUE`. 빈 줄과 `#`로 시작하는 줄은 무시한다. 첫 `=`에서 나누고 키·값의 앞뒤 공백을 지운다
  (`KEY = value`도 된다). 키가 비거나 `=`가 없으면 `IOException`.
- 값 전체를 큰따옴표로 감싸면 따옴표를 벗기며, 안쪽 공백은 그대로 둔다.
- 값의 `${...}`는 앞서 나온 키, `withFacts`로 준 변수, `${env:...}`·`${sys:...}`로 치환된다.
- 작은따옴표, `export` 접두어, 줄 끝 주석은 지원하지 않는다(값의 일부로 읽는다).

---

## 7. `FilePath` / `LfsPath` — 파일 시스템을 가리지 않는 경로

`FilePath`는 로컬 파일과 HDFS 등을 같은 방식으로 다루기 위한 인터페이스이고, `LfsPath`는 로컬 파일 구현이다
(HDFS 구현은 jarvey의 `HdfsPath`).

- `streamChildFilePaths()`는 `null`을 반환하지 않는다. 경로가 없으면 `NoSuchFileException`, 디렉터리가 아니면
  `NotDirectoryException`이다.
- `create(overwrite)`: `false`이면 파일이 있을 때 실패한다(`LfsPath`는 `FileAlreadyExistsException`). 부모 디렉터리는
  자동으로 만든다.
- `renameTo(dst, replaceExisting)`: 부모 디렉터리를 만들고 이동한다. 덮어쓰지 않으면 대상이 있을 때 실패한다.
- `isRegular()`는 "일반 파일인가"가 아니라 **이름이 `_`나 `.`로 시작하지 않는가**(Hadoop 숨김 파일 규칙)이다.
- `path(String)`은 본 경로와 같은 파일 시스템의 **새 경로**를 만든다. 하위 경로는 `getChild`를 쓴다.
- `getParent()`는 부모가 없으면 `null`이다.
- `LfsPath`는 `equals`/`hashCode`를 경로 문자열로 비교하므로 상대 경로와 그 절대 경로는 다르다.

---

## 8. 그 밖의 클래스

- **`ZipFile`:** `ZipFile.zip(zipFile, baseName, files)`, `ZipFile.zipDirectory(zipFile, dir)`로 만들고,
  `new ZipFile(path).listEntries()`, `traverse(visitor)`, `unzip(dir)`로 나열·해제한다. 해제는 ZIP 밖으로 나가는 항목
  (Zip Slip)을 거부한다.
- **`TempFile`:** `try ( TempFile tmp = TempFile.create("prefix-", ".json") ) { ... }` — 닫으면 파일을 지운다.
  삭제에 실패하면 `IOException`.
- **`Serializables`:** `serialize(obj)` / `deserialize(bytes)`. 외부에서 받은 데이터는 허용 클래스를 정한
  `ObjectInputFilter`와 함께 `deserialize(bytes, filter)`로 복원한다.
