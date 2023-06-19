/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/

package org.osgi.util.promise;

import static java.util.Objects.requireNonNull;
import static org.osgi.util.promise.PromiseImpl.uncaughtException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collector;

import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.util.promise.PromiseImpl.InlineCallback;

/**
 * Promise factory to create Deferred and Promise objects.
 * <p>
 * Instances of this class can be used to create Deferred and Promise objects
 * which use the executors used to construct this object for any callback or
 * scheduled operation execution.
 * 
 * @Immutable
 * @author $Id: 9e941a7a33ba3a70cdb8821972dd2b69abbcd3f0 $
 * @since 1.1
 */
@ConsumerType
public class PromiseFactory {

	/**
	 * Defines the options for a Promise factory.
	 * <p>
	 * The default options are no options unless the boolean system property
	 * {@code org.osgi.util.promise.allowCurrentThread} is set to {@code false}.
	 * When this is the case, the option {@link Option#CALLBACKS_EXECUTOR_THREAD}
	 * is a default option.
	 *
	 * @since 1.2
	 */
	public enum Option {
		/**
		 * Run callbacks on an executor thread. If this option is not set,
		 * callbacks added to a resolved Promise may be immediately called on
		 * the caller's thread to avoid a thread context switch.
		 */
		CALLBACKS_EXECUTOR_THREAD
	}

	/**
	 * The default factory which uses the default callback executor and default
	 * scheduled executor.
	 */
	final static PromiseFactory				defaultFactory	= new PromiseFactory(
			null, null);

	/**
	 * The executor to use for callbacks. If {@code null}, the default callback
	 * executor is used.
	 */
	private final Executor					callbackExecutor;
	/**
	 * The executor to use for scheduled operations. If {@code null}, the
	 * default scheduled executor is used.
	 */
	private final ScheduledExecutorService	scheduledExecutor;

	private final boolean					allowCurrentThread;

	/**
	 * Create a new PromiseFactory with the specified callback executor.
	 * <p>
	 * The default scheduled executor and default options will be used.
	 * 
	 * @param callbackExecutor The executor to use for callbacks. {@code null}
	 *            can be specified for the default callback executor.
	 */
	public PromiseFactory(Executor callbackExecutor) {
		this(callbackExecutor, null, defaultOptions());
	}

	/**
	 * Create a new PromiseFactory with the specified callback executor and
	 * specified scheduled executor.
	 * <p>
	 * The default options will be used.
	 * 
	 * @param callbackExecutor The executor to use for callbacks. {@code null}
	 *            can be specified for the default callback executor.
	 * @param scheduledExecutor The scheduled executor for use for scheduled
	 *            operations. {@code null} can be specified for the default
	 *            scheduled executor.
	 */
	public PromiseFactory(Executor callbackExecutor,
			ScheduledExecutorService scheduledExecutor) {
		this(callbackExecutor, scheduledExecutor, defaultOptions());

	}

	private static Option[] defaultOptions() {
		boolean allowCurrentThread = Boolean.parseBoolean(
				System.getProperty("org.osgi.util.promise.allowCurrentThread",
						Boolean.TRUE.toString()));
		return allowCurrentThread ? new Option[0] : new Option[] {
				Option.CALLBACKS_EXECUTOR_THREAD
		};
	}

	/**
	 * Create a new PromiseFactory with the specified callback executor,
	 * specified scheduled executor, and specified options.
	 * 
	 * @param callbackExecutor The executor to use for callbacks. {@code null}
	 *            can be specified for the default callback executor.
	 * @param scheduledExecutor The scheduled executor for use for scheduled
	 *            operations. {@code null} can be specified for the default
	 *            scheduled executor.
	 * @param options Options for PromiseFactory.
	 * @since 1.2
	 */
	public PromiseFactory(Executor callbackExecutor,
			ScheduledExecutorService scheduledExecutor, Option... options) {
		this.callbackExecutor = callbackExecutor;
		this.scheduledExecutor = scheduledExecutor;
		boolean callbacksExecutorOnly = false;
		for (Option option : options) {
			if (option == Option.CALLBACKS_EXECUTOR_THREAD) {
				callbacksExecutorOnly = true;
				continue;
			}
			requireNonNull(option);
			throw new AssertionError("unrecognized option: " + option);
		}
		this.allowCurrentThread = !callbacksExecutorOnly;
	}

