/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.workflow.model.Recipe;

/**
 * One step attempt handed to a WorkflowExecutor: workflowExecution.start payload (push) and workflowWorkItem.claim
 * response (pull). Carries ρ and ξ|dep(s) so the executor can compose the input κ_s without further calls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkItem(
		String workItemId,
		long sessionId,
		String recipe,
		String action,
		String step,
		int attempt,
		Recipe.ServiceRequirement service,
		Recipe.ExecutorRequirement executor,
		Map<String, Object> params,
		JsonNode input,
		Recipe.Policy policy,
		Double leaseDurationSeconds,
		JsonNode parameters,
		Map<String, JsonNode> dependencyResults) {
}
