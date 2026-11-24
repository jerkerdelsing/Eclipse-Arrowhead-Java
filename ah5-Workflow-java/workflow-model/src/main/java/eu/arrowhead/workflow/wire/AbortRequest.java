/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

/** workflowExecution.abort payload. */
public record AbortRequest(String workItemId, long sessionId, String reason) {
}
