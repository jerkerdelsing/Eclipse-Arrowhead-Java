/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

/** The request conflicts with the session or recipe state (HTTP 409). */
public class WorkflowConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public WorkflowConflictException(final String message) {
		super(message);
	}
}
