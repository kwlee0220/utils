package utils.statechart;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link TransitionTable}과 {@link AbstractState#on(Signal, Transition)} 계열 메소드의 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TransitionTableTest {
	private static class TestCtx extends AbstractStateContext<TestCtx> { }

	private record Named(String name) implements Signal { }
	private record Failed(Throwable cause) implements Signal { }
	private record Value(int value) implements Signal { }

	private final TestCtx m_ctx = new TestCtx();

	@Test
	public void empty_table_selects_nothing() {
		TransitionTable<TestCtx> table = new TransitionTable<>();

		Assertions.assertTrue(table.isEmpty());
		Assertions.assertEquals(Optional.empty(), table.select(new Named("x")));
	}

	@Test
	public void signal_entry_matches_by_equals() {
		Transition<TestCtx> toB = Transitions.to("/B");
		TransitionTable<TestCtx> table = new TransitionTable<TestCtx>().on(new Named("go"), toB);

		// record는 equals가 값을 비교하므로, 다른 인스턴스라도 값이 같으면 맞는다.
		Assertions.assertSame(toB, table.select(new Named("go")).orElseThrow());
		Assertions.assertTrue(table.select(new Named("stop")).isEmpty());
	}

	@Test
	public void type_entry_matches_instances_of_the_type() {
		Transition<TestCtx> toError = Transitions.to("/Error");
		TransitionTable<TestCtx> table = new TransitionTable<TestCtx>().on(Failed.class, toError);

		Assertions.assertSame(toError, table.select(new Failed(new RuntimeException())).orElseThrow());
		Assertions.assertTrue(table.select(new Named("x")).isEmpty());
	}

	@Test
	public void selector_entry_builds_transition_from_signal() {
		TransitionTable<TestCtx> table = new TransitionTable<TestCtx>()
				.on(Value.class, sig -> Transitions.to("/V" + sig.value()));

		Assertions.assertEquals(Optional.of("/V7"), table.select(new Value(7)).orElseThrow().getTargetStatePath());
	}

	@Test
	public void selector_returning_null_falls_through_to_next_entry() {
		Transition<TestCtx> small = Transitions.to("/Small");
		Transition<TestCtx> other = Transitions.to("/Other");
		TransitionTable<TestCtx> table = new TransitionTable<TestCtx>()
				.on(Value.class, sig -> sig.value() < 10 ? small : null)
				.on(Value.class, other);

		Assertions.assertSame(small, table.select(new Value(3)).orElseThrow());
		Assertions.assertSame(other, table.select(new Value(30)).orElseThrow());
	}

	@Test
	public void first_matching_entry_wins() {
		Transition<TestCtx> first = Transitions.to("/First");
		Transition<TestCtx> second = Transitions.to("/Second");
		TransitionTable<TestCtx> table = new TransitionTable<TestCtx>()
				.on(new Named("go"), first)
				.on(Named.class, second);

		Assertions.assertSame(first, table.select(new Named("go")).orElseThrow());
		Assertions.assertSame(second, table.select(new Named("other")).orElseThrow());
	}

	@Test
	public void guarded_entry_selects_only_when_guard_holds() {
		Transition<TestCtx> done = Transitions.to("/Done");
		Transition<TestCtx> stay = Transitions.stay();
		TransitionTable<TestCtx> table = new TransitionTable<TestCtx>()
				.on(Value.class, v -> v.value() >= 100, done)
				.on(Value.class, stay);

		Assertions.assertSame(done, table.select(new Value(100)).orElseThrow());
		Assertions.assertSame(stay, table.select(new Value(50)).orElseThrow());
		Assertions.assertTrue(table.select(new Named("x")).isEmpty());
	}

	@Test
	public void null_arguments_rejected() {
		TransitionTable<TestCtx> table = new TransitionTable<>();
		Transition<TestCtx> t = Transitions.stay();

		Assertions.assertThrows(IllegalArgumentException.class, () -> table.on((Signal)null, t));
		Assertions.assertThrows(IllegalArgumentException.class, () -> table.on(new Named("x"), null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> table.on((Class<Named>)null, t));
		Assertions.assertThrows(IllegalArgumentException.class, () -> table.select(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> table.on(Named.class, null, t));
	}

	// ---- AbstractState.on ----

	@Test
	public void abstract_state_selects_from_its_table_by_default() {
		Transition<TestCtx> toBusy = Transitions.to("/Busy");
		AbstractState<TestCtx> idle = new AbstractState<TestCtx>("/Idle", m_ctx) { }
										.on(new Named("start"), toBusy)
										.on(Failed.class, sig -> Transitions.fail("/Error", s -> sig.cause()));

		Assertions.assertSame(toBusy, idle.selectTransition(new Named("start")).orElseThrow());
		Assertions.assertEquals(Optional.of("/Error"),
								idle.selectTransition(new Failed(new RuntimeException())).orElseThrow()
									.getTargetStatePath());
		Assertions.assertTrue(idle.selectTransition(new Named("unknown")).isEmpty());
	}

	@Test
	public void default_state_needs_no_subclass() {
		Transition<TestCtx> toBusy = Transitions.to("/Busy");
		DefaultState<TestCtx> idle = new DefaultState<>("/Idle", m_ctx);
		idle.on(new Named("start"), toBusy)
			.on(Value.class, v -> v.value() < 0, Transitions.to("/Error"));

		Assertions.assertSame(toBusy, idle.selectTransition(new Named("start")).orElseThrow());
		Assertions.assertEquals(Optional.of("/Error"), idle.selectTransition(new Value(-1)).orElseThrow()
															.getTargetStatePath());
		Assertions.assertTrue(idle.selectTransition(new Value(1)).isEmpty());
	}

	@Test
	public void terminalState_rejects_guarded_registration() {
		TerminalState<TestCtx> sink = new TerminalState<>("/Done", m_ctx);
		Assertions.assertThrows(UnsupportedOperationException.class,
								() -> sink.on(Value.class, v -> true, Transitions.to("/A")));
	}

	@Test
	public void abstract_state_without_entries_selects_nothing() {
		AbstractState<TestCtx> state = new AbstractState<TestCtx>("/S", m_ctx) { };

		Assertions.assertTrue(state.selectTransition(new Named("x")).isEmpty());
	}

	@Test
	public void triggeredState_falls_back_to_table_for_other_signals() {
		Named trigger = new Named("trigger");
		Transition<TestCtx> toA = Transitions.to("/A");
		Transition<TestCtx> toError = Transitions.to("/Error");
		AbstractState<TestCtx> state = new TriggeredState<>("/S", m_ctx, trigger, toA)
											.on(Failed.class, toError);

		Assertions.assertSame(toA, state.selectTransition(trigger).orElseThrow());
		Assertions.assertSame(toError, state.selectTransition(new Failed(new RuntimeException())).orElseThrow());
		Assertions.assertTrue(state.selectTransition(new Named("other")).isEmpty());
	}

	@Test
	public void terminalState_rejects_transition_registration() {
		TerminalState<TestCtx> sink = new TerminalState<>("/Done", m_ctx);
		FailureState<TestCtx> failure = new FailureState<>("/Error", m_ctx);
		Transition<TestCtx> t = Transitions.to("/A");

		Assertions.assertThrows(UnsupportedOperationException.class, () -> sink.on(new Named("x"), t));
		Assertions.assertThrows(UnsupportedOperationException.class, () -> sink.on(Named.class, t));
		Assertions.assertThrows(UnsupportedOperationException.class, () -> failure.on(Named.class, sig -> t));
	}

	@Test
	public void table_drives_a_running_chart_including_composite_fallback() {
		Named next = new Named("next");
		Failed failed = new Failed(new IllegalStateException("boom"));

		StateChart<TestCtx> chart = new StateChart<>(m_ctx);
		// 하위 상태가 처리하지 않은 Failed는 바깥 복합 상태의 표가 처리한다.
		chart.addState(new DefaultCompositeState<>("/Run", m_ctx, "/Run/A")
							.on(Failed.class, sig -> Transitions.fail("/Error", s -> sig.cause())));
		chart.addState(new DefaultState<>("/Run/A", m_ctx).on(next, Transitions.to("/Run/B")));
		chart.addState(new DefaultState<>("/Run/B", m_ctx));
		chart.addState(new FailureState<>("/Error", m_ctx));		// 자동으로 최종 상태가 된다.
		chart.setInitialState("/Run");
		chart.start();

		chart.handleSignal(next);
		Assertions.assertEquals("/Run/B", chart.getCurrentState().getPath());

		chart.handleSignal(failed);
		Assertions.assertSame(failed.cause(), chart.poll().getFailureCause());
	}
}
