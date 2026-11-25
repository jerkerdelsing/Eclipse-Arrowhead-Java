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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// One value of a system's own observation type. The id stays the same when the publication is repeated, so that a
// consumer recognises the repetition (SysMonitor stores an observationId once).
public record SysMonitorReading(String id, String observationType, Instant time, Map<String, Object> value, String unit, boolean good) {

	public static SysMonitorReading of(final String observationType, final Map<String, Object> value, final String unit, final boolean good) {
		return new SysMonitorReading(UUID.randomUUID().toString(), observationType, Instant.now(), value, unit, good);
	}
}
