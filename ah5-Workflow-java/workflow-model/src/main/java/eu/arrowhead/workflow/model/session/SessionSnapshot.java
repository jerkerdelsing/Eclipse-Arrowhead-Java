/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.session;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.workflow.model.session.Markings.ActionMarking;
import eu.arrowhead.workflow.model.session.Markings.SessionStatus;
import eu.arrowhead.workflow.model.session.Markings.StepMarking;

/**
 * Serialisable form of a session: z = (M, M_A, β, ε, ξ, status), the actual runner of each step (the executor that
 * stands for the choreographer when η(s) is undefined), the trace and the extensions used.
 */
public record SessionSnapshot(
		Map<String, StepMarking> stepMarking,
		Map<String, ActionMarking> actionMarking,
		Map<String, JsonNode> binding,
		Map<String, String> executorAssignment,
		Map<String, String> runner,
		Map<String, JsonNode> results,
		SessionStatus status,
		String failure,
		Set<String> extensions,
		List<Transition> trace) {
}
