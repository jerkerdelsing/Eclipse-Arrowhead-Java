/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import eu.arrowhead.workflow.wire.AbortRequest;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflowmanager.service.ExecutorDirectory.ExecutorEndpoint;

/** Outbound workflowExecution calls (push mode). */
public interface ExecutorGateway {

	void start(ExecutorEndpoint executor, WorkItem item);

	void abort(ExecutorEndpoint executor, AbortRequest request);
}
