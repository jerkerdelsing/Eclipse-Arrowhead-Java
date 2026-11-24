/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.validation;

import java.util.List;

import eu.arrowhead.workflow.model.RecipeGraph;

/** Validator plug-in. Built-in checks are static; catalogue and authorisation checks are added by the WorkflowManager. */
@FunctionalInterface
public interface RecipeCheck {

	void check(RecipeGraph graph, List<Finding> findings);
}
