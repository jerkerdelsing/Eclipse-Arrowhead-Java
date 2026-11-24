/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RecipeResponseDTO(long id, String name, String version, String status, String registeredBy, JsonNode recipe, String createdAt) {
}
