/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

import com.fasterxml.jackson.databind.JsonNode;

/** workflowContext.put-result payload: the executor stores ξ(s) before it reports completion. */
public record PutResultRequest(String workItemId, JsonNode result) {
}
