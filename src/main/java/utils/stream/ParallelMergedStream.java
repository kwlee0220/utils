package utils.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.annotation.Nullable;

import net.jcip.annotations.GuardedBy;
import utils.async.CancellableWork;
import utils.async.ExecutableExecution;
import utils.async.ThreadInterruptedException;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ParallelMergedStream<T> implements FStream<T> {
	private final FStream<? extends FStream<? extends T>> m_gen;
	private final SuppliableFStream<T> m_merged;

	@GuardedBy("m_lock") private boolean m_closed = false;
	@GuardedBy("m_lock") private List<Harvester> m_activeHarvesters;
	
	ParallelMergedStream(FStream<? extends FStream<? extends T>> gen, int threadCount) {
		m_gen = gen;
		m_merged = FStream.pipe(threadCount);
		m_activeHarvesters = new ArrayList<>(threadCount);
		
		for ( int i =0; i < threadCount && startNextHarvester(null); ++i ) { }
	}

	@Override
	public void close() throws Exception {
		m_merged.getLock().lock();
		try {
			m_closed = true;
			m_merged.close();
			m_gen.closeQuietly();
			
			for ( Harvester harvester: m_activeHarvesters ) {
				harvester.cancel();
			}
		}
		finally {
			m_merged.getLock().unlock();
		}
	}

	@Override
	public FOption<T> next() {
		m_merged.getLock().lock();
		try {
			while ( true ) {
				FOption<T> next = m_merged.poll();
				if ( next.isPresent() ) {
					return next;
				}
				
				if ( m_activeHarvesters.size() == 0 || m_closed ) {
					return FOption.empty();
				}
				
				try {
					m_merged.getCondition().await();
				}
				catch ( InterruptedException e ) {
					throw new ThreadInterruptedException("" + e);
				}
			}
		}
		finally {
			m_merged.getLock().unlock();
		}
	}
	
	private boolean startNextHarvester(@Nullable Harvester invoker) {
		m_merged.getLock().lock();
		try {
			if ( invoker != null ) {
				m_activeHarvesters.remove(invoker);
			}
			
			FOption<? extends FStream<? extends T>> src = m_gen.next();
			if ( src.isPresent() ) {
				Harvester harvester = new Harvester(src.get());
				harvester.whenDone(() -> startNextHarvester(harvester));
				
				m_activeHarvesters.add(harvester);
				harvester.start();
				
				return true;
			}
			else {
				m_merged.getCondition().signalAll();
				return false;
			}
		}
		finally {
			m_merged.getLock().unlock();
		}
	}
	
	private class Harvester extends ExecutableExecution<Void>
										implements CancellableWork {
		private final FStream<? extends T> m_src;
		
		Harvester(FStream<? extends T> src) {
			m_src = src;
		}

		@Override
		protected Void executeWork() throws InterruptedException, CancellationException, Exception {
			FOption<? extends T> next;
			try {
				while ( (next = m_src.next()).isPresent() ) {
					if ( !isRunning() ) {
						return null;
					}
					
					m_merged.supply(next.get());
				}
			}
			catch ( Exception e ) {
				m_merged.endOfSupply(e);
			}
			
			return null;
		}

		@Override
		public boolean cancelWork() {
			return true;
		}
	}
}
