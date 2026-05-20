## utils (`etri:utils`)

Java 17 유틸리티 라이브러리. `utils.*` 패키지 하위에 함수형 빌딩 블록(`FOption`, `Result`, `Either`, `Try`, `Lazy`, `Checked*`/`Unchecked*` 함수 패밀리), 풀-기반 스트림 API(`FStream` 계열), 비동기 실행 프레임워크(`Execution<T>` / `EventDrivenExecution` / `PeriodicLoopExecution` / `PeriodicPoller`), 상태차트, 스레드 유틸리티(`Guard`, `Timer`, `SingleWaiterExecutor`), I/O, JDBC/JPA, HTTP REST 클라이언트, WebSocket, Picocli CLI 등 다양한 공용 모듈을 제공한다. `main` 메서드는 없고, 빌드 산출물(JAR)은 `$HOME/development/...` 하위의 다른 프로젝트들에서 의존성으로 소비된다.

자세한 패키지 구조와 컨벤션은 [CLAUDE.md](CLAUDE.md)와 다음 문서를 참고한다.
* 상태차트: [docs/statechart-guide.md](docs/statechart-guide.md), [src/main/java/utils/statechart/CLAUDE.md](src/main/java/utils/statechart/CLAUDE.md)
* WebSocket: [docs/websocket-guide.md](docs/websocket-guide.md), [src/main/java/utils/websocket/CLAUDE.md](src/main/java/utils/websocket/CLAUDE.md)
* 비동기 실행: [src/main/java/utils/async/CLAUDE.md](src/main/java/utils/async/CLAUDE.md)

## 설치 방법

### 1. 사전조건

* JDK 17 이상이 설치되어 있어야 한다.

### 2. Gradle 설치
* SDKMan을 설치한다
<pre><code>$ curl -s "https://get.sdkman.io" | bash
$ source "$HOME/.sdkman/bin/sdkman-init.sh"
</code></pre>
* Groovy를 설치한다
<pre><code>sdk install groovy</code></pre>
* Gradle을 설치한다.
<pre><code>sdk install gradle</code></pre>

### 3. `utils` 프로젝트 다운로드 및 컴파일
`$HOME/development` 디렉토리를 생성하고, 이곳으로 이동한다.
	디렉토리 이름이 반드시 `development/common`일 필요는 없으나, 여기서는 편의상 이 이름을 사용한다.
<pre><code>$ cd $HOME
$ mkdir -p development/common
$ cd development/common
</code></pre>

GitHub에서 utils 프로젝트를 clone한다.
<pre><code>git clone https://github.com/kwlee0220/utils</code></pre>
* GitHub URL 주소: `https://github.com/kwlee0220/utils`
* 생성된 utils 디렉토리는 `$HOME/development/common/utils`에 위치한다.

생성된 디렉토리로 이동하여 컴파일을 시도한다.
<pre><code>$ cd utils
$ gradle assemble
</code></pre>

Eclipse IDE를 이용하려는 경우 `eclipse` Gradle 태스크를 수행시켜 Eclipse 프로젝트 import에
필요한 `.project` 파일과 `.classpath` 파일을 생성시킨다.
<pre><code>$ gradle eclipse</code></pre>

> Lombok이 `compileOnly` + `annotationProcessor`로 연결되어 있으므로,
> IDE에서 import할 경우 반드시 Lombok 플러그인이 설치되어 있어야 `@Getter`/`@Setter` 등
> 어노테이션이 생성한 멤버를 인식한다.

### 4. 테스트 실행

테스트는 JUnit 4 + Mockito 기반이며, 다음과 같이 실행한다.
<pre><code>$ gradle test                                       # 전체 테스트
$ gradle test --tests utils.stream.MapTest          # 특정 테스트 클래스
$ gradle test --tests 'utils.stream.*'              # 패키지 단위
</code></pre>

### 5. 산출물

* GAV 좌표: `etri:utils:25.10.27`
* 출력 JAR: `build/libs/utils-25.10.27.jar`
