/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;


public record WorkItemResponseDTO(String workItemId, long sessionId, String action, String step, String executor, int attempt, String state, String message,
		String createdAt, String updatedAt) {
}
