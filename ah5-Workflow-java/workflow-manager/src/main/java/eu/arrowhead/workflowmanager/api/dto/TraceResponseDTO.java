/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.workflow.model.session.Transition;

/** The run z0 →ℓ1 z1 → … with ξ, β, ε and the runner per step. withinModel is false when an extension (§6.6) was used. */
public record TraceResponseDTO(
		long sessionId,
		String status,
		List<Transition> trace,
		Map<String, JsonNode> results,
		Map<String, JsonNode> bindings,
		Map<String, String> executorAssignment,
		Map<String, String> runner,
		long modelTransitions,
		int transitionBound,
		boolean withinModel) {
}
