/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.wire.PutResultRequest;
import eu.arrowhead.workflowmanager.engine.WorkflowEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

/** workflowContext: ρ and ξ|dep(s) for the executor holding the step (the kit's session-context service). */
@RestController
@RequestMapping(CONTEXT_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class ContextAPI {

	@Autowired
	private WorkflowEngine engine;

	@Operation(summary = "Session parameters ρ (workflowContext.get-parameters)")
	@GetMapping(path = CONTEXT_PARAMETERS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody JsonNode parameters(final HttpServletRequest request, @PathVariable final String workItemId) {
		final String origin = HttpMethod.GET.name() + " " + CONTEXT_PATH + CONTEXT_PARAMETERS_PATH;
		return engine.parameters(HttpUtilities.acquireName(request, origin), HttpUtilities.isSysop(request, origin), workItemId);
	}

	@Operation(summary = "Stored results restricted to dep(s); 409 when one is missing (workflowContext.get-results)")
	@GetMapping(path = CONTEXT_RESULTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody Map<String, JsonNode> results(final HttpServletRequest request, @PathVariable final String workItemId) {
		final String origin = HttpMethod.GET.name() + " " + CONTEXT_PATH + CONTEXT_RESULTS_PATH;
		return engine.results(HttpUtilities.acquireName(request, origin), HttpUtilities.isSysop(request, origin), workItemId);
	}

	@Operation(summary = "Stores ξ(s) before completion is reported (workflowContext.put-result)")
	@PostMapping(path = CONTEXT_PUT_RESULT_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> putResult(final HttpServletRequest request, @RequestBody final PutResultRequest dto) {
		final String origin = HttpMethod.POST.name() + " " + CONTEXT_PATH + CONTEXT_PUT_RESULT_PATH;
		engine.putResult(HttpUtilities.acquireName(request, origin), HttpUtilities.isSysop(request, origin), dto);
		return ResponseEntity.ok().build();
	}
}
