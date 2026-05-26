package utils.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LogTailerTest {
	private static final Duration SHORT = Duration.ofMillis(50);
	private static final long WAIT_MS = 5_000;

	@TempDir
	public Path tmpFolder;

	// ----- Builder 검증 -----

	@Test
	public void buildWithoutFileThrows() {
		Assertions.assertThrows(IllegalStateException.class, () -> LogTailer.builder().build());
	}

	@Test
	public void runWithoutListenerThrows() throws Exception {
		File logFile = newLogFile("log.txt", "");
		LogTailer tailer = LogTailer.builder().file(logFile).sampleInterval(SHORT).build();
		Assertions.assertThrows(IllegalStateException.class, tailer::run);
	}

	@Test
	public void builderRejectsNullArgs() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> LogTailer.builder().file(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> LogTailer.builder().sampleInterval(null));
	}

	// ----- 기본 읽기 -----

	@Test
	@Timeout(10)
	public void readsExistingLinesFromBeginning() throws Exception {
		File logFile = newLogFile("log.txt", "a\nb\nc\n");
		TestListener listener = new TestListener(3, 0);

		LogTailer tailer = LogTailer.builder()
									.file(logFile)
									.startAtBeginning(true)
									.sampleInterval(SHORT)
									.build();
		tailer.addLogTailerListener(listener);
		tailer.run();		// listener가 3줄째에서 false를 반환하므로 정상 종료한다.

		Assertions.assertEquals(List.of("a", "b", "c"), listener.lines);
	}

	@Test
	@Timeout(10)
	public void decodesUtf8MultibyteLines() throws Exception {
		File logFile = newLogFile("log.txt", "안녕하세요\n세계\n");
		TestListener listener = new TestListener(2, 0);

		LogTailer tailer = LogTailer.builder()
									.file(logFile)
									.startAtBeginning(true)
									.sampleInterval(SHORT)
									.build();
		tailer.addLogTailerListener(listener);
		tailer.run();

		Assertions.assertEquals(List.of("안녕하세요", "세계"), listener.lines);
	}

	@Test
	@Timeout(10)
	public void tailFromEndIgnoresExistingContent() throws Exception {
		File logFile = newLogFile("log.txt", "old1\nold2\n");
		TestListener listener = new TestListener(1, 0);

		LogTailer tailer = LogTailer.builder()
									.file(logFile)
									.startAtBeginning(false)		// 기본값: 끝부터 읽는다.
									.sampleInterval(SHORT)
									.build();
		tailer.addLogTailerListener(listener);

		AtomicReference<Throwable> err = new AtomicReference<>();
		Thread t = start(tailer, err);
		try {
			// tailer가 최소 한 번 polling하여 "변화 없음"을 관측할 때까지 대기한다.
			waitUntil(() -> listener.silences.get() >= 1);
			append(logFile, "new1\n");

			waitUntil(() -> !listener.lines.isEmpty());
			Assertions.assertEquals(List.of("new1"), listener.lines);
		}
		finally {
			join(t);
		}
		Assertions.assertNull(err.get());
	}

	// ----- 미완성 줄 처리 (#2) -----

	@Test
	@Timeout(10)
	public void partialLineNotDeliveredUntilNewline() throws Exception {
		File logFile = newLogFile("log.txt", "");
		TestListener listener = new TestListener(1, 0);

		LogTailer tailer = LogTailer.builder()
									.file(logFile)
									.startAtBeginning(true)
									.sampleInterval(SHORT)
									.build();
		tailer.addLogTailerListener(listener);

		AtomicReference<Throwable> err = new AtomicReference<>();
		Thread t = start(tailer, err);
		try {
			// 개행 없는 미완성 조각을 기록한다.
			int base = listener.silences.get();
			append(logFile, "partial");

			// tailer가 미완성 조각을 본 뒤(추가 polling 2회 이상) 아무 줄도 전달하지 않았음을 확인한다.
			waitUntil(() -> listener.silences.get() >= base + 2);
			Assertions.assertTrue(listener.lines.isEmpty(),
									"미완성 줄이 조기에 전달됨: " + listener.lines);

			// 개행을 붙여 줄을 완성하면 앞 조각과 합쳐져 한 줄로 전달된다.
			append(logFile, "rest\n");
			waitUntil(() -> !listener.lines.isEmpty());
			Assertions.assertEquals(List.of("partialrest"), listener.lines);
		}
		finally {
			join(t);
		}
		Assertions.assertNull(err.get());
	}

	// ----- rewind 감지 -----

	@Test
	@Timeout(10)
	public void rewindReopensAndNotifies() throws Exception {
		File logFile = newLogFile("log.txt", "a\nb\n");
		TestListener listener = new TestListener(3, 0);

		LogTailer tailer = LogTailer.builder()
									.file(logFile)
									.startAtBeginning(true)
									.sampleInterval(SHORT)
									.build();
		tailer.addLogTailerListener(listener);

		AtomicReference<Throwable> err = new AtomicReference<>();
		Thread t = start(tailer, err);
		try {
			// 기존 두 줄을 모두 읽을 때까지 대기한 뒤 파일을 truncate하여 rewind를 유발한다.
			waitUntil(() -> listener.lines.size() >= 2);
			truncate(logFile, "c\n");

			waitUntil(() -> listener.lines.size() >= 3);
			Assertions.assertEquals(List.of("a", "b", "c"), listener.lines);
			Assertions.assertTrue(listener.rewinds.get() >= 1, "rewind가 통지되지 않음");
		}
		finally {
			join(t);
		}
		Assertions.assertNull(err.get());
	}

	// ----- silence / timeout -----

	@Test
	@Timeout(10)
	public void silenceReportedWhenNoChange() throws Exception {
		File logFile = newLogFile("log.txt", "");
		TestListener listener = new TestListener(0, 3);		// silence 3회째에 종료한다.

		LogTailer tailer = LogTailer.builder()
									.file(logFile)
									.startAtBeginning(true)
									.sampleInterval(SHORT)
									.build();
		tailer.addLogTailerListener(listener);
		tailer.run();

		Assertions.assertTrue(listener.silences.get() >= 3);
		Assertions.assertTrue(listener.lines.isEmpty());
	}

	@Test
	@Timeout(10)
	public void timeoutThrows() throws Exception {
		File logFile = newLogFile("log.txt", "");
		TestListener listener = new TestListener(0, 0);		// 스스로 종료하지 않는다.

		LogTailer tailer = LogTailer.builder()
									.file(logFile)
									.startAtBeginning(true)
									.sampleInterval(SHORT)
									.timeout(Duration.ofMillis(200))
									.build();
		tailer.addLogTailerListener(listener);

		Assertions.assertThrows(TimeoutException.class, tailer::run);
	}

	// ----- 헬퍼 -----

	private File newLogFile(String name, String content) throws IOException {
		Path path = tmpFolder.resolve(name);
		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
		return path.toFile();
	}

	private static void append(File f, String s) throws IOException {
		Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	private static void truncate(File f, String s) throws IOException {
		// CREATE + TRUNCATE_EXISTING + WRITE (기본) — 기존 내용을 비우고 새로 쓴다.
		Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
	}

	private static Thread start(LogTailer tailer, AtomicReference<Throwable> err) {
		Thread t = new Thread(() -> {
			try {
				tailer.run();
			}
			catch ( Throwable e ) {
				err.set(e);
			}
		}, "log-tailer-test");
		t.setDaemon(true);
		t.start();
		return t;
	}

	private static void join(Thread t) throws InterruptedException {
		t.interrupt();
		t.join(WAIT_MS);
	}

	private static void waitUntil(BooleanSupplier cond) throws InterruptedException {
		long deadline = System.currentTimeMillis() + WAIT_MS;
		while ( !cond.getAsBoolean() ) {
			if ( System.currentTimeMillis() > deadline ) {
				Assertions.fail("조건이 " + WAIT_MS + "ms 안에 충족되지 않음");
			}
			Thread.sleep(10);
		}
	}

	private static class TestListener implements LogTailerListener {
		final List<String> lines = new CopyOnWriteArrayList<>();
		final AtomicInteger silences = new AtomicInteger();
		final AtomicInteger rewinds = new AtomicInteger();
		private final int m_stopAfterLines;		// <=0이면 줄 수로 종료하지 않는다.
		private final int m_stopAfterSilences;		// <=0이면 silence로 종료하지 않는다.

		TestListener(int stopAfterLines, int stopAfterSilences) {
			m_stopAfterLines = stopAfterLines;
			m_stopAfterSilences = stopAfterSilences;
		}

		@Override
		public boolean handleLogTail(String line) {
			lines.add(line);
			return !(m_stopAfterLines > 0 && lines.size() >= m_stopAfterLines);
		}

		@Override
		public boolean logFileRewinded(File file) {
			rewinds.incrementAndGet();
			return true;
		}

		@Override
		public boolean handleLogFileSilence(Duration interval) {
			int n = silences.incrementAndGet();
			return !(m_stopAfterSilences > 0 && n >= m_stopAfterSilences);
		}
	}
}
