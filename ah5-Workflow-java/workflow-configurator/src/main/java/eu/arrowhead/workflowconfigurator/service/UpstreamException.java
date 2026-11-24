/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.service;

/** A provider the configurator consumes refused or failed; carries the HTTP status to pass on to the editor. */
public class UpstreamException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final int status;
	private final String provider;

	public UpstreamException(final int status, final String provider, final String message) {
		super(message);
		this.status = status;
		this.provider = provider;
	}

	public int status() {
		return status;
	}

	public String provider() {
		return provider;
	}
}
