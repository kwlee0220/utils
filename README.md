## 설치 방법

### 1. 사전조건

* Oracle Java를 (Java8 이상) 설치되어 있어야 한다.

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
	디렉토리 이름이 반드시 `development`일 필요는 없으나, 여기서는 편의상 이 이름을 사용한다.
<pre><code>$ cd $HOME
$ mkdir development
$ cd development
</code></pre>

`common` 디렉토리를 생성하고, 이곳으로 이동한다. 이때는 가능하면
	`common` 이름을 사용하는 것을 권장한다.
<pre><code>$ mkdir common
$ cd common
</code></pre>

GitHub에서 utils 프로젝트를 download하고, 받은 zip 파일 (utils-master.zip)의 압축을 풀고,
디렉토리 이름을 utils로 변경한다.
* GitHub URL 주소: `https://github.com/kwlee0220/utils`
* 생성된 utils 디렉토리는 `$HOME/development/common/utils`에 위치한다.

생성된 디렉토리로 이동하여 컴파일을 시도한다.
<pre><code>$ cd utils
$ gradle assemble
</code></pre>

Eclipse IDE를 이용하려는 경우 `eclipse` 태스크를 수행시켜 Eclipse 프로젝트 import에
필요한 `.project` 파일과 `.classpath` 파일을 시킨다.
<pre><code>$ gradle eclipse</code></pre>
