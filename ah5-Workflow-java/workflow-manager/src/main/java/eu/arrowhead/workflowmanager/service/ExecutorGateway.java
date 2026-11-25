/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import eu.arrowhead.workflow.wire.AbortRequest;
import eu.arrowhead.workflow.wire.ExecutionState;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflowmanager.service.ExecutorDirectory.ExecutorEndpoint;

/** Outbound workflowExecution calls. */
public interface ExecutorGateway {

	void start(ExecutorEndpoint executor, WorkItem item);

	void abort(ExecutorEndpoint executor, AbortRequest request);

	/** workflowExecution.get-status: what the executor knows about a work item. */
	ExecutionState status(ExecutorEndpoint executor, String workItemId);
}
