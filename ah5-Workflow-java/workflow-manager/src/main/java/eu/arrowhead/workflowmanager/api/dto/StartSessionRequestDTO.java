/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** workflowInstance.start: a registered recipe (latest active version when version is empty), or a product to retrieve the recipe from. */
public record StartSessionRequestDTO(String recipe, String version, String subject, JsonNode parameters, String dispatch, String product) {
}
