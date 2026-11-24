/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflowmanager.api.dto.SessionListResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.SessionResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.StartSessionRequestDTO;
import eu.arrowhead.workflowmanager.api.dto.TraceResponseDTO;
import eu.arrowhead.workflowmanager.engine.WorkflowEngine;
import eu.arrowhead.workflowmanager.jpa.entity.SessionRecord;
import eu.arrowhead.workflowmanager.jpa.repository.SessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

/** workflowInstance. */
@RestController
@RequestMapping(INSTANCE_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class InstanceAPI {

	@Autowired
	private WorkflowEngine engine;

	@Autowired
	private SessionRepository sessions;

	@Operation(summary = "Starts a session of a recipe, or of the recipe a product provides (workflowInstance.start)")
	@PostMapping(path = INSTANCE_START_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public @ResponseBody SessionResponseDTO start(final HttpServletRequest request, @RequestBody final StartSessionRequestDTO dto) {
		final String origin = HttpMethod.POST.name() + " " + INSTANCE_PATH + INSTANCE_START_PATH;
		final SessionRecord s = engine.start(HttpUtilities.acquireName(request, origin), dto);
		return DTOConverter.session(s, engine.state(s));
	}

	@Operation(summary = "Aborts a session; owner or sysop only (workflowInstance.abort, extension x-abort)")
	@DeleteMapping(path = INSTANCE_ABORT_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody SessionResponseDTO abort(final HttpServletRequest request, @PathVariable final long id, @RequestParam(required = false) final String reason) {
		final String origin = HttpMethod.DELETE.name() + " " + INSTANCE_PATH + INSTANCE_ABORT_PATH;
		final SessionRecord s = engine.abort(id, HttpUtilities.acquireName(request, origin), HttpUtilities.isSysop(request, origin), reason);
		return DTOConverter.session(s, engine.state(s));
	}

	@Operation(summary = "Lists sessions, optionally by status (workflowInstance.query)")
	@GetMapping(path = INSTANCE_QUERY_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody SessionListResponseDTO query(@RequestParam(required = false) final String status) {
		final var records = status == null ? sessions.findAllByOrderByIdDesc() : sessions.findAllByStatus(status);
		final var entries = records.stream().map(s -> DTOConverter.session(s, engine.state(s))).toList();
		return new SessionListResponseDTO(entries, entries.size());
	}

	@Operation(summary = "Returns the markings and status of a session (workflowInstance.get-status)")
	@GetMapping(path = INSTANCE_ITEM_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody SessionResponseDTO get(@PathVariable final long id) {
		final SessionRecord s = engine.get(id);
		return DTOConverter.session(s, engine.state(s));
	}

	@Operation(summary = "Returns the run of a session with ξ, β, ε (workflowInstance.get-trace)")
	@GetMapping(path = INSTANCE_TRACE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody TraceResponseDTO trace(@PathVariable final long id) {
		final SessionRecord s = engine.get(id);
		return DTOConverter.trace(s, engine.state(s));
	}
}
