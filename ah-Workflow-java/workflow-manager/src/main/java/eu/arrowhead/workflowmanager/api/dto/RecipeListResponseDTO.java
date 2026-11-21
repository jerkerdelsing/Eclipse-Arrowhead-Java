/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;

import java.util.List;

public record RecipeListResponseDTO(List<RecipeResponseDTO> entries, long count) {
}
