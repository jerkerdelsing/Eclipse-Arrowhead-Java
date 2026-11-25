/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.session;

import com.fasterxml.jackson.annotation.JsonInclude;

import eu.arrowhead.workflow.model.session.Markings.Rule;

/** One labelled transition z →ℓ z' (§5.10); {@code target} is the step or action name, null for close. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Transition(int seq, Rule rule, String target, String detail, String at) {

	public String label() {
		return target == null ? rule.label() : rule.label() + "(" + target + ")";
	}
}
