package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CancellationException;
import java.util.function.Function;


import utils.Preconditions;
import utils.Tuple;
import utils.async.AbstractThreadedExecution;
import utils.async.AsyncResult;
import utils.async.Execution;
import utils.func.CheckedConsumer;

/**
 * {@link OutputStream}에 데이터를 쓰는 작업(pump)의 출력을 pipe를 통해 읽을 수 있게 하는 {@link InputStream}이다.
 * <p>
 * pump 함수는 pipe의 출력 측 {@link OutputStream}을 받아 데이터를 기록하는 {@link Execution}을
 * 반환해야 하며, 본 스트림은 pipe의 입력 측에서 데이터를 읽는다. pump 작업에는 다음 조건이 있다.
 * <ul>
 *   <li>읽는 스레드와 다른 스레드에서 수행되어야 한다.</li>
 *   <li>기록을 마치거나 실패하면 출력 스트림을 닫아야 한다. 닫지 않으면 읽는 쪽이 EOF를 보지 못한다.</li>
 *   <li>출력 스트림을 닫은 뒤에는 곧 끝나야 한다. 읽는 쪽은 EOF를 본 시점에 pump가 끝나기를 기다린다.</li>
 * </ul>
 * pipe에서 EOF를 읽으면 pump의 결과로 정상 종료인지 판정한다.
 * <ul>
 *   <li>pump가 성공했으면 {@code -1}(EOF)을 반환한다.</li>
 *   <li>pump가 실패했으면 그 원인을 {@link IOException}으로 던진다. 원인이 {@link IOException}이면 그대로 던진다.</li>
 *   <li>pump가 취소되었으면 데이터가 잘렸으므로 {@link IOException}을 던진다(원인은 {@link CancellationException}).</li>
 * </ul>
 * 그래서 pump가 출력 스트림을 닫은 뒤에 실패하더라도, 잘린 데이터가 정상 종료처럼 읽히지 않는다.
 * pipe 자체의 오류(예: pump가 출력 스트림을 닫지 않고 끝나 생기는 {@code "Write end dead"})가 났을 때 pump가
 * 이미 실패로 끝나 있으면, pump의 원인을 던지고 pipe 오류는 suppressed로 덧붙인다.
 * <p>
 * {@link #close()}는 pump 작업을 취소하고 pipe를 닫는다.
 * <p>
 * 대부분의 경우 위 조건을 직접 맞출 필요 없이 {@link #of(CheckedConsumer)}에 쓰기 함수만 넘기면 된다.
 * 생성자는 pump를 직접 만들어야 하는 경우(실행기·취소 방식을 정하는 경우)에 쓴다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class InputStreamFromOutputStream extends InputStream {
	private static final int DEFAULT_PIPE_SIZE = 32 * 1024;

	private final PipedInputStream m_pipe;
	private final Execution<Void> m_pump;

	/**
	 * 쓰기 함수의 출력을 읽는 입력 스트림을 만든다.
	 * <p>
	 * 쓰기 함수는 별도 스레드에서 pipe의 출력 측 스트림을 받아 실행된다. 함수가 끝나면(실패해도) 출력 스트림은
	 * 자동으로 닫히므로 함수가 닫을 필요가 없다. 함수가 던진 예외는 읽는 쪽 {@code read}에서
	 * {@link IOException}으로 전달된다. {@link #close()}로 일찍 닫으면 pipe가 닫혀 쓰기 함수의 다음
	 * {@code write}가 {@link IOException}으로 실패하고 끝난다.
	 *
	 * @param writer	출력 스트림에 데이터를 기록하는 함수. {@code null}이면 안 된다.
	 * @return	쓰기 함수의 출력을 읽는 입력 스트림.
	 */
	public static InputStreamFromOutputStream of(CheckedConsumer<OutputStream> writer) {
		return of(writer, DEFAULT_PIPE_SIZE);
	}

	/**
	 * {@link #of(CheckedConsumer)}와 같되 pipe 버퍼 크기를 지정한다.
	 *
	 * @param writer	출력 스트림에 데이터를 기록하는 함수. {@code null}이면 안 된다.
	 * @param pipeSize	pipe 버퍼 크기(byte). 0보다 커야 한다.
	 * @return	쓰기 함수의 출력을 읽는 입력 스트림.
	 */
	public static InputStreamFromOutputStream of(CheckedConsumer<OutputStream> writer, int pipeSize) {
		Preconditions.checkNotNull(writer, "writer is null");

		return new InputStreamFromOutputStream(os -> {
			WriterPump pump = new WriterPump(writer, os);
			pump.start();
			return pump;
		}, pipeSize);
	}

	private static final class WriterPump extends AbstractThreadedExecution<Void> {
		private final CheckedConsumer<OutputStream> m_writer;
		private final OutputStream m_out;

		WriterPump(CheckedConsumer<OutputStream> writer, OutputStream out) {
			m_writer = writer;
			m_out = out;
		}

		@Override
		protected Void executeWork() throws Exception {
			try ( OutputStream out = m_out ) {
				m_writer.accept(out);
			}
			return null;
		}
	}

	/**
	 * 주어진 pump 함수와 pipe 버퍼 크기로 입력 스트림을 생성한다.
	 * 생성 과정에서 pump 함수가 즉시 호출된다.
	 *
	 * @param pump	pipe 출력 스트림을 받아 데이터를 기록하는 {@link Execution}을 반환하는 함수.
	 * @param pipeSize	pipe 버퍼 크기(byte). 0보다 커야 한다.
	 * @throws NullPointerException	{@code pump}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	{@code pipeSize}가 0 이하인 경우.
	 * @throws IllegalStateException	pump 함수가 예외를 던지거나 {@code null}을 반환한 경우. 원인 예외를 cause로
	 * 									갖는다. 이때 pipe는 양쪽 모두 닫힌다.
	 */
	public InputStreamFromOutputStream(Function<OutputStream,Execution<Void>> pump, int pipeSize) {
		Preconditions.checkNotNull(pump, "pump is null");
		Preconditions.checkArgument(pipeSize > 0, "invalid pipe size: " + pipeSize);

		Tuple<PipedOutputStream, PipedInputStream> pipe = IOUtils.pipe(pipeSize);
		Execution<Void> exec;
		try {
			exec = pump.apply(pipe._1);
		}
		catch ( RuntimeException e ) {
			closePipe(pipe);
			throw new IllegalStateException("failed to start the pump: cause=" + e, e);
		}
		if ( exec == null ) {
			closePipe(pipe);
			throw new IllegalStateException("pump function returned null");
		}

		m_pipe = pipe._2;
		m_pump = exec;
	}

	/**
	 * 주어진 pump 함수와 기본 pipe 버퍼 크기(32KB)로 입력 스트림을 생성한다.
	 * 생성 과정에서 pump 함수가 즉시 호출된다.
	 *
	 * @param pump	pipe 출력 스트림을 받아 데이터를 기록하는 {@link Execution}을 반환하는 함수.
	 * @throws NullPointerException	{@code pump}가 {@code null}인 경우.
	 * @throws IllegalStateException	pump 함수가 예외를 던지거나 {@code null}을 반환한 경우.
	 */
	public InputStreamFromOutputStream(Function<OutputStream,Execution<Void>> pump) {
		this(pump, DEFAULT_PIPE_SIZE);
	}

	@Override
	public void close() throws IOException {
		m_pump.cancel(true);
		m_pipe.close();
	}

	@Override
	public int read() throws IOException {
		int ret;
		try {
			ret = m_pipe.read();
		}
		catch ( IOException pipeError ) {
			throw pumpFailureOr(pipeError);
		}
		return (ret >= 0) ? ret : checkEndOfStream();
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int ret;
		try {
			ret = m_pipe.read(b, off, len);
		}
		catch ( IOException pipeError ) {
			throw pumpFailureOr(pipeError);
		}
		return (ret >= 0) ? ret : checkEndOfStream();
	}

	/**
	 * pipe의 EOF가 pump의 정상 완료에 따른 것인지 확인한다.
	 *
	 * @return	pump가 성공했으면 {@code -1}.
	 * @throws IOException	pump가 실패했거나 취소된 경우.
	 * @throws InterruptedIOException	pump의 종료를 기다리는 중에 인터럽트된 경우. 인터럽트 상태는 복원된다.
	 */
	private int checkEndOfStream() throws IOException {
		AsyncResult<Void> result;
		try {
			result = m_pump.waitForFinished();
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			InterruptedIOException iioe
					= new InterruptedIOException("interrupted while waiting for the pump to finish");
			iioe.initCause(e);
			throw iioe;
		}

		if ( result.isCompleted() ) {
			return -1;
		}
		else if ( result.isFailed() ) {
			throw toIOException(result.getFailureCause());
		}
		else {
			throw new IOException("pump was cancelled: the stream is truncated", new CancellationException());
		}
	}

	/**
	 * pipe 읽기에서 오류가 났을 때 던질 예외를 고른다. pump가 이미 실패로 끝났으면 그 원인을 던지고
	 * pipe 오류를 suppressed로 붙인다. 그렇지 않으면 pipe 오류를 그대로 던진다.
	 */
	private IOException pumpFailureOr(IOException pipeError) {
		AsyncResult<Void> result = m_pump.poll();
		if ( result.isFailed() ) {
			IOException error = toIOException(result.getFailureCause());
			if ( error != pipeError ) {
				error.addSuppressed(pipeError);
			}
			return error;
		}
		return pipeError;
	}

	private static IOException toIOException(Throwable error) {
		if ( error instanceof Error err ) {
			throw err;
		}
		if ( error instanceof IOException ioe ) {
			return ioe;
		}
		return new IOException("pump failed: cause=" + error, error);
	}

	private static void closePipe(Tuple<PipedOutputStream, PipedInputStream> pipe) {
		IOUtils.closeQuietly(pipe._1);
		IOUtils.closeQuietly(pipe._2);
	}
}
