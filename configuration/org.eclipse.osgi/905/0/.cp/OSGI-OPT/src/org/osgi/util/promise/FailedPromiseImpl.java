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

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.osgi.util.function.Consumer;
import org.osgi.util.function.Function;
import org.osgi.util.function.Predicate;

/**
 * Failed Promise implementation.
 * <p>
 * This class is not used directly by clients. Clients should use
 * {@link PromiseFactory#failed(Throwable)} to create a failed {@link Promise}.
 * 
 * @param <T> The result type associated with the Promise.
 * @since 1.1
 * @ThreadSafe
 * @author $Id: faaa1e9812944bb34475ddaff5eacdc145256612 $
 */
final class FailedPromiseImpl<T> extends PromiseImpl<T> {
	/**
	 * The failure of this failed Promise.
	 */
	private final Throwable	fail;

	/**
	 * Initialize this failed Promise.
	 * 
	 * @param fail The failure of this failed Promise. Must not be {@code null}.
	 * @param factory The factory to use for callbacks and scheduled operations.
	 */
	FailedPromiseImpl(Throwable fail, PromiseFactory factory) {
		super(factory);
		this.fail = requireNonNull(fail);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isDone() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getValue() throws InvocationTargetException {
		throw new InvocationTargetException(fail);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Throwable getFailure() {
		return fail;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void result(Result< ? super T> consumer) {
		consumer.accept(null, fail);
	}

	@Override
	public String toString() {
		return super.toString() + "[failed: " + fail + "]";
	}

	/**
	 * Coerce the value type of this FailedPromiseImpl.
	 * 
	 * @return This FailedPromiseImpl.
	 */
	@SuppressWarnings("unchecked")
	private <V> FailedPromiseImpl<V> coerce() {
		return (FailedPromiseImpl<V>) this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Promise<T> onSuccess(Consumer< ? super T> success) {
		requireNonNull(success);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <R> Promise<R> then(Success< ? super T, ? extends R> success,
			Failure failure) {
		if (failure == null) {
			return coerce();
		}
		return super.then(success, failure);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Promise<T> thenAccept(Consumer< ? super T> consumer) {
		requireNonNull(consumer);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Promise<T> filter(Predicate< ? super T> predicate) {
		requireNonNull(predicate);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <R> Promise<R> map(Function< ? super T, ? extends R> mapper) {
		requireNonNull(mapper);
		return coerce();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <R> Promise<R> flatMap(
			Function< ? super T,Promise< ? extends R>> mapper) {
		requireNonNull(mapper);
		return coerce();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Promise<T> timeout(long millis) {
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CompletionStage<T> toCompletionStage() {
		CompletableFuture<T> completableFuture = new CompletableFuture<>();
		completableFuture.completeExceptionally(fail);
		return completableFuture;
	}
}
