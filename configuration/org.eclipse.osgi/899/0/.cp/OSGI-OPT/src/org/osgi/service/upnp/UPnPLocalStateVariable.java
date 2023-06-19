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
/**
 * To keep the current values getting from subscribed UPnPDevices. 
 * 
 * The actual values of the UPnPStateVaraible are passed as Java object type. 
 * 
 * @since 1.1
 **/

package org.osgi.service.upnp;

/**
 * A local UPnP state variable which allows the value of the state variable to
 * be queried.
 * 
 * @since 1.1
 * 
 * @author $Id: d491334387bfdc913f196eb77a22ddc181bb81a8 $
 */
public interface UPnPLocalStateVariable extends UPnPStateVariable {
	/**
	 * This method will keep the current values of UPnPStateVariables of a
	 * UPnPDevice whenever UPnPStateVariable's value is changed , this method
	 * must be called.
	 * 
	 * @return {@code Object} current value of UPnPStateVariable. If the current
	 *         value is initialized with the default value defined UPnP service
	 *         description.
	 * @throws IllegalStateException If the UPnP state variable has been
	 *             removed.
	 */
	public Object getCurrentValue();
}
