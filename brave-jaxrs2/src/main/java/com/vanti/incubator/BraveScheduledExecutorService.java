package com.vanti.incubator;

import com.github.kristofa.brave.BraveCallable;
import com.github.kristofa.brave.BraveExecutorService;
import com.github.kristofa.brave.BraveRunnable;
import com.github.kristofa.brave.ServerSpanThreadBinder;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Decorator for a ScheduledExecutorService that adds support for Brave. Without using this span information will be
 * lost as the thread changes between the caller and the scheduled task.
 *
 * @author Matt
 */
public class BraveScheduledExecutorService extends BraveExecutorService implements ScheduledExecutorService {

  private final ScheduledExecutorService delegate;
  private ServerSpanThreadBinder threadBinder;

  public BraveScheduledExecutorService(ScheduledExecutorService delegate, ServerSpanThreadBinder threadBinder) {
    super(delegate, threadBinder);
    this.delegate = delegate;
    this.threadBinder = threadBinder;
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return delegate.schedule(new BraveCallable<V>(callable, threadBinder), delay, unit);
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return delegate.schedule(new BraveRunnable(command, threadBinder), delay, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    return delegate.scheduleAtFixedRate(new BraveRunnable(command, threadBinder), initialDelay, period, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return delegate.scheduleWithFixedDelay(new BraveRunnable(command, threadBinder), initialDelay, delay, unit);
  }
}
