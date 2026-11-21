/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.session;

/** A rule was requested whose premise does not hold in the current session state. */
public class RuleViolationException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	public RuleViolationException(final String message) {
		super(message);
	}
}
