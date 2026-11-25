/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.common.sysmonitor;

import java.util.List;
import java.util.Set;

// Optional beans: a system contributes its own observation types to its sysMonitor service, next to echo, power and status.
// The types are offered in the service registration, so that SysMonitor subscriptions can select them.
public interface SysMonitorSource {

	// type names: letters, digits, '-' and '_' (they become a segment of the routing key)
	Set<String> observationTypes();

	// the current state, published in every cycle
	default List<SysMonitorReading> state() {
		return List.of();
	}

	// events that are not published yet, oldest first; they are offered again until published() confirms them
	default List<SysMonitorReading> events() {
		return List.of();
	}

	default void published(final SysMonitorReading event) {
	}
}
