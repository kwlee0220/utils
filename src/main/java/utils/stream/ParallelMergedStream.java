package utils.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;

import utils.async.AbstractThreadedExecution;
import utils.async.AsyncResult;
import utils.async.CancellableWork;
import utils.async.Guard;
import utils.func.FOption;
import utils.func.Try;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ParallelMergedStream<T> implements FStream<T> {
	private final SuppliableFStream<T> m_merged;
	
	private final Guard m_guard = Guard.create();
	@GuardedBy("m_guard") private final FStream<? extends FStream<? extends T>> m_gen;
	@GuardedBy("m_guard") private boolean m_closed = false;
	@GuardedBy("m_guard") private boolean m_endOfSource = false;
	@GuardedBy("m_guard") private List<Harvester> m_runningHarvesters;
	
	ParallelMergedStream(FStream<? extends FStream<? extends T>> gen, int threadCount) {
		m_gen = gen;
		m_merged = FStream.pipe(threadCount);
		m_runningHarvesters = new ArrayList<>(threadCount);

		m_guard.run(() -> {
			for ( int i =0; i < threadCount; ++i ) {
				if ( !startNextHarvesterInGuard() ) {
					break;
				}
			}
		});
	}

	@Override
	public void close() throws Exception {
		List<Harvester> harvesters = m_guard.get(() -> {
			m_closed = true;
			m_merged.closeQuietly();
			m_gen.closeQuietly();
			
			return new ArrayList<>(m_runningHarvesters);
		});
			
		for ( Harvester harvester: harvesters ) {
			harvester.cancel(true);
		}
		Try.run(() -> {
			for ( Harvester harvester: harvesters ) {
				harvester.poll(100, TimeUnit.MILLISECONDS);
			}
		});
		m_guard.run(m_runningHarvesters::clear);
	}

	@Override
	public FOption<T> next() {
		return m_merged.next();
	}
	
	private boolean startNextHarvesterInGuard() {
		FOption<Harvester> oh = m_gen.next().map(Harvester::new);
		if ( oh.isPresent() ) {
			Harvester harvester = oh.get();
			
			m_runningHarvesters.add(harvester);
			harvester.whenFinished(r -> m_guard.run(() -> onHarvesterFinishedInGuard(harvester, r)));
			harvester.start();
			
			return true;
		}
		else {
			m_endOfSource = true;
			return false;
		}
	}
	
	private void onHarvesterFinishedInGuard(Harvester harvester, AsyncResult<Void> result) {
		m_runningHarvesters.remove(harvester);
		if ( (result.isCompleted() || result.isFailed()) && !m_closed ) {
			if ( !startNextHarvesterInGuard() ) {
				if ( m_runningHarvesters.isEmpty() ) {
					// 더 이상 harvest할 FStream이 없을 때
					m_merged.endOfSupply();
				}
			}
		}
	}
	
	private class Harvester extends AbstractThreadedExecution<Void>
							implements CancellableWork {
		private final FStream<? extends T> m_src;
		
		Harvester(FStream<? extends T> src) {
			m_src = src;
		}

		@Override
		protected Void executeWork() throws InterruptedException, CancellationException,
											Exception {
			FOption<? extends T> next;
			while ( (next = m_src.next()).isPresent() ) {
				if ( !isRunning() ) {
					return null;
				}
				
				if ( !m_merged.supply(next.get()) ) {
					throw new CancellationException();
				}
			}

			return null;
		}

		@Override
		public boolean cancelWork() {
			return true;
		}
	}
}
