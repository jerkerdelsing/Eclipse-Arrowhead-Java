/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.wire.ClaimRequest;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflow.wire.WorkItemReport;
import eu.arrowhead.workflowmanager.engine.WorkflowEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

/** workflowWorkItem: claim (pull mode) and report (all modes; the holder only). */
@RestController
@RequestMapping(WORK_ITEM_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class WorkItemAPI {

	@Autowired
	private WorkflowEngine engine;

	@Operation(summary = "Claims the first open work item the requester can run; 204 when none (workflowWorkItem.claim)")
	@PostMapping(path = WORK_ITEM_CLAIM_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<WorkItem> claim(final HttpServletRequest request, @RequestBody(required = false) final ClaimRequest dto) {
		final String origin = HttpMethod.POST.name() + " " + WORK_ITEM_PATH + WORK_ITEM_CLAIM_PATH;
		return engine.claim(HttpUtilities.acquireName(request, origin), dto).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@Operation(summary = "Reports BOUND, BIND_FAILED, SUCCESS, ERROR, FATAL_ERROR or ABORTED for a held work item (workflowWorkItem.report)")
	@PostMapping(path = WORK_ITEM_REPORT_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> report(final HttpServletRequest request, @RequestBody final WorkItemReport dto) {
		final String origin = HttpMethod.POST.name() + " " + WORK_ITEM_PATH + WORK_ITEM_REPORT_PATH;
		engine.report(HttpUtilities.acquireName(request, origin), HttpUtilities.isSysop(request, origin), dto);
		return ResponseEntity.ok().build();
	}
}
