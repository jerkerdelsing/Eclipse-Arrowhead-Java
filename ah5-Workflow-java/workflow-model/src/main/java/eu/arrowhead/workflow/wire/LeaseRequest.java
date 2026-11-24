/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

import com.fasterxml.jackson.annotation.JsonInclude;

/** workflowLease payload: reserve uses resource, sessionId and durationSeconds; poll uses ticket; renew and release use token. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaseRequest(String resource, Long sessionId, Double durationSeconds, String ticket, String token) {
}
