/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager;

import eu.arrowhead.common.Defaults;

public final class WorkflowManagerDefaults extends Defaults {

	public static final String SERVER_PORT_DEFAULT = "8482";
	public static final String DISCOVERY_CACHE_SECONDS_DEFAULT = "10";
	public static final String DEFAULT_DISPATCH_DEFAULT = "push";
	public static final String CLAIM_TIMEOUT_DEFAULT = "300";
	public static final String WORKER_THREADS_DEFAULT = "8";
	public static final String LEASE_DEFAULT_DURATION_DEFAULT = "120";

	private WorkflowManagerDefaults() {
		throw new UnsupportedOperationException();
	}
}
