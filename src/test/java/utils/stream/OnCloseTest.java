package utils.stream;


import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class OnCloseTest {
	@Test
	public void test0() throws Exception {
		Runnable task = mock(Runnable.class);
		
		FStream<Integer> stream = FStream.from(Lists.newArrayList(1, 2, 4))
										.onClose(task);
		Assertions.assertEquals(3, stream.count());
		verify(task, times(1)).run();
	}

	/**
	 * 닫으면 원본을 먼저 닫고 작업을 실행한다(2026-11-02 이전에는 원본을 닫지 않았다).
	 */
	@Test
	public void closesSourceBeforeTask() throws Exception {
		List<String> events = new java.util.ArrayList<>();
		FStream<Integer> src = FStream.of(1, 2, 3).onClose(() -> events.add("source"));
		FStream<Integer> stream = src.onClose(() -> events.add("task"));

		Assertions.assertEquals(List.of(1, 2, 3), stream.toList());
		Assertions.assertEquals(List.of("source", "task"), events);
	}

	/**
	 * 연달아 등록한 작업은 등록 순서대로 모두 실행된다(이전에는 가장 바깥 작업만 실행됐다).
	 */
	@Test
	public void chainedTasksRunInRegistrationOrder() throws Exception {
		List<String> events = new java.util.ArrayList<>();
		FStream<Integer> stream = FStream.of(1).onClose(() -> events.add("first"))
												.onClose(() -> events.add("second"));
		stream.close();
		stream.close();		// 두 번째 close는 아무 일도 하지 않는다
		Assertions.assertEquals(List.of("first", "second"), events);
		Assertions.assertThrows(IllegalStateException.class, stream::next);
	}

	/**
	 * 원본 닫기가 실패해도 작업은 실행되고, 작업의 예외는 원본 예외의 suppressed가 된다.
	 */
	@Test
	public void taskRunsEvenIfSourceCloseFails() throws Exception {
		Runnable task = mock(Runnable.class);
		doThrow(new IllegalStateException("task failed")).when(task).run();
		FStream<Integer> failingSource = new FStreams.AbstractFStream<Integer>() {
			@Override
			protected void closeInGuard() throws Exception {
				throw new java.io.IOException("source failed");
			}
			@Override
			protected FOption<Integer> nextInGuard() {
				return FOption.empty();
			}
		};

		java.io.IOException e = Assertions.assertThrows(java.io.IOException.class,
														() -> failingSource.onClose(task).close());
		verify(task, times(1)).run();
		Assertions.assertEquals("source failed", e.getMessage());
		Assertions.assertEquals("task failed", e.getSuppressed()[0].getMessage());
	}
}
