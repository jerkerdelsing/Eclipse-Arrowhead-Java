/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;


public record WorklogEntryDTO(long id, String createdAt, Long sessionId, String recipe, String step, String message, String detail) {
}