	/**
	 * Returns the executor to use for callbacks.
	 * 
	 * @return The executor to use for callbacks. This will be the default
	 *         callback executor if {@code null} was specified for the callback
	 *         executor when this PromiseFactory was created.
	 */
	public Executor executor() {
		if (callbackExecutor == null) {
			return DefaultExecutors.callbackExecutor();
		}
		return callbackExecutor;
	}

	/**
	 * Returns the scheduled executor to use for scheduled operations.
	 * 
	 * @return The scheduled executor to use for scheduled operations. This will
	 *         be the default scheduled executor if {@code null} was specified
	 *         for the scheduled executor when this PromiseFactory was created.
	 */
	public ScheduledExecutorService scheduledExecutor() {
		if (scheduledExecutor == null) {
			return DefaultExecutors.scheduledExecutor();
		}
		return scheduledExecutor;
	}

	/**
	 * Create a new Deferred with the callback executor and scheduled executor
	 * of this PromiseFactory object.
	 * <p>
	 * Use this method instead of {@link Deferred#Deferred()} to create a new
	 * {@link Deferred} whose associated Promise uses executors other than the
	 * default executors.
	 * 
	 * @param <T> The value type associated with the returned Deferred.
	 * @return A new {@link Deferred} with the callback and scheduled executors
	 *         of this PromiseFactory object
	 */
	public <T> Deferred<T> deferred() {
		return new Deferred<>(this);
	}

	/**
	 * Returns a new Promise that has been resolved with the specified value.
	 * <p>
	 * The returned Promise uses the callback executor and scheduled executor of
	 * this PromiseFactory object.
	 * <p>
	 * Use this method instead of {@link Promises#resolved(Object)} to create a
	 * Promise which uses executors other than the default executors.
	 * 
	 * @param <T> The value type associated with the returned Promise.
	 * @param value The value of the resolved Promise.
	 * @return A new Promise that has been resolved with the specified value.
	 */
	public <T> Promise<T> resolved(T value) {
		return new ResolvedPromiseImpl<>(value, this);
	}

	/**
	 * Returns a new Promise that has been resolved with the specified failure.
	 * <p>
	 * The returned Promise uses the callback executor and scheduled executor of
	 * this PromiseFactory object.
	 * <p>
	 * Use this method instead of {@link Promises#failed(Throwable)} to create a
	 * Promise which uses executors other than the default executors.
	 * 
	 * @param <T> The value type associated with the returned Promise.
	 * @param failure The failure of the resolved Promise. Must not be
	 *            {@code null}.
	 * @return A new Promise that has been resolved with the specified failure.
	 */
	public <T> Promise<T> failed(Throwable failure) {
		return new FailedPromiseImpl<>(failure, this);
	}

	/**
	 * Returns a new Promise that will hold the result of the specified task.
	 * <p>
	 * The returned Promise uses the callback executor and scheduled executor of
	 * this PromiseFactory object.
	 * <p>
	 * The specified task will be executed on the {@link #executor() callback
	 * executor}.
	 * 
	 * @param <T> The value type associated with the returned Promise.
	 * @param task The task whose result will be available from the returned
	 *            Promise.
	 * @return A new Promise that will hold the result of the specified task.
	 */
	public <T> Promise<T> submit(Callable< ? extends T> task) {
		DeferredPromiseImpl<T> promise = new DeferredPromiseImpl<>(this);
		Runnable submit = promise.new Submit(task);
		try {
			executor().execute(submit);
		} catch (Exception t) {
			promise.tryResolve(null, t);
		}
		return promise.orDone();
	}

