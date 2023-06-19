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

package org.osgi.service.wireadmin;

/**
 * {@code BasicEnvelope} is an implementation of the {@link Envelope} interface
 * 
 * @Immutable
 * @author $Id: b9b13e2e6a7da87146d73fcd4dc3b4ec5f5cc4a9 $
 */
public class BasicEnvelope implements Envelope {
	private final Object	value;
	private final Object	identification;
	private final String	scope;

	/**
	 * Constructor.
	 * 
	 * @param value Content of this envelope, may be {@code null}.
	 * @param identification Identifying object for this {@code Envelope}
	 *        object, must not be {@code null}
	 * @param scope Scope name for this object, must not be {@code null}
	 * @see Envelope
	 */
	public BasicEnvelope(Object value, Object identification, String scope) {
		this.value = value;
		this.identification = identification;
		this.scope = scope;
	}

	/**
	 * @see org.osgi.service.wireadmin.Envelope#getValue()
	 */
	@Override
	public Object getValue() {
		return value;
	}

	/**
	 * @see org.osgi.service.wireadmin.Envelope#getIdentification()
	 */
	@Override
	public Object getIdentification() {
		return identification;
	}

	/**
	 * @see org.osgi.service.wireadmin.Envelope#getScope()
	 */
	@Override
	public String getScope() {
		return scope;
	}
}
