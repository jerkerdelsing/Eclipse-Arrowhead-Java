/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.api.compat;

import static eu.arrowhead.workflow.wire.WorkflowServices.CHOREOGRAPHER_COMPAT_PATH;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.common.exception.ForbiddenException;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.wire.WorkflowServices;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;
import eu.arrowhead.workflowexecutor.WorkflowExecutorSystemInfo;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.AbortStepRequest;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.ExecuteStepRequest;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.ExecutorServiceInfoRequest;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.ExecutorServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

/** Choreographer executor endpoints (service-info, start, abort); callable by the Choreographer (or a sysop) only. */
@RestController
@RequestMapping(CHOREOGRAPHER_COMPAT_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class ChoreographerCompatAPI {

	@Autowired
	private ChoreographerCompatRunner runner;

	@Autowired
	private WorkflowExecutorSystemInfo sysInfo;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_SYSTEM_WD)
	private String choreographerSystem;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_MIN_VERSION_WD)
	private int minVersion;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_MAX_VERSION_WD)
	private int maxVersion;

	@Operation(summary = "Choreographer executor service-info: this executor has no dependencies")
	@PostMapping(path = "/service-info", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ExecutorServiceInfoResponse serviceInfo(final HttpServletRequest request, @RequestBody final ExecutorServiceInfoRequest dto) {
		check(request, "/service-info");
		// the Choreographer sends the definition in camelCase; the executor may list it in kebab-case as recipes do
		if (!WorkflowServices.covers(sysInfo.services(), dto.serviceDefinition())) {
			throw new DataNotFoundException("This executor does not run " + dto.serviceDefinition());
		}
		return new ExecutorServiceInfoResponse(dto.serviceDefinition(), minVersion, maxVersion, List.of());
	}

	@Operation(summary = "Choreographer executor start")
	@PostMapping(path = "/start", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> start(final HttpServletRequest request, @RequestBody final ExecuteStepRequest dto) {
		check(request, "/start");
		runner.start(dto);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Choreographer executor abort")
	@PostMapping(path = "/abort", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> abort(final HttpServletRequest request, @RequestBody final AbortStepRequest dto) {
		check(request, "/abort");
		runner.abort(dto);
		return ResponseEntity.ok().build();
	}

	private void check(final HttpServletRequest request, final String path) {
		final String origin = HttpMethod.POST.name() + " " + CHOREOGRAPHER_COMPAT_PATH + path;
		if (!choreographerSystem.equals(HttpUtilities.acquireName(request, origin)) && !HttpUtilities.isSysop(request, origin)) {
			throw new ForbiddenException("Only " + choreographerSystem + " may call the Choreographer executor interface", origin);
		}
	}
}
