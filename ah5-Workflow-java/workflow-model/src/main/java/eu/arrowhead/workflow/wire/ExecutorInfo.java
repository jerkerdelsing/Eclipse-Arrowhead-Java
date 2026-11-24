/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

import java.util.List;

/** workflowExecution.service-info response: what an executor declares (cap(w) and the services it can run). */
public record ExecutorInfo(String name, List<String> capabilities, List<String> services, String workstation, String mode) {
}
