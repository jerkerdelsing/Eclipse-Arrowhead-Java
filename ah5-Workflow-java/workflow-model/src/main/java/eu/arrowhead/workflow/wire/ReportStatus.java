/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

/**
 * Executor report codes. BOUND realises S-Bind, SUCCESS S-Done, BIND_FAILED S-FailBind (after all candidates),
 * FATAL_ERROR S-FailInvoke. ERROR is S-FailInvoke unless the step has a retry policy (extension). ABORTED acknowledges an abort; on an active work item (the executor stopped on its own) it fails the session.
 */
public enum ReportStatus {
	BOUND, BIND_FAILED, SUCCESS, ERROR, FATAL_ERROR, ABORTED
}
