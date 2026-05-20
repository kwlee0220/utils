package utils.stream;


import java.util.concurrent.CancellationException;

import utils.Preconditions;
import utils.Suppliable;
import utils.func.FOption;
import utils.stream.FStreams.AbstractFStream;


/**
 * {@link Generator}가 별도 쓰레드에서 생성하는 데이터를 bounded buffer를 통해 소비하는 {@link FStream}.
 * <p>
 * 첫 {@code next()} 호출 시점에 generator 쓰레드가 시작되어 채널로 데이터를 push하며, 소비자는
 * {@code next()}로 채널에서 pull한다. {@code close()} 호출 시 generator 쓰레드를 인터럽트하고
 * 채널을 닫는다.
 * <p>
 * <b>본 클래스는 단일 consumer만 지원한다.</b> {@code next()} / {@code close()}의 동시 호출은
 * 동기화되지 않는다. 다수의 쓰레드가 동시에 호출하면 generator 쓰레드의 중복 시작, 데이터 중복,
 * race 등이 발생할 수 있다.
 *
 * @param <T>	스트림 원소 타입
 * @author Kang-Woo Lee (ETRI)
 */
class GeneratorBasedFStream<T> extends AbstractFStream<T> {
	private final Generator<T> m_dataGenerator;
	private final SuppliableFStream<T> m_channel;
	private final String m_threadName;
	private Thread m_generatorThread;

	/**
	 * 지정된 {@link Generator}와 buffer capacity로 {@link GeneratorBasedFStream}을 생성한다.
	 *
	 * @param dataGenerator	데이터를 생성할 {@link Generator}.
	 * @param length	내부 buffer capacity. 양의 정수여야 한다.
	 * @param threadName	generator 쓰레드 이름. {@code null}인 경우 {@code "generator"}가 사용된다.
	 * @throws IllegalArgumentException	{@code dataGenerator}가 {@code null}이거나 {@code length}가 {@code 0} 이하인 경우.
	 */
	GeneratorBasedFStream(Generator<T> dataGenerator, int length, String threadName) {
		Preconditions.checkNotNullArgument(dataGenerator, "generator is null");
		Preconditions.checkArgument(length > 0, "Buffer length should be larger than zero.");

		m_dataGenerator = dataGenerator;
		m_channel = new SuppliableFStream<>(length);
		m_threadName = threadName;
	}

	/**
	 * 기본 쓰레드 이름({@code "generator"})으로 {@link GeneratorBasedFStream}을 생성한다.
	 *
	 * @param dataGenerator	데이터를 생성할 {@link Generator}.
	 * @param length	내부 buffer capacity. 양의 정수여야 한다.
	 * @throws IllegalArgumentException	{@code dataGenerator}가 {@code null}이거나 {@code length}가 {@code 0} 이하인 경우.
	 */
	GeneratorBasedFStream(Generator<T> dataGenerator, int length) {
		this(dataGenerator, length, null);
	}

	/**
	 * 첫 {@code next()} 호출 시점에 호출되어 generator 쓰레드를 daemon으로 시작한다.
	 * <p>
	 * 생성된 쓰레드는 daemon으로 설정되므로 JVM shutdown을 막지 않는다.
	 */
	@Override
	protected void initialize() {
		m_generatorThread = new Thread(new DataGeneratingWorker<>(m_dataGenerator, m_channel),
										m_threadName != null ? m_threadName : "generator");
		m_generatorThread.setDaemon(true);
		m_generatorThread.start();
	}

	/**
	 * generator 쓰레드를 인터럽트하고 내부 채널을 닫는다.
	 * <p>
	 * 본 메소드는 generator 쓰레드의 종료를 기다리지 않고 즉시 반환한다. 의도적인 동작으로,
	 * generator의 잔여 작업이 cleanup에 시간이 걸리더라도 close 호출자가 블로킹되지 않는다.
	 * 채널이 닫힌 후 generator는 자신의 다음 {@code supply} 호출 시점에 중단된다.
	 * 호출자가 generator 쓰레드의 완전한 종료까지 보장해야 한다면 별도의 동기화 수단을 도입해야 한다.
	 */
	@Override
	protected void closeInGuard() {
		if ( m_generatorThread != null ) {
			m_generatorThread.interrupt();
		}
		m_channel.close();
	}

	/**
	 * 내부 채널에서 다음 원소를 읽어 반환한다.
	 *
	 * @return	다음 원소. 스트림이 종료된 경우 {@link FOption#empty()}.
	 */
	@Override
	public FOption<T> nextInGuard() {
		return m_channel.next();
	}

	/**
	 * {@link Generator}를 호출하여 채널로 데이터를 push하는 worker.
	 */
	private static class DataGeneratingWorker<T> implements Runnable {
		private final Generator<T> m_dataGenerator;
		private final Suppliable<T> m_outChannel;

		DataGeneratingWorker(Generator<T> dataGenerator, Suppliable<T> channel) {
			m_dataGenerator = dataGenerator;
			m_outChannel = channel;
		}

		/**
		 * Generator를 호출하여 채널로 데이터를 push한다.
		 * <p>
		 * 다음과 같이 분기된다:
		 * <ul>
		 *   <li>generator가 정상 종료된 경우: {@link Suppliable#endOfSupply()}로 정상 종료시킨다.</li>
		 *   <li>generator가 인터럽트({@link InterruptedException}) 또는 취소({@link CancellationException})된 경우:
		 *       정상 종료로 처리한다.</li>
		 *   <li>채널이 consumer 측에서 이미 닫혀({@link IllegalStateException}) supply가 실패한 경우:
		 *       정상 종료로 처리한다.</li>
		 *   <li>그 외 {@link Exception}이 발생한 경우: {@link Suppliable#endOfSupply(Throwable)}로 에러 종료시킨다.
		 *       소비자가 후속 {@code next()}를 호출하면 등록된 에러가 RuntimeException으로 래핑되어 던져진다.</li>
		 * </ul>
		 * {@link Error}는 catch하지 않고 JVM에 전파된다.
		 */
		@Override
		public void run() {
			try {
				m_dataGenerator.generate(m_outChannel);
				m_outChannel.endOfSupply();
			}
			catch ( InterruptedException | CancellationException expected ) {
				m_outChannel.endOfSupply();
			}
			catch ( IllegalStateException expected ) {
				// 채널이 이미 닫힌 경우, 데이터 생성 중단
				m_outChannel.endOfSupply();
			}
			catch ( Exception e ) {
				m_outChannel.endOfSupply(e);
			}
		}
	}
}
