/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.graphdbmanager;

import eu.arrowhead.common.Defaults;

public final class GraphDBManagerDefaults extends Defaults {

	public static final String SERVICE_AUTHORIZATION_ENABLED_DEFAULT = "false";
	public static final String HISTORIAN_DIRECTORY_DEFAULT = "./data/historian";

	private GraphDBManagerDefaults() {
		throw new UnsupportedOperationException();
	}
}
