/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import eu.arrowhead.workflow.wire.ClaimRequest;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;
import eu.arrowhead.workflowexecutor.WorkflowExecutorSystemInfo;

/** Pull mode (blackboard, Derhamy Paper H): claim open work items this executor can run. */
@Component
public class PullLoop {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private ManagerClient manager;

	@Autowired
	private StepRunner runner;

	@Autowired
	private WorkflowExecutorSystemInfo sysInfo;

	@Value(WorkflowExecutorConstants.$THREADS_WD)
	private int threads;

	@Scheduled(fixedDelayString = WorkflowExecutorConstants.$POLL_MS_WD, initialDelay = 3000)
	public void poll() {
		if (!"pull".equalsIgnoreCase(sysInfo.mode())) {
			return;
		}
		try {
			while (runner.activeCount() < threads) {
				final WorkItem item = manager.claim(new ClaimRequest(sysInfo.capabilities(), sysInfo.services(), sysInfo.workstation()));
				if (item == null) {
					return;
				}
				logger.info("Claimed {} of session {}", item.step(), item.sessionId());
				runner.submit(item);
			}
		} catch (final RuntimeException ex) {
			logger.debug("Claim failed: {}", ex.getMessage());
		}
	}
}
