/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

/** workflowExecution.get-status response. */
public record ExecutionState(String workItemId, String state, String message) {
}
