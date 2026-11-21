/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** workflowWorkItem.report payload; only the executor holding the work item may report. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkItemReport(String workItemId, ReportStatus status, JsonNode binding, JsonNode result, String message) {
}
