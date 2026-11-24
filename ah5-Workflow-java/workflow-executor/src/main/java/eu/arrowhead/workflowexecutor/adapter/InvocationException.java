/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.adapter;

/** consume_i(π, d) = fail. {@code transient} invocation problems are reported as ERROR, others as FATAL_ERROR. */
public class InvocationException extends Exception {

	private static final long serialVersionUID = 1L;

	private final boolean transientFailure;

	public InvocationException(final String message, final boolean transientFailure, final Throwable cause) {
		super(message, cause);
		this.transientFailure = transientFailure;
	}

	public boolean isTransient() {
		return transientFailure;
	}
}