	/**
	 * Returns a new Promise that is a latch on the resolution of the specified
	 * Promises.
	 * <p>
	 * The returned Promise uses the callback executor and scheduled executor of
	 * this PromiseFactory object.
	 * <p>
	 * The returned Promise acts as a gate and must be resolved after all of the
	 * specified Promises are resolved.
	 * 
	 * @param <T> The value type of the List value associated with the returned
	 *            Promise.
	 * @param <S> The value type of the specified Promises.
	 * @param promises The Promises which must be resolved before the returned
	 *            Promise must be resolved. Must not be {@code null} and all of
	 *            the elements in the collection must not be {@code null}.
	 * @return A Promise that must be successfully resolved with a List of the
	 *         values in the order of the specified Promises if all the
	 *         specified Promises are successfully resolved. The List in the
	 *         returned Promise is the property of the caller and is modifiable.
	 *         The returned Promise must be resolved with a failure of
	 *         {@link FailedPromisesException} if any of the specified Promises
	 *         are resolved with a failure. The failure
	 *         {@link FailedPromisesException} must contain all of the specified
	 *         Promises which resolved with a failure.
	 */
	public <T, S extends T> Promise<List<T>> all(
			Collection<Promise<S>> promises) {
		if (promises.isEmpty()) {
			List<T> value = new ArrayList<>();
			return resolved(value);
		}

		/* make a copy and capture the ordering */
		List<Promise<S>> list = new ArrayList<>(promises);

		DeferredPromiseImpl<List<T>> chained = new DeferredPromiseImpl<>(this);
		All<T,S> all = new All<>(chained, list);
		for (Promise<S> p : list) {
			p.onResolve(all);
		}
		return chained.orDone();
	}

	/**
	 * A callback used to resolve the specified Promise when the specified list
	 * of Promises are resolved for the {@link PromiseFactory#all(Collection)}
	 * method.
	 * 
	 * @ThreadSafe
	 */
	private static final class All<T, S extends T>
			implements Runnable, InlineCallback {
		private final DeferredPromiseImpl<List<T>>	chained;
		private final List<Promise<S>>				promises;
		private final AtomicInteger					promiseCount;

		All(DeferredPromiseImpl<List<T>> chained, List<Promise<S>> promises) {
			this.chained = requireNonNull(chained);
			this.promises = requireNonNull(promises);
			this.promiseCount = new AtomicInteger(promises.size());
		}

		@Override
		public void run() {
			if (promiseCount.decrementAndGet() != 0) {
				return;
			}
			List<T> value = new ArrayList<>(promises.size());
			List<Promise< ? >> failed = new ArrayList<>(promises.size());
			for (Promise<S> p : promises) {
				PromiseImpl.result(p, (v, f) -> {
					if (f != null) {
						failed.add(p);
					} else {
						value.add(v);
					}
				});
			}
			if (failed.isEmpty()) {
				chained.tryResolve(value, null);
			} else {
				Throwable cause;
				try {
					cause = failed.get(0).getFailure();
				} catch (Throwable e) {
					cause = e;
				}
				chained.tryResolve(null,
						new FailedPromisesException(failed, cause));
			}
		}
	}

	/**
	 * Returns an Executor implementation that executes tasks immediately on the
	 * thread calling the {@code Executor.execute} method.
	 * 
	 * @return An Executor implementation that executes tasks immediately on the
	 *         thread calling the {@code Executor.execute} method.
	 */
	public static Executor inlineExecutor() {
		return new InlineExecutor();
	}

	boolean allowCurrentThread() {
		return allowCurrentThread;
	}

	/**
	 * An Executor implementation which executes the task immediately on the
	 * thread calling the {@code Executor.execute} method.
	 * 
	 * @Immutable
	 */
	private static final class InlineExecutor implements Executor {
		InlineExecutor() {
		}

		@Override
		public void execute(Runnable callback) {
			callback.run();
		}
	}

