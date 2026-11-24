/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponseDTO(
		long id,
		String recipe,
		String version,
		String subject,
		String owner,
		String status,
		String dispatch,
		String failure,
		List<String> extensions,
		Map<String, String> stepMarking,
		Map<String, String> actionMarking,
		long modelTransitions,
		int transitionBound,
		String product,
		String createdAt,
		String updatedAt) {
}
