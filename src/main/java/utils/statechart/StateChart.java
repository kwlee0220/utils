package utils.statechart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import utils.Throwables;
import utils.async.AbstractAsyncExecution;
import utils.async.AsyncState;
import utils.async.CancellableWork;
import utils.func.Unchecked;
import utils.thread.CallerRunsSerialExecutor;
import utils.thread.CallerRunsSerialExecutor.Invocation;
import utils.thread.Guard;


/**
 * 신호로 구동되는 계층적 상태 차트.
 * <p>
 * {@link AbstractAsyncExecution}을 상속하기 때문에 차트 자체가 하나의 비동기 실행이다. 즉
 * {@code Future}처럼 종료를 기다릴 수 있고({@code waitForFinished()}), 종료 리스너를 등록할 수 있으며
 * ({@code whenFinished(...)}), 취소할 수 있다({@code cancel(true)}). 상태 전이의 결과가 곧 실행의
 * 성공/실패/취소로 드러난다.
 * <p>
 * <b>구성 순서</b>는 다음과 같다. 상태 등록 계열 메소드는 차트가 시작되기 전에만 호출할 수 있다.
 * <ol>
 *   <li>{@code new StateChart<>(context)} — 컨텍스트에 자기 자신이 자동으로 등록된다.</li>
 *   <li>{@link #addState(utils.statechart.State)}로 모든 상태를 등록한다.</li>
 *   <li>{@link #setInitialState(String)}로 시작 상태를 지정한다. {@link TerminalState}(와 {@link FailureState})는
 *       등록할 때 자동으로 최종 상태가 되며, 그 밖의 상태를 최종 상태로 쓰려면 {@link #addFinalState(String)}로
 *       지정한다. 시작 상태는 최종 상태일 수 없다.</li>
 *   <li>{@link #start()}로 시작하면 시작 상태에 진입하고 {@code RUNNING}이 된다.</li>
 *   <li>{@link #handleSignal(Signal)}로 신호를 넣으면 현재 상태가 전이를 선택하고,
 *       선택된 전이를 따라 이동한다.</li>
 * </ol>
 * <p>
 * <b>계층 구성</b>: 상태의 위치 경로가 부모·자식 관계를 정한다. {@code "/Running/Idle"}의 부모는
 * {@code "/Running"}이며, 부모는 등록된 {@link CompositeState}여야 한다. 차트는 루트부터 가장 안쪽 상태까지
 * 진입해 있는 상태들의 체인({@link #getActiveStates()})을 관리한다.
 * <ul>
 *   <li>복합 상태에 진입하면 그 최초 하위 상태({@link CompositeState#getInitialStatePath()})까지 이어서 진입한다.</li>
 *   <li>신호는 가장 안쪽 상태부터 바깥쪽으로 차례로 물어, 처음 선택된 전이를 따른다.</li>
 *   <li>전이할 때는 현재 체인과 목표 상태의 체인이 공유하는 조상은 그대로 두고, 그 아래의 상태만
 *       안쪽부터 진출한 뒤 바깥쪽부터 진입한다. 목표가 이미 진입해 있는 상태이면 그 상태도 진출했다가
 *       다시 진입한다.</li>
 *   <li>하위 상태인 최종 상태에 도달하면, 차트를 종료하기 전에 그 바깥 상태들을 안쪽부터 진출한다.
 *       취소할 때 모든 상태를 진출하는 것과 같이, 복합 상태의 {@code exit()}에 둔 정리 작업이 실행된다.</li>
 * </ul>
 * 경로에 {@code '/'}가 한 번만 있는 상태({@code "/Running"})만 쓰면 기존과 같은 평면 상태 차트가 된다.
 * <p>
 * <b>종료 방식은 두 가지다.</b> 최종 상태에 도달하면 차트가 스스로 종료한다 — 보통은
 * {@code complete()}이고, 도달한 상태가 {@link FailureState}이면 그 상태가 보유한 원인으로
 * {@code fail()}된다. 이와 별개로 {@link #complete()} / {@link #fail(Throwable)} /
 * {@code cancel(true)}를 직접 호출해 밖에서 끝낼 수도 있다. 이 경우는 상태 전이를 거치지 않으므로
 * 어떤 상태의 {@code enter()}도 실행되지 않는다는 점에 주의한다.
 * <p>
 * <b>동시성</b>: 구성, 시작, 신호 처리, 종료는 모두 하나의 {@link Guard}({@link #getChartLock()})로
 * 직렬화된다. 구성 메소드({@link #addState(utils.statechart.State)}, {@link #setInitialState(String)},
 * {@link #addFinalState(String)})도 이 lock 안에서 실행되므로, 구성한 스레드와 {@link #start()}를 호출하는
 * 스레드가 달라도 구성 결과가 보인다. 다만 구성 도중에 다른 스레드가 시작하면 구성이 덜 된 채 검사에 걸릴 수
 * 있으므로, 구성을 모두 마친 뒤 시작한다. 시작 후에는 구성이 바뀌지 않는다. 이 lock은 부모가 실행 상태를 보호하는 lock과는 별개다. 상태의
 * {@code enter()}/{@code exit()}와 전이 액션은 모두 이 lock을 보유한 채 실행되므로, 오래 걸리는
 * 작업을 넣으면 그동안 다른 신호가 처리되지 못한다.
 * <p>
 * 동기 종료 리스너({@code whenFinished})도 시작 전 취소를 제외한 모든 종료 경로에서 이 lock과 부모의
 * 실행 상태 lock을 함께 보유한 채 실행된다. lock은 항상 이 차트의 lock → 실행 상태 lock 순서로 잡는다.
 * 리스너에서 블록될 수 있는 작업은 {@code whenFinishedAsync}로 등록한다.
 *
 * @param <C>	상태 컨텍스트 타입. (CRTP self-bound)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StateChart<C extends StateContext<C>> extends AbstractAsyncExecution<C>
												implements CancellableWork {
	private static final Logger s_logger = LoggerFactory.getLogger(StateChart.class);

	private final C m_context;
	// 등록(addState)은 m_guard 안에서 하지만, 조회(findState)는 lock 없이 하므로 동시 접근에 안전한 맵을 쓴다.
	private final Map<String,utils.statechart.State<C>> m_states = new ConcurrentHashMap<>();

	private final Guard m_guard = Guard.create();
	// 쓰기는 m_guard 안에서, 읽기는 getInitialState()에서 lock 없이 한다.
	private volatile utils.statechart.State<C> m_initialState;
	private final Set<utils.statechart.State<C>> m_finalStates = Sets.newHashSet();
	// 진입해 있는 상태들. 루트 쪽 상태부터 가장 안쪽 상태 순서. m_guard로 보호한다.
	private final List<utils.statechart.State<C>> m_activeStateChain = new ArrayList<>();
	// m_activeStateChain의 마지막 원소(가장 안쪽 상태). 쓰기는 항상 m_guard 안에서 한다.
	// toString()처럼 lock 없이 읽어야 하는 곳이 있어 volatile로 둔다.
	private volatile utils.statechart.State<C> m_currentState;
	private volatile Logger m_logger = s_logger;
	// 로그와 toString()에서 차트를 구분하는 이름. null이면 m_defaultName(클래스 이름@식별 해시)을 쓴다.
	private volatile String m_name;
	private final String m_defaultName;

	// 신호 큐. 신호는 넣은 순서대로, 한 번에 한 스레드(dispatcher)만 처리한다. 신호 처리는 m_guard를 잡으므로
	// m_guard를 바깥 lock으로 지정한다. 실행기의 큐 lock은 m_guard를 잡은 채로 잡을 수 있지만, 그 반대 순서로는
	// 잡지 않는다(실행기는 큐 lock을 놓은 뒤 작업을 실행한다).
	private final CallerRunsSerialExecutor m_signalExecutor = new CallerRunsSerialExecutor(m_guard);
	// 종료 후 버린 신호를 INFO로 한 번 기록했는지 여부. m_guard로 보호한다.
	private boolean m_lateSignalLogged = false;

	// 종료 요청은 "접수 → 콜백 반환 → 자원 정리 → 종료 통지" 순서로 처리한다. 콜백 구간(시작 진입, 신호 처리,
	// 종료 정리) 안에서 같은 스레드가 종료를 요청하면 m_pendingTermination에 기록만 하고, 콜백이 반환된 뒤
	// applyPendingTermination()이 정리와 함께 확정한다. 처음 요청만 기록하므로, 정리 중 exit()에서 들어온
	// 종료 요청은 이미 결정된 종료를 바꾸지 못한다. 둘 다 m_guard로 보호한다.
	private @Nullable Termination m_pendingTermination;
	private int m_callbackDepth = 0;

	private enum TerminationKind { CANCEL, COMPLETE, FAIL }
	/** 기록된 종료 요청. {@code exitStates}이면 확정하기 전에 진입해 있는 상태들을 안쪽부터 진출시킨다. */
	private record Termination(TerminationKind kind, @Nullable Throwable cause, boolean exitStates) { }

	/**
	 * 주어진 상태 컨텍스트로 상태 차트를 생성한다.
	 * <p>
	 * 생성과 동시에 컨텍스트에 자기 자신을 등록하여({@link StateContext#setStateChart(StateChart)})
	 * 양방향 참조를 맺는다. 따라서 하나의 컨텍스트를 두 개의 차트에 넘길 수 없다.
	 *
	 * @param context	상태 컨텍스트. 다른 차트에 등록된 적이 없어야 한다.
	 * @throws NullPointerException		{@code context}가 {@code null}인 경우.
	 * @throws IllegalStateException	{@code context}가 이미 다른 차트에 등록된 경우.
	 */
	public StateChart(C context) {
		utils.Preconditions.checkNotNull(context, "StateContext is null");

		// setStateChart()가 이 차트를 출력할 수 있으므로 이름을 먼저 정한다.
		String cls = getClass().getSimpleName();
		// 익명 하위 클래스이면 이름이 비어 있다.
		m_defaultName = (cls.isEmpty() ? StateChart.class.getSimpleName() : cls)
						+ "@" + Integer.toHexString(System.identityHashCode(this));

		m_context = context;
		m_context.setStateChart(this);
	}

	/**
	 * 본 차트의 상태 전이를 직렬화하는 lock을 반환한다.
	 * <p>
	 * 하위 클래스가 차트 상태와 함께 갱신되어야 하는 자체 데이터를 보호할 때 사용한다.
	 * 이 lock은 재진입 가능하므로, 이미 lock을 보유한 콜백(상태의 {@code enter()}, 전이 액션 등)
	 * 안에서 다시 획득해도 무방하다. 문서에서 "차트 lock"이라 부르는 것이 이 lock이다.
	 * (2026-10-31 이전 이름은 {@code getMachineLock()}이었다.)
	 *
	 * @return	차트 lock.
	 */
	protected Guard getChartLock() {
		return m_guard;
	}

	/**
	 * 본 차트의 상태 컨텍스트를 반환한다.
	 *
	 * @return	상태 컨텍스트. ({@code null} 아님)
	 */
	public C getContext() {
		return m_context;
	}

	/**
	 * 주어진 위치 경로로 등록된 상태를 찾는다.
	 * <p>
	 * 차트의 실행 상태({@code RUNNING} 등)는 부모로부터 물려받은 {@link #getState()}가 반환한다.
	 * 반드시 있어야 하는 상태라면 {@code findState(path).orElseThrow()}로 쓴다.
	 * <p>
	 * 차트 lock을 잡지 않으므로 어느 스레드에서, 어떤 lock을 쥔 채 불러도 기다리지 않는다.
	 *
	 * @param path	찾을 상태의 위치 경로.
	 * @return	해당 경로로 등록된 상태. 등록된 적이 없으면 {@link Optional#empty()}.
	 * @throws NullPointerException	{@code path}가 {@code null}인 경우.
	 */
	public Optional<utils.statechart.State<C>> findState(String path) {
		utils.Preconditions.checkNotNull(path, "path is null");

		// 차트 lock을 잡지 않는다. 실행 상태 lock만 쥔 코드(시작 전 취소의 동기 종료 리스너 등)에서 불러도
		// lock 순서(차트 lock → 실행 상태 lock)가 뒤집히지 않게 하기 위해서다. 맵이 동시 접근에 안전하므로
		// 다른 스레드에서 등록한 상태도 보인다.
		return Optional.ofNullable(m_states.get(path));
	}

	/**
	 * 상태를 차트에 등록한다.
	 * <p>
	 * 상태는 자신의 위치 경로({@link utils.statechart.State#getPath()})를 키로 등록된다. <b>같은 경로로 다시
	 * 등록할 수 없다.</b> 다시 등록하는 것을 허용하면, 이미 지정한 시작·최종 상태가 이전 객체를 계속 가리켜
	 * 최종 상태 판정이 어긋나기 때문이다. {@link #setInitialState(String)}와
	 * {@link #addFinalState(String)}는 등록된 상태만 지정할 수 있으므로 이 메소드를 먼저 호출해야 한다.
	 * <p>
	 * {@link TerminalState}(와 그 하위 클래스 {@link FailureState})는 빠져나올 수 없는 상태이므로, 등록하면
	 * 자동으로 최종 상태가 된다. {@link #addFinalState(String)}를 따로 호출하지 않아도 된다(호출해도 무방하다).
	 * <p>
	 * 상태의 경로는 {@link StatePath#checkValid(String)}의 조건을 만족해야 한다. 비어 있거나, {@code '/'}로 끝나거나,
	 * 빈 구간({@code "//"})이 있는 경로는 거부한다.
	 *
	 * @param state	등록할 상태.
	 * @throws NullPointerException		{@code state}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	상태의 경로가 올바르지 않거나, 같은 경로의 상태가 이미 등록된 경우,
	 * 									또는 상태의 컨텍스트({@link utils.statechart.State#getContext()})가 이 차트의
	 * 									컨텍스트와 다른 경우.
	 * @throws IllegalStateException	차트가 이미 시작된 경우.
	 */
	public void addState(utils.statechart.State<C> state) {
		utils.Preconditions.checkNotNull(state, "state is null");
		StatePath.checkValid(state.getPath());
		// 상태 콜백은 state.getContext()를, 전이 액션은 차트의 컨텍스트를 쓴다. 둘이 다르면 서로 다른 데이터를 보게 된다.
		utils.Preconditions.checkArgument(state.getContext() == m_context,
										"state belongs to another StateContext: %s", state.getPath());

		m_guard.lock();
		try {
			utils.Preconditions.checkState(isNotStarted(), "StateChart is already started");
			utils.Preconditions.checkArgument(!m_states.containsKey(state.getPath()),
											"state path is already registered: %s", state.getPath());
	
			m_states.put(state.getPath(), state);
			if ( state instanceof TerminalState ) {
				// 빠져나올 수 없는 상태이므로 최종 상태 외에는 쓸모가 없다. addFinalState()를 따로 부르지 않아도 된다.
				m_finalStates.add(state);
			}
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 차트의 시작 상태를 반환한다.
	 *
	 * @return	시작 상태. {@link #setInitialState(String)} 호출 전이면 {@code null}.
	 */
	public utils.statechart.State<C> getInitialState() {
		// findState()와 같은 이유로 차트 lock 없이 volatile 필드를 읽는다.
		return m_initialState;
	}

	/**
	 * 차트의 시작 상태를 지정한다.
	 * <p>
	 * 이미 {@link #addState(utils.statechart.State)}로 등록된 상태만 지정할 수 있다. 시작 상태는 최종 상태일 수 없으며,
	 * 이 검사는 여기가 아니라 {@link #start()}에서 이루어진다.
	 *
	 * @param initialStatePath	시작 상태의 위치 경로.
	 * @throws NullPointerException		{@code initialStatePath}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	해당 경로로 등록된 상태가 없는 경우.
	 * @throws IllegalStateException	차트가 이미 시작된 경우.
	 */
	public void setInitialState(@NotNull String initialStatePath) {
		utils.Preconditions.checkNotNull(initialStatePath, "initialState is null");

		m_guard.lock();
		try {
			utils.Preconditions.checkState(isNotStarted(), "StateChart is already started");
			
			utils.statechart.State<C> initialState = m_states.get(initialStatePath);
			if ( initialState == null ) {
				throw new IllegalArgumentException("initialState is not registered: " + initialStatePath);
			}
	
			m_initialState = initialState;
		}
		finally {
			m_guard.unlock();
		}
	}
	
	/**
	 * 차트의 최종 상태를 추가한다.
	 * <p>
	 * 최종 상태에 도달하면 차트는 스스로 종료한다. 해당 상태가 {@link FailureState}이면 그 상태가
	 * 보유한 원인으로 {@link #fail(Throwable)}되고, 그 외에는 {@link #complete()}된다.
	 * 최종 상태는 여러 개를 등록할 수 있으며, 최소 하나는 있어야 {@link #start()}가 성공한다.
	 * <p>
	 * 이미 {@link #addState(utils.statechart.State)}로 등록된 상태만 지정할 수 있다. {@link TerminalState}는 등록할 때
	 * 자동으로 최종 상태가 되므로, 이 메소드는 주로 일반 상태를 최종 상태로 쓸 때 호출한다.
	 *
	 * @param path	최종 상태의 위치 경로.
	 * @throws NullPointerException		{@code path}가 {@code null}인 경우.
	 * @throws IllegalArgumentException	해당 경로로 등록된 상태가 없는 경우.
	 * @throws IllegalStateException	차트가 이미 시작된 경우.
	 */
	public void addFinalState(String path) {
		utils.Preconditions.checkNotNull(path, "state path is null");

		m_guard.lock();
		try {
			utils.Preconditions.checkState(isNotStarted(), "StateChart is already started");
			
			utils.statechart.State<C> finalState = m_states.get(path);
			if ( finalState == null ) {
				throw new IllegalArgumentException("finalState is not registered: " + path);
			}
	
			m_finalStates.add(finalState);
		}
		finally {
			m_guard.unlock();
		}
	}
	
	/**
	 * 현재 진입 중인 상태 중 가장 안쪽 상태를 반환한다.
	 * <p>
	 * 반환값은 조회 시점의 snapshot이므로, 다른 스레드가 신호를 처리하면 곧바로 낡은 값이 된다.
	 * 상태를 보고 판단한 뒤 행동해야 한다면 {@link #getChartLock()}으로 직접 직렬화한다.
	 * <p>
	 * 복합 상태({@link CompositeState})에 진입해 있으면 그 안의 하위 상태(가장 안쪽 상태)가 반환된다.
	 * 진입해 있는 바깥쪽 복합 상태까지 모두 보려면 {@link #getActiveStates()}를 사용한다.
	 * 상태 콜백 안에서 호출하면 전이 도중의 값(진출을 마친 상태의 부모 등)이 보인다.
	 * <p>
	 * 차트 lock을 잡으므로 진행 중인 전이가 끝날 때까지 기다린다. 실행 상태 lock만 쥔 코드(시작 전 취소의
	 * 동기 종료 리스너)에서 부르면 lock 순서가 뒤집혀 시작과 경쟁할 때 교착될 수 있으므로, 그런 곳에서는
	 * {@link #toString()}처럼 lock을 잡지 않는 정보만 쓴다.
	 *
	 * @return	현재 상태. 시작 전이거나, 취소로 상태에서 진출한 뒤에는 {@code null}.
	 */
	public utils.statechart.State<C> getCurrentState() {
		return m_guard.get(() -> m_currentState);
	}

	/**
	 * 현재 진입해 있는 상태들을 바깥쪽(루트 쪽) 상태부터 가장 안쪽 상태 순서로 반환한다.
	 * <p>
	 * 평면 상태 차트에서는 원소가 하나뿐이다. 마지막 원소는 {@link #getCurrentState()}와 같다.
	 * 반환값은 조회 시점의 snapshot이며 수정할 수 없다.
	 *
	 * @return	진입해 있는 상태 목록. 시작 전이거나 취소로 모든 상태에서 진출한 뒤에는 빈 목록.
	 * 			최종 상태에 도달해 종료한 뒤에는 바깥 상태들을 진출했으므로 최종 상태 하나만 담는다.
	 */
	public List<utils.statechart.State<C>> getActiveStates() {
		return m_guard.get(() -> List.copyOf(m_activeStateChain));
	}

	/**
	 * 차트를 시작한다.
	 * <p>
	 * 시작 상태에 진입한 뒤 실행 상태를 {@code RUNNING}으로 전환한다. 이 시점부터
	 * {@link #handleSignal(Signal)}이 신호를 처리한다. 시작 상태가 하위 상태이면 바깥쪽 복합 상태부터
	 * 차례로 진입하고, 시작 상태가 복합 상태이면 그 최초 하위 상태까지 이어서 진입한다.
	 * <p>
	 * 시작하기 전에 상태 계층을 검사한다. 경로상 부모({@code "/A/B"}의 {@code "/A"})로 등록된 상태가 없거나
	 * 복합 상태가 아니면, 복합 상태의 최초 하위 상태가 등록되지 않았거나 직속 하위 상태가 아니면,
	 * 복합 상태가 최종 상태로 지정되었으면 시작을 거부한다.
	 * <p>
	 * 시작 상태(또는 그 안의 하위 상태)의 {@code enter()}가 예외를 던지면, 진입을 마친 바깥 상태들을 안쪽부터
	 * 진출시킨 뒤 차트는 실패 상태로 종료되고, 그 원인을 담은 {@link IllegalStateException}이 호출자에게 전파된다. {@link Error}도 같은 방식으로 처리하되,
	 * {@link VirtualMachineError}는 차트를 실패시킨 뒤 감싸지 않고 그대로 다시 던진다.
	 * <p>
	 * 시작 상태(또는 그 안의 하위 상태)의 {@code enter()} 안에서 {@code cancel(true)}나 {@link #complete()}를 호출하면
	 * 요청만 접수된다. 진입이 끝난 뒤 시작을 확정({@code RUNNING}, {@code whenStarted} 리스너 실행)하고 곧바로
	 * 취소(진입한 상태들을 자식부터 진출)·완료를 확정하며, 이 메소드는 정상 반환한다.
	 *
	 * @throws NullPointerException		시작 상태가 지정되지 않은 경우.
	 * @throws IllegalStateException	최종 상태가 하나도 없거나, 시작 상태(복합 상태이면 그 안에서 처음 진입할
	 * 									가장 안쪽 상태)가 최종 상태이거나, 상태 계층이 잘못되었거나,
	 * 									이미 시작된 차트이거나, 시작 상태 진입이 실패한 경우.
	 * 									시작 상태의 {@code enter()} 안에서 {@link #fail(Throwable)}을 호출한 경우도 같다.
	 * 									또한 상태 콜백 밖에서 차트 lock({@link #getChartLock()})을 보유한 채
	 * 									호출한 경우. 다른 스레드가 신호를 처리하는 중이면 교착될 수 있으므로
	 * 									항상 거부한다.
	 * @throws utils.RuntimeInterruptedException	다른 스레드의 신호 처리가 끝나기를 기다리는 중에 인터럽트된 경우.
	 */
	@Override
	public void start() {
		// 시작하는 동안(초기 상태의 enter() 안 포함) 보낸 신호는 큐에 쌓였다가 RUNNING이 된 뒤 처리된다.
		m_signalExecutor.runAsDispatcher(this::startInGuard);
	}
	
	/**
	 * 시작 전 구성을 검사한다. 차트 lock을 보유한 채 호출된다.
	 */
	private void checkConfiguration() {
		utils.Preconditions.checkNotNull(m_initialState, "initialState is not set");
		utils.Preconditions.checkState(!m_finalStates.isEmpty(),
							"finalState should not be empty");
		checkHierarchy();
		utils.Preconditions.checkState(!m_finalStates.contains(drillDown(m_initialState)),
							"initialState should not be a finalState");
	}
	
	private void startInGuard() {
		m_guard.lock();
		try {
			// 구성 메소드와 같은 lock 안에서 검사하므로, 다른 스레드에서 구성했더라도 그 결과가 보이고
			// 검사와 시작 사이에 구성이 바뀌지 않는다.
			checkConfiguration();
			
			if ( !notifyStarting() ) {
				throw new IllegalStateException("failed to start the StateChart");
			}
			
			Throwable startFailure = null;
			++m_callbackDepth;
			try {
	            // 초기 상태로 진입 (복합 상태이면 최초 하위 상태까지)
	            enterDownTo(m_initialState, 0);
	        }
	        catch ( Throwable e ) {
	            startFailure = Throwables.unwrapThrowable(e);
	            requestFailure(startFailure);
	        }
			finally {
				--m_callbackDepth;
			}

			Termination termination = m_pendingTermination;
			if ( termination != null ) {
				// 진입이 실패했거나, 초기 상태의 enter() 안에서 종료(cancel/complete/fail)가 요청되었다.
				// 진입을 마친 상태들을 정리하고 종료를 확정한다.
				VirtualMachineError fatal = applyPendingTermination();
				if ( startFailure != null ) {
					throwIfFatal(startFailure);
				}
				if ( fatal != null ) {
					throw fatal;
				}
				if ( termination.kind() == TerminationKind.FAIL ) {
					throw new IllegalStateException("failed to start the StateChart", termination.cause());
				}
				// 시작 중에 취소·완료되었다. 시작은 정상 반환하고, 차트는 이미 종료 상태다.
				return;
			}

			if ( !notifyStarted() ) {
				while ( !m_activeStateChain.isEmpty() ) {
					Unchecked.runOrIgnore(m_currentState::exit);
					removeInnermostState();
				}
				
	            notifyFailed(new IllegalStateException("failed to start the StateChart"));
				throw new IllegalStateException("failed to start the StateChart");
			}
		}
		finally {
			m_guard.unlock();
		}
	}
	
	/**
	 * 주어진 신호를 처리하고, 선택된 전이를 반환한다.
	 * <p>
	 * {@link #processSignal(Signal)}과 같이 처리하되 결과로 선택된 전이만 반환한다. 반환값이 비어 있으면
	 * 차트가 실행 중이 아니었는지, 처리할 전이가 없었는지, 큐에 넣었는지 구분할 수 없다. 구분이 필요하면
	 * {@link #processSignal(Signal)}을 사용한다.
	 *
	 * @param signal	처리할 신호.
	 * @return	신호 처리에 사용된 전이. 전이가 일어나지 않았거나 큐에 넣었으면 {@link Optional#empty()}.
	 * @throws NullPointerException	{@code signal}이 {@code null}인 경우.
	 * @throws RuntimeException	{@link utils.statechart.State#selectTransition(Signal)}이 예외를 던진 경우,
	 * 							또는 선택된 전이의 목표 경로로 등록된 상태가 없는 경우({@link IllegalStateException}).
	 * 							이때 차트도 함께 실패 상태로 종료된다. 반면 전이 <b>수행</b> 중에
	 * 							발생한 예외는 차트 내부에서 흡수되어 전파되지 않는다.
	 * @throws Error	{@link utils.statechart.State#selectTransition(Signal)}이 {@link Error}를 던졌거나,
	 * 					전이 수행 중 {@link VirtualMachineError}가 발생한 경우. 어느 경우든 차트는
	 * 					실패 상태로 종료된 뒤 전파된다.
	 * @see #processSignal(Signal)
	 */
	public Optional<Transition<C>> handleSignal(Signal signal) {
		return processSignal(signal).getTransition();
	}

	/**
	 * 주어진 신호를 처리하고 그 결과를 반환한다.
	 * <p>
	 * 진입해 있는 상태들에게 가장 안쪽부터 이 신호를 처리할 전이가 있는지 묻고
	 * ({@link utils.statechart.State#selectTransition(Signal)}), 처음 선택된 전이를 따라 이동한다.
	 * 이동은 진출 → 전이 액션 → 진입 순으로 이루어지며({@link #executeTransition(Transition, Signal)} 참고),
	 * 이동 후 가장 안쪽 상태가 최종 상태이면 차트가 종료된다. 어떤 상태도 전이를 선택하지 않으면
	 * {@link #onUnhandledSignal(Signal)}이 호출된다.
	 * <p>
	 * <b>처리 순서</b>: 신호는 차트의 큐에 들어간 순서대로, 한 번에 하나씩 처리된다(run-to-completion).
	 * <ul>
	 *   <li>다른 스레드가 신호를 처리하는 중이면, 큐에 넣고 그 신호가 처리될 때까지 기다린다. 처리는 그 스레드가
	 *       한다. 반대로 이 스레드가 처리하게 되면, 큐가 빌 때까지 다른 스레드가 넣은 신호도 이어서 처리한다.</li>
	 *   <li>상태 콜백({@code enter()}/{@code exit()}/{@code selectTransition()})이나 전이 액션 안에서 같은
	 *       스레드로 호출하면, 진행 중인 처리를 끝낸 뒤 처리하도록 큐에 넣고 기다리지 않고
	 *       {@link SignalResult.Status#QUEUED}를 반환한다. 전이 도중에 다른 전이가 끼어들지 않는다.
	 *       차트 lock을 보유한 다른 코드(동기 종료 리스너, {@link #getChartLock()}을 잡은 코드)에서 호출했는데
	 *       다른 스레드가 신호를 처리하는 중이어도, 기다리면 교착되므로 큐에 넣고 {@code QUEUED}를 반환한다.</li>
	 *   <li>{@link #start()} 도중(초기 상태의 {@code enter()} 안 포함)에 보낸 신호는 차트가 {@code RUNNING}이
	 *       된 뒤 처리된다.</li>
	 * </ul>
	 * <p>
	 * 처리할 때 차트가 실행 중이 아니면 신호는 버려지고 {@link SignalResult.Status#IGNORED}가 반환된다.
	 * 시작 전에 보낸 신호는 매번 경고 로그를 남긴다. 종료 후(취소 중 포함)에 도착한 신호는 첫 신호만 INFO로,
	 * 이후는 DEBUG로 기록한다.
	 *
	 * @param signal	처리할 신호.
	 * @return	신호 처리 결과.
	 * @throws NullPointerException	{@code signal}이 {@code null}인 경우.
	 * @throws RuntimeException	{@link utils.statechart.State#selectTransition(Signal)}이나
	 * 							{@link #onUnhandledSignal(Signal)}이 예외를 던진 경우, 또는 선택된 전이의 목표 경로로
	 * 							등록된 상태가 없는 경우({@link IllegalStateException}). 이때 차트도 함께 실패
	 * 							상태로 종료된다. 다른 스레드가 처리했더라도 예외는 이 호출자에게 전달된다.
	 * 							전이 <b>수행</b> 중에 발생한 예외는 차트 내부에서 흡수되어 전파되지 않는다.
	 * @throws Error	위와 같은 곳에서 {@link Error}가 발생했거나, 전이 수행 중 {@link VirtualMachineError}가
	 * 					발생한 경우. 어느 경우든 차트는 실패 상태로 종료된 뒤 전파된다.
	 * @throws utils.RuntimeExecutionException	위와 같은 곳에서 checked 예외가 (sneaky throw 등으로) 전달된 경우.
	 * 											원인으로 그 예외를 갖는다. 차트는 실패 상태로 종료된다.
	 */
	public SignalResult<C> processSignal(Signal signal) {
		utils.Preconditions.checkNotNull(signal, "signal is null");

		Invocation<SignalResult<C>> invocation = m_signalExecutor.invokeOrDefer(new SignalTask(signal));
		if ( invocation.isDeferred() ) {
			// 신호를 처리하는 스레드가 다시 보냈거나, 차트 lock을 쥔 스레드가 보낸 신호.
			// 기다리면 교착되므로 큐에 넣기만 한다.
			getLogger().debug("{}: queued signal sent while handling another: {}", getName(), signal);
			return SignalResult.of(SignalResult.Status.QUEUED);
		}
		// 처리 중 발생한 예외는 다른 스레드가 처리했더라도 원래 예외 그대로 다시 던져진다.
		return invocation.get();
	}

	/**
	 * 주어진 신호를 큐에 넣고, 처리 결과를 받을 수 있는 {@link CompletableFuture}를 반환한다.
	 * <p>
	 * 처리 순서와 규칙은 {@link #processSignal(Signal)}과 같다. 다른 스레드가 신호를 처리하는 중이거나
	 * 상태 콜백 안에서 호출한 경우는 큐에 넣기만 하고 바로 반환한다. 그 외에는 이 스레드가 큐가 빌 때까지
	 * 신호를 처리한 뒤 반환한다. 따라서 보낸 순서는 지키면서, 다른 신호를 처리하는 동안 기다리지 않아도 되는
	 * 호출자(예: 네트워크 수신 스레드, 비동기 작업의 완료 콜백)에 알맞다.
	 * <p>
	 * {@link #processSignal(Signal)}에서 호출자에게 던져질 예외는 반환된 future를 예외로 완료시킨다.
	 * <b>상태 콜백 안에서 반환된 future의 완료를 기다리면 안 된다.</b> 그 신호는 현재 처리가 끝나야 처리되므로
	 * 교착된다.
	 * <p>
	 * 큐에 넣은 신호는 반환된 future와 관계없이 반드시 처리된다. future를 취소하거나 {@code orTimeout()} 등으로
	 * 직접 완료시켜도 결과를 받지 않게 될 뿐, 신호 처리를 철회하지 않는다. 반환된 future는 내부 future의 사본이므로,
	 * {@code whenComplete()}/{@code handle()}이 받는 예외는 원래 예외를 원인으로 갖는 {@link java.util.concurrent.CompletionException}이다.
	 *
	 * @param signal	처리할 신호.
	 * @return	신호 처리 결과를 받을 future. {@link SignalResult.Status#QUEUED}로 완료되지는 않는다.
	 * @throws NullPointerException	{@code signal}이 {@code null}인 경우.
	 */
	public CompletableFuture<SignalResult<C>> postSignal(Signal signal) {
		utils.Preconditions.checkNotNull(signal, "signal is null");

		// 실행기는 실행 직전에 future가 이미 완료(취소)되어 있으면 작업을 건너뛴다. 호출자가 future를 취소하거나
		// 시간 제한을 걸어도 신호가 버려지지 않도록, 내부 future 대신 사본을 반환한다.
		return m_signalExecutor.submit(new SignalTask(signal)).copy();
	}

	/**
	 * 실행 중인 차트에서, 진입해 있는 어떤 상태도 전이를 선택하지 않은 신호를 받았을 때 호출된다.
	 * <p>
	 * 기본 구현은 아무 일도 하지 않는다. 처리되지 않은 신호에 대한 공통 처리(예: 오류 신호를 받으면
	 * 차트를 실패시킴)가 필요한 하위 클래스가 재정의한다. 차트 lock을 보유한 채, 신호를 처리하는 스레드에서
	 * 호출된다. 여기서 {@link #fail(Throwable)}이나 {@code cancel(true)}를 호출해도 된다. 예외를 던지면
	 * {@link utils.statechart.State#selectTransition(Signal)}이 예외를 던진 것과 같이 처리된다.
	 *
	 * @param signal	처리되지 않은 신호.
	 */
	protected void onUnhandledSignal(Signal signal) { }

	/**
	 * 신호 하나를 처리하는 실행기 작업. 실행기 로그에 신호가 드러나도록 람다 대신 이름 있는 클래스로 둔다.
	 */
	private final class SignalTask implements Callable<SignalResult<C>> {
		private final Signal m_signal;

		SignalTask(Signal signal) {
			m_signal = signal;
		}

		@Override
		public SignalResult<C> call() {
			return dispatchSignal(m_signal);
		}

		@Override
		public String toString() {
			return "signal[" + m_signal + "]";
		}
	}

	private SignalResult<C> dispatchSignal(Signal signal) {
		m_guard.lock();
		try {
			if ( !isRunning() ) {
				logIgnoredSignal(signal);
				return SignalResult.of(SignalResult.Status.IGNORED);
			}
			getLogger().debug("{}: received signal at {}: {}", getName(), m_currentState, signal);

			SignalResult<C> result = null;
			Throwable failure = null;
			++m_callbackDepth;
			try {
				result = selectAndExecuteTransition(signal);
			}
			catch ( Throwable e ) {
				// selectTransition()/onUnhandledSignal()이 예외를 던졌거나, 목표 상태가 없거나, 치명 오류가 전파되었다.
				failure = e;
				if ( m_pendingTermination == null ) {
					Throwable cause = Throwables.unwrapThrowable(e);
					getLogger().error("{}: failed to handle signal at {}: {}, cause={}",
										getName(), m_currentState, signal, cause);
					requestFailure(cause);
				}
			}
			finally {
				--m_callbackDepth;
			}

			// 콜백이 모두 반환된 뒤 정리하고 종료를 확정한다. 종료 리스너는 정리가 끝난 뒤 실행된다.
			VirtualMachineError fatal = applyPendingTermination();
			if ( failure instanceof VirtualMachineError vme ) {
				throw vme;
			}
			if ( fatal != null ) {
				throw fatal;
			}
			if ( failure != null ) {
				// RuntimeException·Error는 그대로, 몰래 던져진(sneaky) checked 예외도 삼키지 않고 그대로 다시 던진다.
				// 신호 큐가 processSignal()에서는 RuntimeExecutionException으로 감싸고, postSignal()의 future는
				// 이 예외로 실패시킨다.
				throw Throwables.<RuntimeException, RuntimeException>sneakyThrow(failure);
			}
			return result;
		}
		finally {
			m_guard.unlock();
		}
	}

	private SignalResult<C> selectAndExecuteTransition(Signal signal) {
		// Signal에 따른 transition을 선택하고, 해당 전이로 이동
		Optional<Transition<C>> selected = selectTransition(signal);
		if ( isTerminating() ) {
			// selectTransition() 안에서 종료가 요청되었다. 선택한 전이는 수행하지 않는다.
			return SignalResult.of(SignalResult.Status.IGNORED);
		}
		if ( selected.isPresent() ) {
			boolean succeeded = executeTransition(selected.get(), signal);
			return succeeded ? SignalResult.transitioned(selected.get()) : SignalResult.failed(selected.get());
		}
		else {
			onUnhandledSignal(signal);
			return SignalResult.of(SignalResult.Status.UNHANDLED);
		}
	}

	private void logIgnoredSignal(Signal signal) {
		if ( isNotStarted() ) {
			// 시작 전 신호는 호출 순서의 실수일 가능성이 높으므로 매번 경고한다.
			getLogger().warn("{}: ignoring signal before start: {}", this, signal);
		}
		else if ( !m_lateSignalLogged ) {
			// 종료 직후 늦게 도착하는 신호는 흔하다. 첫 신호만 INFO로 남기고 이후는 DEBUG로 남긴다.
			m_lateSignalLogged = true;
			getLogger().info("{}: ignoring signal in non-running state: {} (further ones are logged at DEBUG)",
							this, signal);
		}
		else {
			getLogger().debug("{}: ignoring signal in non-running state: {}", this, signal);
		}
	}
	
	/**
	 * 차트를 정상 완료 상태로 종료시킨다.
	 * <p>
	 * 최종 상태에 도달하면 차트가 스스로 호출하지만, 상태 전이와 무관하게 밖에서 끝내고 싶을 때
	 * 직접 호출할 수도 있다. <b>직접 호출하는 경우 상태 전이를 거치지 않으므로 현재 상태의
	 * {@code exit()}도, 어떤 최종 상태의 {@code enter()}도 실행되지 않는다.</b> 상태 진입에 의존하는
	 * 정리 작업이 있다면 종료 리스너({@code whenFinished})로 옮겨야 한다.
	 * <p>
	 * 실행 결과값으로는 상태 컨텍스트가 사용된다.
	 * <p>
	 * 상태 콜백 안(신호 처리나 시작 중 상태의 {@code enter()}/{@code exit()}, 전이 액션 등)에서 호출하면 요청만 접수하고
	 * 바로 반환한다. 종료는 콜백이 반환된 뒤 확정되며, 종료 리스너도 그때 실행된다. 이미 다른 종료가 접수되었으면
	 * {@link IllegalStateException}을 던진다.
	 *
	 * @throws IllegalStateException	아직 시작되지 않았거나 이미 종료된 차트인 경우, 또는 상태 콜백 안에서
	 * 									다른 종료가 이미 접수된 경우.
	 */
	public void complete() {
        m_guard.lock();
        try {
        	if ( m_callbackDepth > 0 ) {
        		// 상태 콜백 안에서 호출되었다. 콜백이 반환된 뒤 확정한다.
        		if ( !requestTermination(new Termination(TerminationKind.COMPLETE, null, false)) ) {
        			throw new IllegalStateException("cannot complete the StateChart: termination is already requested");
        		}
        		return;
        	}
            if ( !notifyCompleted(m_context) ) {
            	throw new IllegalStateException("cannot complete the StateChart");
            }
        }
        finally {
            m_guard.unlock();
        }
	}
	
	/**
	 * 차트를 실패 상태로 종료시킨다.
	 * <p>
	 * {@link FailureState}에 도달하면 차트가 그 상태의 원인으로 스스로 호출하며, 전이 수행 중
	 * 예외가 발생한 경우에도 내부적으로 같은 경로를 탄다. {@link #complete()}와 마찬가지로 직접
	 * 호출할 수 있고, 그 경우 상태 전이를 거치지 않는다는 점도 동일하다.
	 * <p>
	 * 상태 콜백 안(신호 처리나 시작 중 상태의 {@code enter()}/{@code exit()}, 전이 액션 등)에서 호출하면 요청만 접수하고
	 * 바로 반환한다. 종료는 콜백이 반환된 뒤 확정되며, 종료 리스너도 그때 실행된다. 이미 다른 종료가 접수되었으면
	 * {@link IllegalStateException}을 던진다.
	 *
	 * @param cause	실패 원인. {@code null}이면 안 된다.
	 * @throws IllegalArgumentException	{@code cause}가 {@code null}인 경우. 차트의 상태는 바뀌지 않는다.
	 * @throws IllegalStateException	아직 시작되지 않았거나 이미 종료된 차트인 경우, 또는 상태 콜백 안에서
	 * 									다른 종료가 이미 접수된 경우.
	 */
	public void fail(Throwable cause) {
		utils.Preconditions.checkNotNullArgument(cause, "failure cause is null");

		m_guard.lock();
		try {
			if ( m_callbackDepth > 0 ) {
				// 상태 콜백 안에서 호출되었다. 콜백이 반환된 뒤 확정한다.
				if ( !requestTermination(new Termination(TerminationKind.FAIL, cause, false)) ) {
					throw new IllegalStateException("cannot fail the StateChart: termination is already requested");
				}
				return;
			}
			if ( !notifyFailed(cause) ) {
				throw new IllegalStateException("cannot fail the StateChart");
			}
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 차트를 취소한다.
	 * <p>
	 * 상태 콜백 안(신호 처리나 시작 중 상태의 {@code enter()}/{@code exit()}, 전이 액션 등)에서 호출하면 요청만 접수하고
	 * {@code true}를 반환한다. 취소는 콜백이 반환된 뒤, 진입해 있는 상태들을 안쪽부터 진출시키고 확정한다.
	 * 따라서 종료 리스너는 정리가 끝난 뒤 실행된다. 이미 다른 종료가 접수되었으면 {@code false}를 반환한다.
	 * 시작 상태의 {@code enter()}에서 취소하면, 시작을 확정({@code RUNNING})한 직후 취소를 확정한다.
	 * <p>
	 * 그 밖의 경우는 부모의 취소 절차를 따른다({@link #cancelWork()} 참고).
	 */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		// 콜백 구간은 차트 lock을 쥔 스레드에만 있으므로, lock 보유 여부를 먼저 본다(다른 스레드는 lock을 기다리지 않는다).
		if ( m_guard.isHeldByCurrentThread() && m_callbackDepth > 0 ) {
			return requestTermination(new Termination(TerminationKind.CANCEL, null, true));
		}
		return super.cancel(mayInterruptIfRunning);
	}

	/**
	 * 취소 요청을 받아 현재 상태에서 진출한 뒤 취소를 확정한다.
	 * <p>
	 * {@code cancel(true)}가 호출되면 부모가 실행 상태를 {@code CANCELLING}으로 바꾼 뒤 lock 밖에서 호출하는
	 * 콜백이다. 진입해 있는 상태들의 {@code exit()}를 가장 안쪽 상태부터 수행하고 현재 상태를 비운 다음,
	 * 실행 상태를 {@code CANCELLED}로 확정한다. 따라서 종료 리스너({@code whenFinished})가 실행되고 {@code waitForFinished()}도 반환된다.
	 * {@code setTimeout()}의 시간 초과도 이 경로로 차트를 취소한다.
	 * <p>
	 * <b>어떤 최종 상태로도 진입하지 않으므로 최종 상태의 {@code enter()}는 실행되지 않는다.</b>
	 * 진출 과정에서 발생한 예외는 경고 로그만 남기고 바깥쪽 상태의 진출과 취소 확정을 계속한다.
	 * {@link Error}도 마찬가지이며,
	 * {@link VirtualMachineError}만 취소를 확정한 뒤 다시 던진다.
	 * <p>
	 * 진행 중이던 전이가 먼저 최종 상태에 도달하여 차트가 이미 종료된 경우는 아무 일도 하지 않는다.
	 *
	 * @return	취소를 확정했으면 {@code true}, 차트가 이미 완료·실패로 종료되어 취소하지 못했으면 {@code false}.
	 */
	@Override
	public boolean cancelWork() {
		m_guard.lock();
		try {
			if ( isDone() ) {
				// 취소 요청과 경쟁하던 전이가 먼저 최종 상태에 도달한 경우.
				return isCancelled();
			}

			// 가장 안쪽 상태부터 바깥쪽으로 모두 진출한 뒤 취소를 확정한다. 진출에 실패해도 바깥쪽 상태의 진출과
			// 취소 확정은 계속한다(확정하지 않으면 차트가 CANCELLING에 남는다). 콜백 안의 취소 요청과 같은 경로로
			// 처리하므로, 진출 중 exit()에서 들어온 종료 요청은 취소를 바꾸지 못한다.
			m_pendingTermination = new Termination(TerminationKind.CANCEL, null, true);
			VirtualMachineError fatal = applyPendingTermination();
			if ( fatal != null ) {
				throw fatal;
			}
			return isCancelled();
		}
		finally {
			m_guard.unlock();
		}
	}

	/**
	 * 로그와 {@link #toString()}에서 이 차트를 구분하는 이름을 반환한다.
	 *
	 * @return	{@link #setName(String)}로 지정한 이름. 지정하지 않았으면 {@code 클래스이름@식별해시}
	 * 			(예: {@code StateChart@1b2c3d4}).
	 */
	public String getName() {
		String name = m_name;
		return (name != null) ? name : m_defaultName;
	}

	/**
	 * 로그와 {@link #toString()}에서 이 차트를 구분하는 이름을 지정한다.
	 * <p>
	 * 여러 차트가 같은 로거를 쓸 때 어느 차트의 로그인지 알아보기 쉽게 한다. 언제든 바꿀 수 있다.
	 *
	 * @param name	차트 이름. {@code null}이면 기본 이름으로 되돌린다.
	 */
	public void setName(String name) {
		m_name = name;
	}

	@Override
	public Logger getLogger() {
		return (m_logger != null) ? m_logger : s_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
		m_signalExecutor.setLogger(logger);
	}
	
	/**
	 * 차트의 실행 상태와 현재 상태를 문자열로 반환한다.
	 * <p>
	 * <b>차트 lock({@link #getChartLock()})을 잡지 않는다.</b> 부모는 실행 상태 lock 안에서 로그를 남기며
	 * {@code this}를 출력하는데, 상태 전이는 차트 lock을 잡은 채 실행 상태 lock을 요청한다. 여기서 차트 lock을
	 * 잡으면 두 lock의 획득 순서가 뒤집혀 교착될 수 있다. 따라서 현재 상태는 lock 없이 읽은 값이며,
	 * 전이 도중에는 이전 값이나 {@code null}일 수 있다.
	 *
	 * @return	차트를 나타내는 문자열.
	 */
	@Override
	public String toString() {
		return String.format("%s[%s, state=%s]", getName(), getState(), m_currentState);
	}
	
	/**
	 * 선택된 전이를 따라 상태를 이동시킨다.
	 * <p>
	 * 이동은 진출 → 전이 액션({@link Transition#execute(StateContext, Signal)}) → 진입 순으로 수행되며,
	 * 이동을 마친 뒤 가장 안쪽 상태가 최종 상태이면 이어서 차트를 종료시킨다
	 * ({@link FailureState}이면 그 상태의 원인으로 {@link #fail(Throwable)}, 그 외에는
	 * {@link #complete()}). 최종 상태가 복합 상태 안의 하위 상태이면, 종료하기 전에 최종 상태를 둘러싼 바깥
	 * 상태들을 안쪽부터 진출한다. 따라서 종료 리스너가 실행될 때는 바깥 상태의 {@code exit()}가 이미 끝나 있다.
	 * 이 진출이 실패해도 경고 로그만 남기고 종료를 계속한다.
	 * <ul>
	 *   <li><b>진출</b>: 현재 진입해 있는 상태들({@link #getActiveStates()}) 중 목표 상태와 공유하는 조상은
	 *       그대로 두고, 그 아래의 상태들을 가장 안쪽부터 진출한다. 목표가 이미 진입해 있는 상태이면
	 *       목표 상태도 진출한다(진출했다가 다시 진입한다).</li>
	 *   <li><b>진입</b>: 목표 상태까지의 경로 중 진입해 있지 않은 상태들을 바깥쪽부터 진입한다. 목표가
	 *       복합 상태이면 최초 하위 상태까지 이어서 진입한다.</li>
	 * </ul>
	 * 예를 들어 {@code /A/X}에 있을 때 {@code /A/Y}로 가면 {@code /A/X}만 진출하고 {@code /A/Y}에 진입하며,
	 * {@code /A}는 그대로 둔다. 바깥쪽 {@code /A}가 선택한 전이라도 마찬가지다.
	 * <p>
	 * 목표 경로가 없는 전이({@link Transition#isInternal()}, 내부 전이)이면 상태를 바꾸지 않고
	 * <b>전이 액션만 실행한다.</b> 상태의 {@code exit()}/{@code enter()}는 실행되지 않으며 최종 상태 판정도 하지
	 * 않는다. {@link Transitions#internal(TransitionAction)}이 이런 전이를 만들고, {@link Transitions#stay()}는
	 * 액션이 비어 있는 내부 전이다.
	 * <p>
	 * <b>이동 도중 발생한 예외는 밖으로 전파되지 않는다.</b> 차트를 실패 상태로 종료시키고 흡수한다.
	 * {@link Error}({@link AssertionError} 등)도 같게 처리한다. 단 {@link VirtualMachineError}
	 * ({@link OutOfMemoryError}, {@link StackOverflowError} 등)는 차트를 실패시킨 뒤 다시 던진다.
	 * 실패로 확정하기 전에 그때 진입해 있던 상태들을 가장 안쪽부터 진출시킨다(진출 중 예외는 경고 로그만 남긴다).
	 * 이전 상태로 되돌리지는 않는다. {@code enter()}가 실패한 상태 자신은 진출시키지 않으므로, 그 상태가
	 * {@code enter()}에서 얻은 자원은 그 {@code enter()}가 정리해야 한다.
	 * <p>
	 * 콜백({@code exit()}, 전이 액션, {@code enter()}) 안에서 종료가 요청되면({@code cancel(true)}, {@link #complete()},
	 * {@link #fail(Throwable)}) 그 뒤의 단계는 실행하지 않는다. 요청은 접수만 되고, 이 메소드가 반환된 뒤 정리
	 * (취소이면 진입해 있는 상태들을 자식부터 진출)와 함께 확정된다. {@code enter()}가 반환된 상태는 진입한 것으로
	 * 보므로, 자기 {@code enter()}에서 취소한 상태도 자식부터 진출된다. 이 메소드는 신호 처리 중에만 호출된다.
	 * 단, 목표 경로에 해당하는 상태가 등록되어 있지 않은 경우는 이 처리 이전에 검사되므로
	 * {@link IllegalStateException}이 그대로 전파된다.
	 * <p>
	 * 본 메소드는 {@link #handleSignal(Signal)}이 lock을 보유한 채 호출한다. 하위 클래스가 전이 과정에
	 * 개입해야 하는 경우에만 재정의한다.
	 *
	 * (2026-10-31 이전 이름은 {@code traverse()}였다.)
	 *
	 * @param transition	수행할 전이.
	 * @param signal		전이를 유발한 신호.
	 * @return	전이를 예외 없이 마쳤으면 {@code true}(콜백 안에서 차트가 종료되어 중간에 멈춘 경우 포함),
	 * 			수행 중 예외로 차트가 실패했으면 {@code false}.
	 * @throws IllegalStateException	전이의 목표 경로로 등록된 상태가 없는 경우.
	 */
	protected boolean executeTransition(Transition<C> transition, Signal signal) {
		if ( transition.isInternal() ) {
			// 상태를 바꾸지 않고 전이 액션만 실행한다. stay()는 액션이 비어 있다.
			// 액션 안에서 취소되면 현재 상태가 비므로 경로를 미리 읽어 둔다.
			String statePath = m_currentState.getPath();
			getLogger().debug("{}: internal transition at {}: {}", getName(), statePath, transition);
			try {
				transition.execute(m_context, signal);
			}
			catch ( Throwable e ) {
				Throwable cause = Throwables.unwrapThrowable(e);
				getLogger().error("{}: failed to execute internal transition at {}", getName(), statePath, cause);

				boolean recorded = requestFailure(cause);
				throwIfFatal(cause);
				return !recorded;
			}
			return true;
		}
		
		String targetStatePath = transition.getTargetStatePath().get();
		utils.statechart.State<C> targetState = m_states.get(targetStatePath);
		if ( targetState == null ) {
			throw new IllegalStateException("no state found for path: " + targetStatePath);
		}
	
		utils.statechart.State<C> srcState = m_currentState;
		getLogger().info("{}: transition {} -> {}", getName(), srcState.getPath(), targetStatePath);

		// 현재 진입해 있는 상태들과 목표 상태의 조상 체인이 공유하는 부분은 그대로 둔다.
		// 목표 상태 자체가 진입해 있는 상태이면 목표 상태는 진출했다가 다시 진입한다.
		List<utils.statechart.State<C>> targetChain = getAncestorChain(targetState);
		int sharedAncestorCount = 0;
		while ( sharedAncestorCount < m_activeStateChain.size() && sharedAncestorCount < targetChain.size()
				&& m_activeStateChain.get(sharedAncestorCount) == targetChain.get(sharedAncestorCount) ) {
			++sharedAncestorCount;
		}
		if ( sharedAncestorCount == targetChain.size() ) {
			--sharedAncestorCount;
		}

		try {
			// 콜백(exit()/전이 액션/enter()) 안에서 종료(cancel/complete/fail)가 요청되면, 그 뒤의 단계는 건너뛴다.
			// 종료될 차트에서 상태에 진입하거나 액션을 실행하지 않기 위해서다. 정리와 종료 확정은 호출자가 한다.

			// 공유하지 않는 상태들에서 가장 안쪽부터 진출한다.
			while ( m_activeStateChain.size() > sharedAncestorCount ) {
				exitInnermostState();
				if ( isTerminating() ) {
					return true;
				}
			}
			
			// 인자로 주어진 transition으로 전이 연산 수행
			transition.execute(m_context, signal);
			if ( isTerminating() ) {
				return true;
			}
			
			// 목표 상태(복합 상태이면 그 최초 하위 상태까지)로 진입한다.
			enterDownTo(targetState, sharedAncestorCount);
			if ( isTerminating() ) {
				return true;
			}
			
			// 최종 상태에 도달했으면 바깥 상태들을 진출한 뒤 완료 통지
			if ( m_finalStates.contains(m_currentState) ) {
				exitEnclosingStates();
				if ( isTerminating() ) {
					return true;
				}
				if ( m_currentState instanceof FailureState ) {
					Throwable cause = ((FailureState<C>) m_currentState).getFailureCause();
					if ( cause == null ) {
						// 들어오는 전이가 원인을 설정하지 않은 경우. 어느 상태에서 원인이 빠졌는지 알 수 있도록 한다.
						cause = new IllegalStateException("failure cause is not set: state="
															+ m_currentState.getPath());
					}
					requestTermination(new Termination(TerminationKind.FAIL, cause, false));
				}
				else {
					requestTermination(new Termination(TerminationKind.COMPLETE, null, false));
				}
			}
		}
		catch ( Throwable e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			getLogger().error("{}: failed to execute transition: {} -> {}",
								getName(), srcState.getPath(), targetStatePath, cause);

			boolean recorded = requestFailure(cause);
			throwIfFatal(cause);
			return !recorded;
		}
		return true;
	}

	/**
	 * 현재 진입해 있는 상태들에게 가장 안쪽부터 차례로 전이를 묻는다.
	 *
	 * @param signal	처리할 신호.
	 * @return	처음 선택된 전이. 아무 상태도 선택하지 않으면 {@link Optional#empty()}.
	 */
	private Optional<Transition<C>> selectTransition(Signal signal) {
		for ( int i = m_activeStateChain.size() - 1; i >= 0; --i ) {
			Optional<Transition<C>> selected = m_activeStateChain.get(i).selectTransition(signal);
			if ( selected.isPresent() ) {
				return selected;
			}
		}
		return Optional.empty();
	}

	/**
	 * 종료 요청을 접수한다. 이미 접수된 요청이 있거나 차트가 종료되었으면 접수하지 않는다.
	 *
	 * @return	접수했으면 {@code true}.
	 */
	private boolean requestTermination(Termination termination) {
		if ( m_pendingTermination != null || isDone() ) {
			return false;
		}
		m_pendingTermination = termination;
		return true;
	}

	/**
	 * 콜백에서 발생한 예외로 차트를 실패시키도록 접수한다.
	 * <p>
	 * 확정할 때 진입해 있는 상태들을 진출시킨다. 시작이나 전이가 실패해도 진입을 마친 상태들이 {@code enter()}에서
	 * 얻은 자원을 {@code exit()}로 반환하게 하기 위해서다. {@code enter()}가 실패한 상태 자신은 진입하지 않은
	 * 것으로 보아 진출시키지 않으므로, 그 상태의 부분 초기화는 해당 {@code enter()}가 정리해야 한다.
	 * 원인이 {@link VirtualMachineError}이면 진출 없이 확정한다.
	 *
	 * @return	접수했으면 {@code true}. 다른 종료가 먼저 접수되었으면 {@code false}(경고 로그만 남긴다).
	 */
	private boolean requestFailure(Throwable cause) {
		boolean fatal = cause instanceof VirtualMachineError;
		if ( requestTermination(new Termination(TerminationKind.FAIL, cause, !fatal)) ) {
			return true;
		}
		getLogger().warn("{}: ignoring failure after termination was requested: cause={}", getName(), "" + cause);
		return false;
	}

	/**
	 * 종료가 접수되었거나 차트가 이미 종료되었는지 여부. 콜백이 반환될 때마다 확인해 남은 단계를 건너뛴다.
	 */
	private boolean isTerminating() {
		return m_pendingTermination != null || isDone();
	}

	/**
	 * 접수된 종료 요청을 확정한다. 콜백 구간이 끝난 뒤, 차트 lock을 쥔 채 호출한다.
	 * <p>
	 * 요청이 진출을 요구하면 진입해 있는 상태들을 가장 안쪽부터 진출시킨 다음, 실행 상태를 종료로 바꾼다. 따라서 종료
	 * 리스너는 정리가 끝난 뒤 실행된다. 진출도 콜백 구간으로 다루므로, 진출 중 {@code exit()}에서 들어온 종료
	 * 요청은 이미 접수된 요청 때문에 거부되어 결과를 바꾸지 못한다. 시작 중({@code STARTING})이면 부모가 취소·완료로
	 * 바로 갈 수 없으므로 시작을 먼저 확정한다(실패는 {@code STARTING}에서 바로 확정한다).
	 *
	 * @return	진출 중 처음 발생한 {@link VirtualMachineError}. 호출자는 종료를 확정한 뒤 이를 다시 던진다.
	 */
	private @Nullable VirtualMachineError applyPendingTermination() {
		Termination termination = m_pendingTermination;
		if ( termination == null || isDone() ) {
			return null;
		}

		VirtualMachineError fatal = null;
		// 진출과 시작 확정(동기 whenStarted 리스너 실행)도 콜백 구간으로 다룬다. 그 안에서 들어온 종료 요청은 이미
		// 접수된 요청 때문에 거부되어, 결정된 종료를 바꾸지 못한다.
		++m_callbackDepth;
		try {
			if ( termination.exitStates() ) {
				fatal = exitActiveStatesQuietly(termination.kind() == TerminationKind.CANCEL ? "cancelling" : "failing");
			}
			if ( termination.kind() != TerminationKind.FAIL && getState() == AsyncState.STARTING ) {
				notifyStarted();
			}
		}
		finally {
			--m_callbackDepth;
		}
		switch ( termination.kind() ) {
			case CANCEL -> notifyCancelled();
			case COMPLETE -> notifyCompleted(m_context);
			case FAIL -> notifyFailed(termination.cause());
		}
		return fatal;
	}

	/**
	 * 진입해 있는 상태들을 가장 안쪽부터 모두 진출시킨다. 진출 중 예외는 경고 로그만 남기고 계속한다.
	 * {@link TerminalState}는 진출할 수 없으므로 목록에서만 뺀다.
	 *
	 * @param when	로그에 남길 상황 설명.
	 * @return	진출 중 처음 발생한 {@link VirtualMachineError}. 없으면 {@code null}.
	 */
	private VirtualMachineError exitActiveStatesQuietly(String when) {
		VirtualMachineError fatal = null;
		while ( !m_activeStateChain.isEmpty() ) {
			utils.statechart.State<C> state = m_currentState;
			if ( state instanceof TerminalState ) {
				removeInnermostState();
				continue;
			}
			try {
				// 상태는 exit() 전에 목록에서 빠지므로, 실패해도 다시 진출시키지 않는다.
				exitInnermostState();
			}
			catch ( Throwable e ) {
				getLogger().warn("{}: failed to exit state while {}: {}, cause={}",
									getName(), when, state.getPath(), "" + e);
				if ( fatal == null && e instanceof VirtualMachineError vme ) {
					fatal = vme;
				}
			}
		}
		return fatal;
	}

	/**
	 * 주어진 오류가 JVM이 더 이상 정상 동작을 보장할 수 없는 {@link VirtualMachineError}
	 * ({@link OutOfMemoryError}, {@link StackOverflowError} 등)이면 다시 던진다.
	 * <p>
	 * 차트는 이 메소드를 부르기 전에 실패(또는 취소)로 확정해 두므로, 오류가 전파되어도
	 * 차트가 RUNNING에 남지 않는다.
	 *
	 * @param error	검사할 오류.
	 */
	private static void throwIfFatal(Throwable error) {
		if ( error instanceof VirtualMachineError vme ) {
			throw vme;
		}
	}
	
	/**
	 * 상태 계층을 검사한다.
	 * <ul>
	 *   <li>경로상 부모가 있는 상태는 그 부모가 등록된 {@link CompositeState}여야 한다.</li>
	 *   <li>복합 상태의 최초 하위 상태는 등록되어 있고 직속 하위 상태여야 한다.</li>
	 *   <li>복합 상태는 최종 상태가 될 수 없다. 최종 상태 판정은 가장 안쪽 상태로 하기 때문이다.</li>
	 * </ul>
	 *
	 * @throws IllegalStateException	위 조건을 어긴 상태가 있는 경우.
	 */
	private void checkHierarchy() {
		List<utils.statechart.State<C>> states = m_states.values().stream()
											.sorted(Comparator.comparing(utils.statechart.State::getPath))
											.toList();
		for ( utils.statechart.State<C> state: states ) {
			String path = state.getPath();
			String parentPath = StatePath.parentOf(path);
			if ( parentPath != null ) {
				utils.Preconditions.checkState(m_states.get(parentPath) instanceof CompositeState,
						"parent of state '%s' should be a registered CompositeState: %s", path, parentPath);
			}
			if ( state instanceof CompositeState<C> composite ) {
				String initPath = composite.getInitialStatePath();
				utils.Preconditions.checkState(m_states.containsKey(initPath),
						"initial sub-state of CompositeState '%s' is not registered: %s", path, initPath);
				utils.Preconditions.checkState(path.equals(StatePath.parentOf(initPath)),
						"initial sub-state of CompositeState '%s' is not its direct child: %s", path, initPath);
				utils.Preconditions.checkState(!m_finalStates.contains(state),
						"CompositeState cannot be a finalState: %s", path);
			}
		}
	}

	/**
	 * 최상위 상태부터 주어진 상태까지의 조상 체인을 반환한다. 마지막 원소가 주어진 상태다.
	 */
	private List<utils.statechart.State<C>> getAncestorChain(utils.statechart.State<C> state) {
		List<utils.statechart.State<C>> chain = new ArrayList<>();
		for ( utils.statechart.State<C> s = state; s != null; ) {
			chain.add(0, s);
			String parentPath = StatePath.parentOf(s.getPath());
			s = (parentPath != null) ? m_states.get(parentPath) : null;
		}
		return chain;
	}

	/**
	 * 주어진 상태가 복합 상태이면 최초 하위 상태를 따라 가장 안쪽 상태까지 내려간다.
	 */
	private utils.statechart.State<C> drillDown(utils.statechart.State<C> state) {
		while ( state instanceof CompositeState<C> composite ) {
			state = m_states.get(composite.getInitialStatePath());
		}
		return state;
	}

	/**
	 * 목표 상태의 조상 체인 중 {@code depth}번째부터 목표 상태까지 바깥쪽부터 진입하고,
	 * 목표가 복합 상태이면 최초 하위 상태를 따라 가장 안쪽 상태까지 이어서 진입한다.
	 * 진입한 상태의 {@code enter()} 안에서 차트가 종료되면 그 자리에서 멈춘다.
	 *
	 * @param target	목표 상태.
	 * @param depth		이미 진입해 있어 건너뛸 조상의 수.
	 */
	private void enterDownTo(utils.statechart.State<C> target, int depth) {
		List<utils.statechart.State<C>> chain = getAncestorChain(target);
		for ( int i = depth; i < chain.size(); ++i ) {
			enterState(chain.get(i));
			if ( isTerminating() ) {
				// enter() 안에서 종료가 요청되었다. 더 안쪽 상태에는 진입하지 않는다.
				return;
			}
		}
		utils.statechart.State<C> state = target;
		while ( state instanceof CompositeState<C> composite ) {
			state = m_states.get(composite.getInitialStatePath());
			enterState(state);
			if ( isTerminating() ) {
				return;
			}
		}
	}

	private void enterState(utils.statechart.State<C> state) {
		state.enter();
		// enter()가 반환되면 진입한 것으로 본다. enter() 안에서 종료가 요청되었더라도 목록에 넣어, 정리할 때
		// 자식부터 부모 순으로 진출하게 한다.
		m_activeStateChain.add(state);
		m_currentState = state;
		getLogger().debug("{}: entered state: {}", getName(), state.getPath());
	}

	/**
	 * 가장 안쪽 상태를 진출한다.
	 * <p>
	 * 상태를 목록에서 먼저 뺀 뒤 {@code exit()}를 호출한다. {@code exit()} 안에서 취소되어 {@link #cancelWork()}가
	 * 남은 상태를 진출시키더라도 이 상태를 다시 진출시키지 않게 하기 위해서다. {@code exit()}가 예외를 던져도
	 * 상태는 이미 목록에서 빠져 있다.
	 */
	private void exitInnermostState() {
		utils.statechart.State<C> state = m_currentState;
		removeInnermostState();
		state.exit();
		getLogger().debug("{}: left state: {}", getName(), state.getPath());
	}

	/**
	 * 최종 상태에 도달했을 때, 최종 상태를 둘러싼 바깥 상태(복합 상태)들을 안쪽부터 진출한다.
	 * <p>
	 * 최종 상태 자신은 진출하지 않고 진입해 있는 상태로 남긴다({@link TerminalState}는 진출할 수 없다).
	 * 진출에 실패해도 경고 로그만 남기고 나머지 바깥 상태의 진출과 종료를 계속한다. 취소할 때
	 * ({@link #cancelWork()})와 같은 규칙이다. 단 {@link VirtualMachineError}는 바로 다시 던지며,
	 * {@link #executeTransition(Transition, Signal)}이 차트를 실패시킨다.
	 */
	private void exitEnclosingStates() {
		// 최종 상태(마지막 원소) 바로 바깥 상태부터 차례로 목록에서 빼고 진출한다.
		while ( m_activeStateChain.size() > 1 ) {
			utils.statechart.State<C> state = m_activeStateChain.remove(m_activeStateChain.size() - 2);
			try {
				state.exit();
				getLogger().debug("{}: left state: {}", getName(), state.getPath());
			}
			catch ( VirtualMachineError e ) {
				throw e;
			}
			catch ( Throwable e ) {
				getLogger().warn("{}: failed to exit enclosing state on reaching final state: {}, cause={}",
									getName(), state.getPath(), "" + e);
			}
			if ( isTerminating() ) {
				// exit() 안에서 종료가 요청되었다. 나머지 정리는 종료를 확정할 때 한다.
				return;
			}
		}
	}

	private void removeInnermostState() {
		m_activeStateChain.remove(m_activeStateChain.size() - 1);
		m_currentState = m_activeStateChain.isEmpty() ? null : m_activeStateChain.get(m_activeStateChain.size() - 1);
	}
}
