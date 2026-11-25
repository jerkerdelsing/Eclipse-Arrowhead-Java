/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.runtime;

import org.springframework.stereotype.Component;

import eu.arrowhead.common.sysmonitor.SysMonitorHealth;
import eu.arrowhead.common.sysmonitor.SysMonitorHealthIndicator;

@Component
public class WorkflowExecutorHealthIndicator implements SysMonitorHealthIndicator {

	private final StepRunner runner;

	public WorkflowExecutorHealthIndicator(final StepRunner runner) {
		this.runner = runner;
	}

	@Override
	public SysMonitorHealth health() {
		return SysMonitorHealth.alive("active work items: " + runner.activeCount());
	}
}
