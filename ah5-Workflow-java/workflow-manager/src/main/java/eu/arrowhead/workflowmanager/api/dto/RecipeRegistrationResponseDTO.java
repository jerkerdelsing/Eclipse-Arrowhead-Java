/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import eu.arrowhead.workflow.model.validation.ValidationReport;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecipeRegistrationResponseDTO(RecipeResponseDTO recipe, ValidationReport validation, boolean created) {
}
