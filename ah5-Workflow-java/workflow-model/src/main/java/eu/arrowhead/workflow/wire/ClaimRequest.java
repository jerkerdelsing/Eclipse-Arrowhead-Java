/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

import java.util.List;

/** workflowWorkItem.claim payload (pull mode): what the requesting executor can do. */
public record ClaimRequest(List<String> capabilities, List<String> services, String workstation) {

	public ClaimRequest {
		capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
		services = services == null ? List.of() : List.copyOf(services);
	}
}
