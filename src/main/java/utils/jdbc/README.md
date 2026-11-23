# `utils.jdbc` 사용자 가이드

`utils.jdbc`는 JDBC 연결 생성, SQL 실행, 결과 조회와 타입 변환을 지원하는 유틸리티 패키지다.

- 드라이버·URL·자격 증명으로 연결을 만드는 `JdbcProcessor` (선택적으로 JVM 전역 연결 풀 사용)
- 질의 결과를 `FStream`으로 읽는 순회 도구
- SQL 컬럼 값을 Java 값으로 읽고, Java 값을 SQL 파라미터로 설정하는 타입 핸들러 `SQLDataType`

| 클래스 | 역할 |
|---|---|
| [`JdbcProcessor`](JdbcProcessor.java) | 연결 생성, 질의·갱신 실행, 테이블 메타데이터 조회. `JdbcProcessor.Builder`로 만든다 |
| [`JdbcConfiguration`](JdbcConfiguration.java) | 접속 설정 객체. `parse`로 접속 설정을 포함한 JDBC URL(이하 full JDBC URL, `<url>?user=..&password=..`)을 읽는다 |
| [`JdbcConnectionPools`](JdbcConnectionPools.java) | 연결 풀을 사용하는 `JdbcProcessor` 인스턴스들이 공유하는 HikariCP 풀 레지스트리 |
| [`JdbcUtils`](JdbcUtils.java) | `fstream`(ResultSet → `FStream`), `toColumnObjectList`, `maskPassword` 등 |
| [`SQLDataType<T>`](SQLDataType.java), [`SQLDataTypes`](SQLDataTypes.java) | Java 값 하나를 JDBC로 읽고 쓰는 핸들러와 그 상수들 |
| [`JdbcException`](JdbcException.java) | 검사 예외(checked exception)를 던질 수 없는 곳(스트림 순회 중)에서 `SQLException`을 감싸는 비검사 예외(unchecked exception) |
| [`JdbcParameters`](JdbcParameters.java) | picocli 옵션으로 locator(콜론으로 구분한 접속 정보 문자열)·드라이버 jar를 받는 도우미 |

## 목차