	/**
	 * Default executors for Promises.
	 * 
	 * @Immutable
	 */
	private static final class DefaultExecutors
			implements ThreadFactory, RejectedExecutionHandler, Runnable {
		private static final DefaultExecutors	callbacks;
		private static final ScheduledExecutor	scheduledExecutor;
		private static final ThreadPoolExecutor	callbackExecutor;
		static {
			callbacks = new DefaultExecutors();
			scheduledExecutor = new ScheduledExecutor(2, callbacks);
			callbackExecutor = new ThreadPoolExecutor(0, 64, 60L,
					TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
					callbacks, callbacks);
		}

		static Executor callbackExecutor() {
			return callbackExecutor;
		}

		static ScheduledExecutorService scheduledExecutor() {
			return scheduledExecutor;
		}

		private final AtomicBoolean	shutdownHookInstalled;
		private final ThreadFactory	delegateThreadFactory;

		private DefaultExecutors() {
			shutdownHookInstalled = new AtomicBoolean();
			delegateThreadFactory = Executors.defaultThreadFactory();
		}

		/**
		 * Executor threads should not prevent VM from exiting.
		 */
		@Override
		public Thread newThread(Runnable r) {
			if (shutdownHookInstalled.compareAndSet(false, true)) {
				Thread shutdownThread = delegateThreadFactory.newThread(this);
				shutdownThread.setName(
						"ExecutorShutdownHook," + shutdownThread.getName());
				try {
					Runtime.getRuntime().addShutdownHook(shutdownThread);
				} catch (IllegalStateException e) {
					// VM is already shutting down...
					callbackExecutor.shutdown();
					scheduledExecutor.shutdown();
				}
			}
			Thread t = delegateThreadFactory.newThread(r);
			t.setName("PromiseFactory," + t.getName());
			t.setDaemon(true);
			return t;
		}

		/**
		 * Call the callback using the caller's thread because the thread pool
		 * rejected the execution.
		 */
		@Override
		public void rejectedExecution(Runnable callback,
				ThreadPoolExecutor executor) {
			try {
				callback.run();
			} catch (Throwable t) {
				uncaughtException(t);
			}
		}

		/**
		 * Shutdown hook
		 */
		@Override
		public void run() {
			// limit new thread creation
			callbackExecutor.setMaximumPoolSize(
					Math.max(1, callbackExecutor.getPoolSize()));
			// Run all delayed callbacks now
			scheduledExecutor.shutdown();
			BlockingQueue<Runnable> queue = scheduledExecutor.getQueue();
			if (!queue.isEmpty()) {
				for (Object r : queue.toArray()) {
					if (r instanceof RunnableScheduledFuture< ? >) {
						RunnableScheduledFuture< ? > future = (RunnableScheduledFuture< ? >) r;
						if ((future.getDelay(TimeUnit.NANOSECONDS) > 0L)
								&& queue.remove(future)) {
							future.run();
							scheduledExecutor.afterExecute(future, null);
						}
					}
				}
				scheduledExecutor.shutdown();
			}
			try {
				scheduledExecutor.awaitTermination(20, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			// Shutdown callback executor
			callbackExecutor.shutdown();
			try {
				callbackExecutor.awaitTermination(20, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		/**
		 * ScheduledThreadPoolExecutor for scheduled execution.
		 * 
		 * @ThreadSafe
		 */
		private static final class ScheduledExecutor
				extends ScheduledThreadPoolExecutor {
			ScheduledExecutor(int corePoolSize, ThreadFactory threadFactory) {
				super(corePoolSize, threadFactory);
			}

			/**
			 * Handle uncaught exceptions
			 */
			@Override
			protected void afterExecute(Runnable r, Throwable t) {
				super.afterExecute(r, t);
				if ((t == null) && (r instanceof Future< ? >)) {
					boolean interrupted = Thread.interrupted();
					try {
						((Future< ? >) r).get();
					} catch (CancellationException e) {
						// ignore
					} catch (InterruptedException e) {
						interrupted = true;
					} catch (ExecutionException e) {
						t = e.getCause();
					} finally {
						if (interrupted) { // restore interrupt status
							Thread.currentThread().interrupt();
						}
					}
				}
				if (t != null) {
					uncaughtException(t);
				}
			}
		}
	}

	/**
	 * Returns a new Promise that will be resolved with the result of the
	 * specified CompletionStage.
	 * <p>
	 * The returned Promise uses the callback executor and scheduled executor of
	 * this PromiseFactory object.
	 * <p>
	 * If the specified CompletionStage is completed normally, the returned
	 * Promise is resolved with the value of the specified CompletionStage. If
	 * the specified CompletionStage is completed exceptionally, the returned
	 * Promise is resolved with the exception of the specified CompletionStage.
	 * <p>
	 * After the returned Promise is resolved with the specified
	 * CompletionStage, all registered {@link Promise#onResolve(Runnable)
	 * callbacks} are called and any {@link Promise#then(Success, Failure)
	 * chained} Promises are resolved. This may occur asynchronously to this
	 * method.
	 * <p>
	 * Resolving the returned Promise <i>happens-before</i> any registered
	 * callback is called. That is, in a registered callback,
	 * {@link Promise#isDone()} must return {@code true} and
	 * {@link Promise#getValue()} and {@link Promise#getFailure()} must not
	 * block.
	 * 
	 * @param <T> The value type associated with the returned Promise.
	 * @param with A CompletionStage whose result will be used to resolve the
	 *            returned Promise. Must not be {@code null}.
	 * @return A new Promise that will be resolved with the result of the
	 *         specified CompletionStage.
	 * @since 1.2
	 */
	public <T> Promise<T> resolvedWith(CompletionStage< ? extends T> with) {
		DeferredPromiseImpl<T> chained = new DeferredPromiseImpl<>(this);
		with.whenComplete(chained::tryResolve);
		return chained.orDone();
	}

	/**
	 * Returns a new Promise that will be resolved with the specified Promise.
	 * <p>
	 * The returned Promise uses the callback executor and scheduled executor of
	 * this PromiseFactory object.
	 * <p>
	 * If the specified Promise is successfully resolved, the returned Promise
	 * is resolved with the value of the specified Promise. If the specified
	 * Promise is resolved with a failure, the returned Promise is resolved with
	 * the failure of the specified Promise.
	 * <p>
	 * After the returned Promise is resolved with the specified Promise, all
	 * registered {@link Promise#onResolve(Runnable) callbacks} are called and
	 * any {@link Promise#then(Success, Failure) chained} Promises are resolved.
	 * This may occur asynchronously to this method.
	 * <p>
	 * Resolving the returned Promise <i>happens-before</i> any registered
	 * callback is called. That is, in a registered callback,
	 * {@link Promise#isDone()} must return {@code true} and
	 * {@link Promise#getValue()} and {@link Promise#getFailure()} must not
	 * block.
	 * 
	 * @param <T> The value type associated with the returned Promise.
	 * @param with A Promise whose value or failure must be used to resolve the
	 *            returned Promise. Must not be {@code null}.
	 * @return A new Promise that is resolved with the specified Promise.
	 * @since 1.2
	 */
	public <T> Promise<T> resolvedWith(Promise< ? extends T> with) {
		DeferredPromiseImpl<T> chained = new DeferredPromiseImpl<>(this);
		with.onResolve(chained.new Chain(with));
		return chained.orDone();
	}

	/**
	 * Returns a {@code Collector} that accumulates the results of the input
	 * Promises into a new {@link #all(Collection)} Promise.
	 * 
	 * @param <T> The value type of the List value result of the collected
	 *            {@link #all(Collection)} Promise.
	 * @param <S> The value type of the input Promises.
	 * @return A {@code Collector} which accumulates the results of all the
	 *         input Promises into a new {@link #all(Collection)} Promise.
	 * @since 1.2
	 */
	public <T, S extends T> Collector<Promise<S>, ? ,Promise<List<T>>> toPromise() {
		return Collector.of(ArrayList<Promise<S>>::new, List::add,
				PromiseFactory::combiner, this::all);
	}

	private static <E, C extends Collection<E>> C combiner(C t, C u) {
		t.addAll(u);
		return t;
	}
}
