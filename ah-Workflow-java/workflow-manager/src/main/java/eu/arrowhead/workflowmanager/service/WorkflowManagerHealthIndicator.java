/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import org.springframework.stereotype.Component;

import eu.arrowhead.common.sysmonitor.SysMonitorHealth;
import eu.arrowhead.common.sysmonitor.SysMonitorHealthIndicator;
import eu.arrowhead.workflowmanager.jpa.repository.SessionRepository;

/** sysMonitor echo: degraded while the database cannot be reached. */
@Component
public class WorkflowManagerHealthIndicator implements SysMonitorHealthIndicator {

	private final SessionRepository sessions;

	public WorkflowManagerHealthIndicator(final SessionRepository sessions) {
		this.sessions = sessions;
	}

	@Override
	public SysMonitorHealth health() {
		try {
			return SysMonitorHealth.alive("running sessions: " + sessions.countByStatus("running"));
		} catch (final RuntimeException ex) {
			return SysMonitorHealth.degraded("database unavailable");
		}
	}
}