- [1. `JdbcProcessor` 만들기](#1-jdbcprocessor-만들기)
- [2. 연결과 연결 풀](#2-연결과-연결-풀)
- [3. 질의와 갱신](#3-질의와-갱신)
- [4. 예외 정책](#4-예외-정책)
- [5. 타입 핸들러 (`SQLDataType`)](#5-타입-핸들러-sqldatatype)
- [6. 테이블 메타데이터](#6-테이블-메타데이터)
- [7. `JdbcUtils`](#7-jdbcutils)
- [8. 테스트](#8-테스트)

---

## 1. `JdbcProcessor` 만들기

### 1.1 생성 방식

`JdbcProcessor`는 다음 네 가지 방식으로 생성할 수 있다. 각 예제는 독립적으로 사용한다.

```java
// (1) 필드를 하나씩 지정
JdbcProcessor jdbc = JdbcProcessor.builder()
                                  .jdbcUrl("jdbc:postgresql://db:5432/mdt")
                                  .user("mdt").password("secret")
                                  .build();

// (2) 시스템 단축 이름으로 표준 URL 생성
JdbcProcessor jdbc = JdbcProcessor.builder()
                                  .system("mysql").host("localhost").port(3306).dbName("mydb")
                                  .user("user").password("passwd")
                                  .build();

// (3) 설정 객체 또는 full JDBC URL
JdbcProcessor jdbc = JdbcProcessor.builder(JdbcConfiguration.parse(
                        "jdbc:postgresql://db:5432/mdt?user=mdt&password=a+b%26c&sslmode=require"))
                                  .pooled()
                                  .build();

// (4) locator(콜론으로 구분한 접속 정보 문자열): system:host:port:user:passwd:dbname
JdbcProcessor jdbc = JdbcProcessor.builderFromLocator("postgresql:db:5432:mdt:secret:mdt").build();
```

- **필수 항목:** JDBC URL 하나, 또는 `system`·`host`·`port`·`dbName` 네 개. 두 방식의 설정을 함께 지정하면 `IllegalArgumentException`이 발생한다.
- **사용자·비밀번호는 선택 사항이다.** 지정하지 않으면 드라이버에 전달하지 않으므로, URL에 포함된 자격 증명이나 인증이 없는 DB(H2 등)를 그대로 사용할 수 있다.
- **드라이버 클래스:** `driverClassName`을 지정하지 않으면 URL의 하위 프로토콜로 추정한다. 하위 프로토콜 이름 전체를 비교하므로 `jdbc:my:...`는 MySQL로 추정되지 않는다.

| 시스템 이름 | 생성되는 URL | 드라이버 |
|---|---|---|
| `mysql` | `jdbc:mysql://${host}:${port}/${dbname}?characterEncoding=utf8&useSSL=false&useCursorFetch=true` | `com.mysql.cj.jdbc.Driver` |
| `postgresql` | `jdbc:postgresql://${host}:${port}/${dbname}` | `org.postgresql.Driver` |
| `mariadb` | `jdbc:mariadb://${host}:${port}/${dbname}` | `org.mariadb.jdbc.Driver` |
| `kairos` | `jdbc:kairos://${host}:${port}/${dbname}` | `kr.co.realtimetech.kairos.jdbc.kairosDriver` |
| `h2_remote` | `jdbc:h2:tcp://${host}:${port}/${dbname}` | `org.h2.Driver` |
| `h2_local` | `jdbc:h2:${dbname}` | `org.h2.Driver` |

### 1.2 full JDBC URL 형식 (`JdbcConfiguration.parse`)

```
<jdbcUrl>?user=<user>&password=<password>&maxPoolSize=<n>&<드라이버 옵션>...
```

- `user`·`password`·`maxPoolSize` 세 파라미터만 추출하고, **나머지 파라미터는 원래 형식을 유지하여 JDBC URL에 남긴다.** 이 파라미터는 드라이버에 전달된다(`sslmode=require`, `useSSL=false` 등).
- 세 값은 `%XX` escape만 UTF-8로 디코딩한다. **`+`는 공백이 아니라 그대로 `+`**다. 비밀번호에 `&`·`=`·`%`가 있으면 `%26`·`%3D`·`%25`로 쓴다.
- `?`가 없으면 문자열 전체가 JDBC URL이다. `maxPoolSize`가 정수가 아니면 `IllegalArgumentException`이 발생한다.
- `maxPoolSize`는 빌더에서 `pooled()`를 호출했을 때의 풀 크기로만 쓰인다. `maxPoolSize`만 지정해도 연결 풀 사용이 활성화되는 것은 아니다.
- `JdbcConfiguration`에서 필수는 JDBC URL뿐이다(`setJdbcUrl(null)`은 예외). 드라이버 클래스 이름, 사용자 이름, 비밀번호에는 `null`을 지정할 수 있다.

### 1.3 드라이버 적재

| 지정 | 동작 |
|---|---|
| `classLoader(cl)` | 그 ClassLoader로 드라이버를 적재한다(`jarFile`보다 우선) |
| `jarFile(f)` | 그 jar를 적재하는 `URLClassLoader`를 쓴다. **jar 파일마다 하나만**(정규 경로 기준) 만들어 모든 `JdbcProcessor` 인스턴스가 공유하고 닫지 않는다. 경로에 공백·`#`·`%`가 있어도 된다 |
| 둘 다 없음 | `JdbcProcessor`를 정의한 ClassLoader로 `Class.forName` 후 `DriverManager`로 연결한다 |

앞의 두 경우(`classLoader`, `jarFile` 지정)에는 `JdbcProcessor` 인스턴스마다 드라이버가 한 번 인스턴스화되며, `DriverManager`에는 등록되지 않는다.

---

## 2. 연결과 연결 풀

```java
try ( Connection conn = jdbc.connect() ) {
    ...
}
```

- `pooled()` / `pooled(n)` 없이 만든 `JdbcProcessor`는 `connect()`할 때마다 **새 물리 연결**을 만든다.
- `pooled()`로 만들면 `connect()`가 JVM 전역 HikariCP 풀에서 연결을 가져온다. 반환된 `Connection`의 `close()`를 호출하면 연결을 풀에 반환한다.
  - URL·사용자·비밀번호·드라이버 클래스·ClassLoader(또는 jar 경로)가 같은 `JdbcProcessor` 인스턴스들은 **하나의 풀을 공유**한다. 질의마다 인스턴스를 새로 만들어도 연결은 재사용된다.
  - 풀 크기: `pooled(n)`의 `n` → 설정의 `maxPoolSize` → 3(`DEFAULT_MAX_POOL_SIZE`) 순으로 정한다. 같은 연결 정보의 풀이 이미 있으면 처음 만든 인스턴스의 크기를 따른다.
  - 최소 유휴 연결 수는 0(`minimumIdle=0`)이며, 유휴 연결 제한 시간은 60초(`idleTimeout`)로 설정한다.
  - 사용 가능한 연결을 얻지 못하면(풀의 연결이 모두 사용 중이거나 DB에 접속할 수 없으면) 대기 시간 5초가 지난 뒤 `SQLException`이 발생한다.
  - 반환할 때 auto-commit·read-only·격리 수준·catalog·스키마는 복원되지만, `SET` 문으로 변경한 세션 변수는 다음에 그 연결을 받는 사용자에게 그대로 남는다.
- 풀 닫기: `JdbcConnectionPools.close(jdbc)`는 그 인스턴스가 쓰는 풀 하나를, `closeAll()`은 모든 풀을 닫는다. JVM 종료 훅도 모든 풀을 닫는다. 닫힌 풀을 쓰던 인스턴스가 다시 `connect()`하면 새 풀이 만들어진다(풀이 닫히는 순간과 겹쳐 실패한 연결 요청은 새 풀을 만들어 한 번 더 시도한다).
- 비밀번호를 바꾸는 등 연결 정보가 달라지면 새 풀이 생긴다. 기존 풀은 기존 연결 정보로 생성한 `JdbcProcessor`를 `close(jdbc)`에 전달하여 종료한다.

---

## 3. 질의와 갱신

### 3.1 메소드와 자원 해제 책임

| 메소드 | 반환 | 주요 동작 | 자원 해제 책임 |
|---|---|---|---|
| `executeQuery(sql)` | `ResultSet` | `Statement`로 실행한다 | 호출자. **ResultSet을 닫으면 Statement와 연결도 닫힌다.** try-with-resources로 닫는다 |
| `executeQuery(sql, stmtSetter)` | `ResultSet` | `stmtSetter`로 fetch size·timeout 등을 먼저 설정한다 | 위와 같다 |
| `query(sql, deserializer)` | `FStream<T>` | 행을 지연해서 읽어 `deserializer`로 변환한다 | 호출자. 스트림을 닫으면 ResultSet·Statement·연결이 모두 닫힌다. 종단 연산(`toList()`, `findFirst()` 등)은 스트림을 닫는다 |
| `query(sql, binder, deserializer)` | `FStream<T>` | `PreparedStatement`로 실행하며 `binder`는 파라미터만 설정한다 | `query`와 같다 |
| `queryFirst(sql, deserializer)` | `FOption<T>` | 첫 행만 변환한다 | 메소드가 첫 행을 읽은 뒤 모두 닫는다 |
| `queryFirst(sql, binder, deserializer)` | `FOption<T>` | 파라미터는 `binder`로 설정한다 | `queryFirst`와 같다 |
| `executeUpdate(sql)` | 영향받은 행 수 | `Statement`로 실행한다 | 메소드 안에서 모두 닫는다 |
| `executeUpdate(sql, binder)` | 영향받은 행 수 | `binder`는 파라미터만 설정하고, `executeUpdate()`는 메소드가 한 번 호출한다 | 메소드 안에서 모두 닫는다 |
| `countRows(table)` | `long` | 테이블의 행 수를 조회한다([3.3절](#33-테이블-이름과-sql-검사)) | 메소드 안에서 모두 닫는다 |
| `countQueryRows(query)` | `long` | 질의 결과의 행 수를 조회한다([3.3절](#33-테이블-이름과-sql-검사)) | 메소드 안에서 모두 닫는다 |
| `dropTable(table)` | `void` | 테이블을 삭제한다. 지정한 테이블이 없으면 `SQLException`이 발생한다 | 메소드 안에서 모두 닫는다 |
| `connect()` | `Connection` | 새 연결을 만들거나 풀에서 가져온다 | 호출자가 닫는다 |
| `JdbcUtils.fstream(rs, f)` | `FStream<T>` | 행을 지연해서 읽어 `f`로 변환한다 | 스트림을 닫으면 **ResultSet만** 닫는다. Statement·연결은 호출자가 관리한다 |

테이블 메타데이터 조회(`tableExists`, `getColumns`)는 [6장](#6-테이블-메타데이터)을 참고한다.

### 3.2 사용 예

```java
List<String> names = jdbc.query("select name from item order by id", rs -> rs.getString(1)).toList();

try ( FStream<Item> items = jdbc.query("select * from item", Item::fromRow) ) {
    Item first = items.next().getOrNull();       // 중간에 멈추면 직접 닫는다
}

List<String> after = jdbc.query("select name from item where id > ?", pstmt -> pstmt.setInt(1, 10),
                                rs -> rs.getString(1)).toList();

int n = jdbc.executeUpdate("update item set name = ? where id = ?", pstmt -> {
    pstmt.setString(1, "x");
    pstmt.setInt(2, 1);
});
```

- `JdbcProcessor`가 연결·Statement·ResultSet을 생성한 뒤 결과를 반환하기 전에 실패하면(런타임 예외 포함), 그때까지 생성한 자원을 닫고 원래 예외를 전파한다. 풀 크기 1에서 실패를 반복해도 연결이 고갈되지 않는다.
- 이미 반환된 스트림을 직접 순회하는 중에 예외가 발생하면 스트림은 자동으로 닫히지 않는다. 이때는 호출자가 스트림을 닫아야 하므로 try-with-resources를 쓴다.
- `query`·`JdbcUtils.fstream`은 행을 **지연해서** 읽는다. 첫 행도 `next()`(또는 종단 연산)에서 읽는다. `next()`로 끝까지 읽은 것만으로는 닫히지 않으므로, 직접 순회할 때는 try-with-resources를 쓴다.
- 행 변환 결과가 `null`이어도 해당 행을 제외하지 않고 스트림 원소로 반환한다.

### 3.3 테이블 이름과 SQL 검사

- `countRows()`와 `dropTable()`은 테이블 이름이 허용된 식별자 형식인지 검사한다. 따옴표 없는 식별자는 유니코드 문자 또는 `_`로 시작해야 하며, 이후에는 문자·숫자·`_`·`$`를 사용할 수 있다. 큰따옴표로 감싼 식별자도 허용하지만 비어 있거나 내부에 큰따옴표를 포함할 수는 없다. 두 형식 모두 앞에 스키마 이름과 `.`을 붙일 수 있다. 형식이 맞지 않으면 `IllegalArgumentException`이 발생한다.
- `countQueryRows(query)`는 `SELECT COUNT(*) FROM (<query>\n) subquery`를 실행한다. 질의 끝의 `;`는 제거한다. 끝의 행 주석(`-- …`)은 허용하지만 `; -- …`처럼 `;` 뒤에 오는 주석은 지원하지 않는다.
- `countQueryRows`의 질의와 `executeQuery`/`executeUpdate`의 SQL은 검사하지 않으므로 신뢰할 수 있는 문자열만 넘긴다.

---

## 4. 예외 정책

- **`JdbcProcessor`의 공개 메소드에서 발생한 JDBC 오류는 연결 풀 사용 여부와 관계없이 `SQLException`으로 전달한다.** 반환된 스트림을 순회하는 중에 발생한 JDBC 오류는 아래처럼 `JdbcException`으로 감싸 전달한다.
- **연결 실패 메시지에는 비밀번호가 들어가지 않는다.** 메시지에는 비밀번호를 가린 URL과 사용자가 들어가고, 드라이버의 SQLState·오류 코드는 유지된다. 감싼 예외에 덧붙이는 드라이버 메시지도 `maskPassword`로 가리지만, 원인 예외(`getCause()`) 자체의 메시지는 드라이버가 생성한 원본 그대로 유지된다.
- 잘못된 locator나 지원하지 않는 JDBC URL로 `IllegalArgumentException`이 발생할 때도 메시지에 비밀번호가 노출되지 않는다(locator는 토큰 수만 표시하고, URL은 비밀번호를 가려 출력한다).
- **비검사 예외인 `JdbcException`은 검사 예외를 던질 수 없는 곳에서만 쓴다.** 스트림 순회 중 행 읽기나 `deserializer`가 `SQLException`으로 실패하면 원인 `SQLException`을 감싼 `JdbcException`이 발생한다. `queryFirst`는 감싸진 원인 예외인 `SQLException`을 꺼내 다시 던진다.
- 콜백(`binder`, `stmtSetter`, `deserializer`)이 던진 런타임 예외는 감싸지 않고 그대로 전파된다.
- 인자 오류(`null`, 잘못된 식별자, 잘못된 URL)에는 `IllegalArgumentException`이 발생한다.

---

## 5. 타입 핸들러 (`SQLDataType`)

```java
public interface SQLDataType<T> {
    Class<T> getJavaClass();
    @Nullable T read(ResultSet rs, int index) throws SQLException;
    void write(PreparedStatement pstmt, int index, @Nullable T value) throws SQLException;
}
```

`index`는 JDBC 규약대로 1부터 시작하는 컬럼 번호(`read`) 또는 파라미터 번호(`write`)다.

```java
SQLDataType<Instant> type = SQLDataTypes.INSTANT;
type.write(pstmt, 1, Instant.now());          // null이면 setNull
Instant ts = type.read(rs, 1);                // SQL NULL이면 null

SQLDataType<?> t = SQLDataTypes.fromSqlType(meta.getColumnType(1));   // 지원하지 않으면 IAE
FOption<SQLDataType<?>> byName = SQLDataTypes.findByName("Decimal");
FOption<SQLDataType<?>> byClass = SQLDataTypes.findByClass(int.class);   // INTEGER
```

- 인터페이스는 위의 세 메소드를 제공한다. 기본 구현 클래스는 비공개이며 상수만 공개된다. 핸들러의 `toString()`은 타입 이름이다.
- **null 계약:** `write()`의 `value`에 `null`을 전달하면 SQL `NULL`을 설정하고, `read()`는 SQL `NULL`에 대해 `null`을 반환한다(숫자·불리언은 `wasNull()`로 판단).

| `java.sql.Types` | 상수 (타입 이름) | Java 타입 | 읽기/쓰기 |
|---|---|---|---|
| `BIT`, `BOOLEAN` | `BOOLEAN` (`Boolean`) | `Boolean` | `getBoolean`/`setBoolean` |
| — | `SHORT` (`Short`) | `Short` | `getShort`/`setShort` |
| `TINYINT`, `SMALLINT`, `INTEGER` | `INTEGER` (`Integer`) | `Integer` | `getInt`/`setInt` |
| `BIGINT` | `LONG` (`Long`) | `Long` | `getLong`/`setLong` |
| `REAL` | `FLOAT` (`Float`) | `Float` | `getFloat`/`setFloat` |
| `FLOAT`, `DOUBLE` | `DOUBLE` (`Double`) | `Double` | `getDouble`/`setDouble` |
| `NUMERIC`, `DECIMAL` | `DECIMAL` (`Decimal`) | `BigDecimal` | `getBigDecimal`/`setBigDecimal`. 부동소수점 타입으로 변환하지 않으며, 저장 가능한 정밀도와 소수 자릿수는 컬럼 정의에 따름 |
| `CHAR`, `VARCHAR`, `LONGVARCHAR`, `NCHAR`, `NVARCHAR`, `LONGNVARCHAR`, `CLOB`, `NCLOB` | `STRING` (`String`) | `String` | `getString`/`setString`. CLOB도 전체를 메모리로 읽음 |
| `DATE` | `DATE` (`Date`) | `java.util.Date` | `getDate`/`setDate` |
| — | `LOCAL_DATE` (`LocalDate`) | `LocalDate` | `getObject(.., LocalDate.class)`/`setObject`. 시간대 변환 없이 날짜를 읽고 쓴다. 날짜만 다루는 경우에는 `DATE`보다 `LOCAL_DATE` 사용을 권장한다 |
| `TIME` | `TIME` (`Time`) | `LocalTime` | `getObject(.., LocalTime.class)`/`setObject` |
| `TIMESTAMP` | `INSTANT` (`Instant`) | `Instant` | UTC `Calendar`를 넘긴 `getTimestamp`/`setTimestamp` |
| `TIMESTAMP_WITH_TIMEZONE` | `INSTANT_TZ` (`InstantTz`) | `Instant` | `getObject(.., OffsetDateTime.class)`/`setObject(OffsetDateTime(UTC))` |
| `BINARY`, `VARBINARY`, `LONGVARBINARY`, `BLOB` | `BINARY` (`Binary`) | `byte[]` | `getBytes`/`setBytes` |
| — | `DURATION` (`Duration`) | `Duration` | ISO-8601 문자열(`PT1M30S`)로 문자열 컬럼에 저장. 빈 문자열은 `null`, 형식 오류는 `SQLException` |

- **시간대:** `INSTANT`는 `Instant`를 UTC 기준 날짜와 시각으로 변환하여, 시간대 정보가 없는 `TIMESTAMP` 컬럼에 저장한다. 읽을 때도 UTC를 적용하므로, 쓰는 JVM과 읽는 JVM의 기본 시간대가 달라도 같은 `Instant`가 나온다.
- **`TIMESTAMP WITH TIME ZONE` 컬럼에는 `INSTANT_TZ`를 쓴다.** 두 컬럼 종류에 모두 맞는 쓰기 방법이 드라이버마다 달라서 핸들러를 나눴다. `findByClass(Instant.class)`는 `INSTANT`다.
- `fromSqlType(Types.DATE)`는 호환을 위해 `DATE`를 반환한다. `LOCAL_DATE`는 `findByClass(LocalDate.class)`·`findByName("LocalDate")`로 찾는다.
- `findByClass`는 원시 타입(`int.class` 등)과 `java.sql.Date`도 찾는다. `java.sql.Timestamp`·`java.sql.Time`은 등록된 핸들러가 없으므로 빈 `FOption`을 반환한다.
- `fromSqlType`은 표에 없는 타입(`ARRAY`, `STRUCT`, `OTHER`(json·geometry), `TIME_WITH_TIMEZONE` 등)에 `IllegalArgumentException`(예: `Unsupported SQL type code: 2003 (ARRAY)`)을 던진다. 문자열로 다루려면 예외를 잡아 `STRING`을 쓴다.

---

## 6. 테이블 메타데이터

```java
boolean exists = jdbc.tableExists("item");                  // 현재 스키마
boolean other  = jdbc.tableExists("other", "item");
Map<String,ColumnInfo> cols = jdbc.getColumns("item");      // 선언 순서
```

- 스키마를 생략(`null`)하면 연결의 현재 스키마(`Connection.getSchema()`)를 조회한다. 드라이버가 현재 스키마를 반환하지 않거나(`null`) 이 메소드를 지원하지 않으면 모든 스키마를 대상으로 조회한다.
- 이름은 **주어진 그대로 먼저** 찾고, 없으면 DB가 따옴표 없는 식별자를 저장하는 대소문자(H2·Oracle은 대문자, PostgreSQL은 소문자)로 바꿔 다시 찾는다. 명시한 스키마 이름도 같은 규칙으로 찾는다(`tableExists("MixedSchema", "t")`).
- 큰따옴표로 생성한 테이블은 따옴표를 제외하고, 생성 시 사용한 대소문자 그대로 이름을 전달한다. 예를 들어 `"MixedCase"`로 생성한 테이블은 `tableExists("MixedCase")`로 조회한다.
- 이름의 `_`·`%`는 패턴이 아니라 글자 그대로 비교한다.
- `tableExists`는 지정한 이름의 테이블이나 뷰가 존재하면 `true`를 반환한다. `getColumns`는 테이블이 없으면 빈 맵을 반환한다.

---

## 7. `JdbcUtils`

| 메소드 | 설명 |
|---|---|
| `fstream(rs, f)` | ResultSet의 각 행을 `f`로 변환한 `FStream`. 지연 읽기, 닫으면 ResultSet만 닫음(3장) |
| `toColumnObjectList(rs)` | 현재 행의 모든 컬럼을 `getObject`로 읽은 `List<Object>` |
| `maskPassword(url)` | URL의 `password=`·`passwd=`·`pwd=` 값과 `//user:pw@host`의 비밀번호를 `****`로 가림. 로그·예외 메시지용 |
| `bindToConnection(rs, stmt, conn)` | ResultSet을 닫을 때 주어진 Statement·연결도 한 번만 닫는 JDK 동적 프록시(`ResultSet` 인터페이스만 구현하므로 드라이버 고유 클래스가 필요하면 `unwrap()`을 사용한다). 원본 ResultSet·Statement가 먼저 닫혀도 연결까지 닫는다 |
| `bindToConnection(rs)` | 위와 같고, Statement·연결은 호출 시점에 `rs.getStatement()`·`getConnection()`으로 얻는다. `rs`가 열려 있어야 한다 |
| `toTimestamp(instant)` | `Timestamp` 변환 |
| `toPostgresInterval(duration)` | `"1 days 2 hours 3 minutes 4.5 seconds"` 형식. 초 미만은 소수 초로 남긴다 |

`JdbcProcessor.toString()`·`JdbcConfiguration.toString()`도 비밀번호를 출력하지 않는다(`JdbcProcessor[url=…, user=…, driver=…]`). 이 문자열은 디버깅·로깅용이며 다시 파싱해 사용하는 용도가 아니다.

---

## 8. 테스트

JDBC 테스트는 인메모리 H2 데이터베이스(`testImplementation com.h2database:h2`)를 사용하여 연결, SQL 실행, 타입 변환과 메타데이터 조회를 검증한다. 예: [`JdbcProcessorH2Test`](../../../../test/java/utils/jdbc/JdbcProcessorH2Test.java)(누수·예외 정책·URL 자격 증명), [`SQLDataTypesH2Test`](../../../../test/java/utils/jdbc/SQLDataTypesH2Test.java)(타입별 왕복·시간대), [`JdbcProcessorMetadataH2Test`](../../../../test/java/utils/jdbc/JdbcProcessorMetadataH2Test.java)(메타데이터·식별자·풀 닫기).

```bash
gradle test --tests 'utils.jdbc.*'
```
