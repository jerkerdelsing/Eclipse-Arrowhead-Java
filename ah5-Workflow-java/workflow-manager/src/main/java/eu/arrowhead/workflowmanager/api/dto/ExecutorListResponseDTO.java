/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;

import java.util.List;

import eu.arrowhead.workflowmanager.service.ExecutorDirectory.ExecutorEndpoint;

public record ExecutorListResponseDTO(List<ExecutorEndpoint> entries, long count) {
}
